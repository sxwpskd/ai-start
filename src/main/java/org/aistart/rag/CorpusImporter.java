package org.aistart.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RAG 语料导入器（启动时执行，决策记录第 34 条）
 * 流程：遍历 rag-corpus 四类目录（documents/references/rules/skills）→ UTF-8 读取
 * → 切片（携带 source/category 元数据）→ 分批向量化 → 写入内存向量库
 * 导入失败不阻断启动：检索返回空结果，链路行为与未装 RAG 一致
 */
@Slf4j
@Component
public class CorpusImporter implements ApplicationRunner {

    /**
     * 语料根目录（硬编码，用户拍板不加配置项；相对工作目录）
     */
    private static final String CORPUS_ROOT = "rag-corpus";

    /**
     * 向量化批大小——DashScope 文本向量单次调用行数上限为 20（text-embedding-v3/v4 为 10），保守取 10
     */
    private static final int EMBED_BATCH_SIZE = 10;

    /**
     * 切片参数（按字符数切，适配中文语料）
     */
    private static final int MAX_SEGMENT_SIZE = 1000;
    private static final int OVERLAP_SIZE = 150;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    public CorpusImporter(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            importCorpus();
        } catch (Exception e) {
            log.error("RAG 语料导入失败（不阻断启动，检索将返回空结果）", e);
        }
    }

    private void importCorpus() throws IOException {
        Path root = Path.of(CORPUS_ROOT);
        if (!Files.isDirectory(root)) {
            log.error("RAG 语料目录不存在: {}（检索将返回空结果）", root.toAbsolutePath());
            return;
        }
        // 1. 收集全部 md 文件（含嵌套目录）
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(path -> path.toString().endsWith(".md")).sorted().toList();
        }
        // 2. 切片：每个片段继承 source（相对路径）与 category（顶层目录名）元数据
        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_SEGMENT_SIZE, OVERLAP_SIZE);
        List<TextSegment> allSegments = new ArrayList<>();
        for (Path file : files) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String source = root.relativize(file).toString().replace('\\', '/');
                String category = source.contains("/") ? source.substring(0, source.indexOf('/')) : "";
                Metadata metadata = Metadata.from(Map.of("source", source, "category", category));
                allSegments.addAll(splitter.split(Document.from(text, metadata)));
            } catch (Exception e) {
                log.warn("RAG 语料文件读取失败（跳过该文件）: {}", file, e);
            }
        }
        log.info("RAG 语料切片完成：{} 个文件，{} 个片段，开始向量化", files.size(), allSegments.size());
        // 3. 分批向量化入库（embedAll 一次性发送全部片段，会超出单次调用行数上限，必须分批）
        long start = System.currentTimeMillis();
        int failedBatches = 0;
        for (int i = 0; i < allSegments.size(); i += EMBED_BATCH_SIZE) {
            List<TextSegment> batch = allSegments.subList(i, Math.min(i + EMBED_BATCH_SIZE, allSegments.size()));
            try {
                List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
                embeddingStore.addAll(embeddings, batch);
            } catch (Exception e) {
                failedBatches++;
                log.warn("RAG 语料批次向量化失败（跳过该批）: offset={}", i, e);
            }
        }
        log.info("RAG 语料导入完成：片段总数 {}，失败批次 {}，耗时 {} ms",
                allSegments.size(), failedBatches, System.currentTimeMillis() - start);
    }
}
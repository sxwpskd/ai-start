package org.aistart.rag;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * RAG 检索服务（工具与工作流节点共用的底层，决策记录第 4/11/34 条）
 * query → 向量化 → 内存库 topK 检索；检索异常统一降级为空结果，不阻断调用方链路
 * 类目过滤（2026-09-20 用户拍板）：构思图=业务场景向、生成图=编码规范向、直连工具=全库
 */
@Slf4j
@Service
public class RagService {

    /**
     * 默认返回片段数（硬编码，暂不设相似度阈值，联调后再调，决策记录第 34 条）
     */
    public static final int DEFAULT_TOP_K = 5;

    /**
     * 类目元数据键（CorpusImporter 切片时写入）
     */
    private static final String CATEGORY_METADATA_KEY = "category";

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    public RagService(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 检索与 query 最相关的语料片段（全库，不过滤类目——直连工具场景）
     *
     * @param query 检索输入（用户需求原文或增强提示词）
     * @param topK  返回片段数上限
     * @return 相关片段列表；无命中/检索失败时返回空列表
     */
    public List<RagFragment> search(String query, int topK) {
        return search(query, topK, null);
    }

    /**
     * 检索与 query 最相关的语料片段（可按类目过滤）
     *
     * @param query      检索输入（用户需求原文或增强提示词）
     * @param topK       返回片段数上限
     * @param categories 类目过滤（references/rules/skills/documents）；null 或空 = 全库检索
     * @return 相关片段列表；无命中/检索失败时返回空列表
     */
    public List<RagFragment> search(String query, int topK, Collection<String> categories) {
        if (StrUtil.isBlank(query) || topK <= 0) {
            return List.of();
        }
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK);
            if (categories != null && !categories.isEmpty()) {
                requestBuilder.filter(MetadataFilterBuilder.metadataKey(CATEGORY_METADATA_KEY).isIn(categories));
            }
            return embeddingStore.search(requestBuilder.build()).matches().stream()
                    .map(match -> new RagFragment(
                            match.embedded().metadata().getString("source"),
                            match.embedded().metadata().getString(CATEGORY_METADATA_KEY),
                            match.embedded().text(),
                            match.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为空结果（不阻断链路）: query={}", query, e);
            return List.of();
        }
    }

    /**
     * 统一格式化检索片段（工具与节点共用，避免两处各写一套）
     *
     * @return 带来源标注的片段文本；空列表返回空串
     */
    public String formatFragments(List<RagFragment> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fragments.size(); i++) {
            RagFragment fragment = fragments.get(i);
            sb.append("【片段 ").append(i + 1).append("｜来源：").append(fragment.source()).append("】\n")
                    .append(fragment.text()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 检索片段（来源/分类/正文/相似度）
     */
    public record RagFragment(String source, String category, String text, Double score) {
    }
}
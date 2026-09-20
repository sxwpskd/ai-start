package org.aistart.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量库配置（内存实现）
 * 重启即清空，由 CorpusImporter 在启动时重建——不做持久化（决策记录第 34 条）
 * 语料导入与检索查询共用本 Bean
 */
@Configuration
public class EmbeddingStoreConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
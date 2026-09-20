package org.aistart.config;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量化模型配置（DashScope text-embedding）
 * 语料导入与检索查询共用本 Bean（同一模型保证向量空间一致）
 * 配置项：langchain4j.community.dashscope.api-key + embedding-model.model-name
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.community.dashscope")
@Data
public class EmbeddingModelConfig {

    private String apiKey;

    private EmbeddingModelProperties embeddingModel = new EmbeddingModelProperties();

    @Data
    public static class EmbeddingModelProperties {
        private String modelName;
    }

    /**
     * 创建 DashScope 向量化模型（单例：导入与查询共用）
     */
    @Bean
    public EmbeddingModel dashscopeEmbeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModel.getModelName())
                .build();
    }
}
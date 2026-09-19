package org.aistart.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 构思期（工作流通道）专用同步模型配置
 * 与全局 chat-model 隔离的原因：全局 chat-model 配置了 response-format=json_object
 * 与 strict-json-schema（供质检、图片收集等 JSON 服务使用），会强制模型只能输出 JSON，
 * 导致构思文档的 <!--THINK_START--> / <!--THINK_END--> 标记无法原样输出，ThinkNode 截取不到文档；
 * 本模型不设置任何输出格式约束（普通文本输出）
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.think-chat-model")
@Data
public class ThinkChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * 创建用于构思文档生成的ChatModel（无输出格式约束）
     */
    @Bean
    @Scope("prototype")
    public ChatModel thinkChatModelPrototype() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}

package org.aistart.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* ai服务创建工厂
* 该工厂类用于创建AI代码生成服务，负责管理ChatModel实例的注入
* */
@Configuration
public class AiCodeGeneratorServiceFactory {
    // 使用@Resource注解注入ChatModel实例
    // ChatModel是一个用于处理AI对话的模型接口
    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;
    // 创建Ai代码生成器服务
    @Bean
    public AiCodeGeneratorService createAiCodeGeneratorService() {
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}

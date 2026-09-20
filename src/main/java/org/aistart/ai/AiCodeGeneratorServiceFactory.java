package org.aistart.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aistart.ai.tools.*;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.aistart.service.ChatHistoryService;
import org.aistart.utils.SpringContextUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/*
* ai服务创建工厂
* 该工厂类用于创建AI代码生成服务，负责管理ChatModel实例的注入
* */
@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {
    // 使用@Resource注解注入ChatModel实例
    // ChatModel是一个用于处理AI对话的模型接口
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService>
            serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)//保存1000条
            .expireAfterWrite(Duration.ofMinutes(30))//写入后30分钟过期
            .expireAfterAccess(Duration.ofMinutes(5))//访问后5分钟过期
            .removalListener((key, value, cause) -> {//移除监听器，并记录移除服务原因
                log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();


    @Bean
    public AiCodeGeneratorService createAiCodeGeneratorService() {
        return getAiCodeGeneratorService(1);
    }

    /**
     * 根据 appId 获取服务（兼容老服务）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }
    /**
     * 根据 appId 和代码生成类型获取服务（带缓存）
     */
     public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key ->
                createAiCodeGeneratorService(appId, codeGenType));
    }
    /**
     * 根据appId获取服务
     * 包括创建Ai代码生成器服务以及记忆
     * */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId,
                                                                CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话记录到内存记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory,20);

        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            case VUE_PROJECT -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil
                        .getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        // 生成期不注册构思写入工具 writeThink——AI 在生成/改码阶段不得改写构思文档；
                        // readThink 保留（生成期可回读构思现状，无文档时工具自身返回不可用提示）
                        .tools(toolManager.getAllToolsExcept("writeThink"))
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
                        .build();
            }
            case HTML, MULTI_FILE -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil
                        .getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        .build();
            }
            case BASE -> {
                // 构思期：流式模型（构思对话无需推理模型）+ 显式注册三枚工具
                // 注意：不可照抄 VUE_PROJECT 的 getAllTools()，否则文件类工具会被注册，
                // AI 在构思期即可编写代码，破坏"只构思"边界（开发文档决策记录第 15 条）
                // ragSearch：构思期可由 AI 自主检索语料（B1/B2 恢复后追加注册，决策记录第 34/35 条）
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil
                        .getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(toolManager.getTool("writeThink"), toolManager.getTool("readThink"),
                                toolManager.getTool("ragSearch"))
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
                        .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };

    }




    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }






}


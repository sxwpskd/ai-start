package org.aistart.ai;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aistart.service.ChatHistoryService;
import org.aistart.utils.SpringContextUtil;
import org.springframework.context.annotation.Configuration;

/**
 * 构思期（BASE）工作流通道 AI 服务工厂
 * 与 AiCodeGeneratorServiceFactory 相互独立：本服务不注册任何工具，配置不同，
 * 若共用实例缓存会相互覆盖，故各自构建
 */
@Slf4j
@Configuration
public class BaseThinkWorkflowServiceFactory {

    /**
     * 对话记忆窗口大小（与直连通道保持一致）
     */
    private static final int MEMORY_MAX_MESSAGES = 20;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 获取构思工作流 AI 服务
     * 每次调用都新建记忆实例并以 DB 历史回灌：DB 是对话历史的唯一真相，
     * 直连通道写入的历史由此被工作流通道看到，反之亦然（切换开关不清空会话）；
     * 不做实例缓存——服务与记忆一一绑定，缓存会使记忆窗口与 DB 脱节
     *
     * @param appId 应用 ID（同时作为对话记忆 ID）
     * @return 无工具的构思 AI 服务
     */
    public BaseThinkWorkflowService getService(long appId) {
        log.info("为 appId: {} 构建构思工作流 AI 服务（无工具）", appId);
        // 动态获取多例的构思专用模型（照 AiCodeGenTypeRoutingServiceFactory 先例，支持并发）
        // 不能用全局 openAiChatModel：后者配置了 response-format=json_object，
        // 会强制模型只输出 JSON，导致构思文档的 THINK 标记无法原样输出（详见 ThinkChatModelConfig）
        ChatModel chatModel = SpringContextUtil.getBean("thinkChatModelPrototype", ChatModel.class);
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(MEMORY_MAX_MESSAGES)
                .build();
        // 从数据库回灌历史对话（内部会先清空记忆，防止重复加载）
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, MEMORY_MAX_MESSAGES);
        return AiServices.builder(BaseThinkWorkflowService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> chatMemory)
                .build();
    }
}

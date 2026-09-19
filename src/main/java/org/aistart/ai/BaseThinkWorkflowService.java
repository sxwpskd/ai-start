package org.aistart.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 构思期（BASE）工作流通道 AI 服务
 * 与直连通道 AiCodeGeneratorService.generateBaseThinkStream 的区别：
 * 1. 不注册任何工具——构思文档由 AI 直接在回复中输出，节点按标记截取后落盘（不经工具类）
 * 2. 同步单值返回——工作流通道为同步阻塞执行，不做流式
 */
public interface BaseThinkWorkflowService {

    /**
     * 构思对话（同步）
     *
     * @param appId       应用 ID（同时作为对话记忆 ID）
     * @param userMessage 用户消息（含注入的当前构思文档全文）
     * @return AI 回复全文（说明文字 + 构思文档区块）
     */
    @SystemMessage(fromResource = "prompt/base-workflow-prompt.txt")
    String generateThink(@MemoryId long appId, @UserMessage String userMessage);
}

package org.aistart.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aistart.ai.BaseThinkWorkflowService;
import org.aistart.ai.BaseThinkWorkflowServiceFactory;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.utils.SpringContextUtil;
import org.aistart.utils.ThinkFileUtils;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 构思节点（工作流通道）
 * 职责：读取当前构思文档拼增强提示词 → 调无工具构思 AI 服务 → 按标记截取构思文档并落盘渲染 → 回填上下文
 * 直接调用 ThinkFileUtils，不经工具类，避免节点反向依赖 ai/tools（开发文档决策记录第 11 条）
 */
@Slf4j
public class ThinkNode {

    /**
     * 构思文档区块起始标记（与 prompt/base-workflow-prompt.txt 保持一致）
     */
    private static final String THINK_START_TAG = "<!--THINK_START-->";

    /**
     * 构思文档区块结束标记（与 prompt/base-workflow-prompt.txt 保持一致）
     */
    private static final String THINK_END_TAG = "<!--THINK_END-->";

    /**
     * 当前构思文档注入前缀（与 B5 生成触发的拼接方式保持一致）
     */
    private static final String THINK_CONTEXT_PREFIX = "\n\n以下是当前的应用构思文档：\n";

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 构思生成");
            Long appId = context.getAppId();
            // 1. 读取构思文档现状并拼增强提示词
            // 记忆窗口仅 20 条，长对话会淘汰最初含全文的消息，且直连通道写入的内容也需被看到，故每轮主动回读
            String thinkContent = ThinkFileUtils.readThink(appId);
            String userMessage = context.getOriginalPrompt() + THINK_CONTEXT_PREFIX + thinkContent;
            // 2. 调用无工具构思 AI 服务（同步阻塞返回）
            BaseThinkWorkflowService thinkService = SpringContextUtil
                    .getBean(BaseThinkWorkflowServiceFactory.class)
                    .getService(appId);
            String aiReply = thinkService.generateThink(appId, userMessage);
            // 3. 截取构思文档区块：命中则全量覆盖保存并同步重渲染；未命中视为本轮构思无实质更新，不写文件
            String thinkDoc = extractThinkDoc(aiReply);
            if (StrUtil.isNotBlank(thinkDoc)) {
                String renderPath = ThinkFileUtils.saveThink(appId, thinkDoc);
                log.info("构思文档已更新并重新渲染: {}", renderPath);
            } else {
                log.info("本轮回复未包含构思文档区块，视为构思无更新，跳过保存");
            }
            // 4. 回填上下文（thinkReply 供接口层落库与返回）
            context.setCurrentStep("构思生成");
            context.setThinkReply(aiReply);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 从 AI 回复中截取构思文档区块内容
     *
     * @param aiReply AI 回复全文
     * @return 构思文档内容；未包含完整区块时返回 null
     */
    private static String extractThinkDoc(String aiReply) {
        if (StrUtil.isBlank(aiReply)) {
            return null;
        }
        int startIndex = aiReply.indexOf(THINK_START_TAG);
        int endIndex = aiReply.indexOf(THINK_END_TAG);
        if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) {
            return null;
        }
        String thinkDoc = aiReply.substring(startIndex + THINK_START_TAG.length(), endIndex).trim();
        return StrUtil.isBlank(thinkDoc) ? null : thinkDoc;
    }
}

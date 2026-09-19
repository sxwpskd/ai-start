package org.aistart.ai.tools;

import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.aistart.utils.ThinkFileUtils;
import org.springframework.stereotype.Component;

/**
 * 构思读取工具
 * 供 BASE（构思期）对话使用：AI 需回看当前构思文档全文时调用
 */
@Slf4j
@Component
public class ReadThinkTool extends BaseTool {

    /**
     * 构思文档不存在时的返回文案：视为工具不可用，避免 AI 反复调用
     */
    private static final String TOOL_UNAVAILABLE_TIP = "本工具不存在（当前应用暂无构思文档）";

    @Tool("读取当前项目构思文档的完整内容")
    public String readThink(
            @ToolMemoryId Long appId
    ) {
        try {
            // 无构思文档时直接返回不可用提示，不暴露"尚未创建"的文档语义
            if (!ThinkFileUtils.existsThink(appId)) {
                log.info("构思文档不存在，readThink 不可用, appId: {}", appId);
                return TOOL_UNAVAILABLE_TIP;
            }
            String markdown = ThinkFileUtils.readThink(appId);
            log.info("构思读取成功, appId: {}", appId);
            return markdown;
        } catch (Exception e) {
            String errorMessage = "构思读取失败: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "readThink";
    }

    @Override
    public String getDisplayName() {
        return "构思读取工具";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return String.format("""
                [工具调用] %s
                已读取当前构思文档
                """, getDisplayName());
    }
}

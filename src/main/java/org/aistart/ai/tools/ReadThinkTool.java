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

    @Tool("读取当前项目构思文档的完整内容")
    public String readThink(
            @ToolMemoryId Long appId
    ) {
        try {
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

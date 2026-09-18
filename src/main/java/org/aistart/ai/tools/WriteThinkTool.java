package org.aistart.ai.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.aistart.utils.ThinkFileUtils;
import org.springframework.stereotype.Component;

/**
 * 构思写入工具
 * 供 BASE（构思期）对话使用：AI 将完整的构思文档交给该工具落盘并渲染预览页
 */
@Slf4j
@Component
public class WriteThinkTool extends BaseTool {

    @Tool("保存或更新项目构思文档（覆盖式保存，每次必须传入完整的构思全文）")
    public String writeThink(
            @P("完整的构思文档内容（Markdown 全文）")
            String markdown,
            @ToolMemoryId Long appId
    ) {
        try {
            String previewPath = ThinkFileUtils.saveThink(appId, markdown);
            log.info("构思保存成功, appId: {}, 预览路径: {}", appId, previewPath);
            return "构思保存成功: " + previewPath;
        } catch (Exception e) {
            String errorMessage = "构思保存失败: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeThink";
    }

    @Override
    public String getDisplayName() {
        return "构思写入工具";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String markdown = arguments.getStr("markdown");
        String sizeDesc = StrUtil.isEmpty(markdown) ? "" : String.format("（约 %d 字）", markdown.length());
        return String.format("""
                [工具调用] %s
                构思文档已更新%s
                """, getDisplayName(), sizeDesc);
    }
}

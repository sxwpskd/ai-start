package org.aistart.core.parser;

import org.aistart.ai.model.BaseCodeResult;
import org.aistart.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseCodeParser implements CodeParser<BaseCodeResult>{
    private static final Pattern role = Pattern.compile("\"ai角色\":\"([^\"]*)\"");
    private static final Pattern workflowPattern = Pattern.compile("\"工作流\":\"([^\"]*)\"");
    private static final Pattern contentPattern = Pattern.compile("\"具体内容\":\"([^\"]*)\"");

/**
 * 解析代码内容，提取角色、工作流和内容信息
 * @param codeContent 需要解析的代码内容字符串
 * @return BaseCodeResult 包含解析结果的封装对象
 */
    public  BaseCodeResult parseCode(String codeContent) {
    // 创建结果对象
        BaseCodeResult result = new BaseCodeResult();
        //解析出
        String role = extractCodeByPattern(codeContent, BaseCodeParser.role);
        String workflow = extractCodeByPattern(codeContent, BaseCodeParser.workflowPattern);
        String content = extractCodeByPattern(codeContent, BaseCodeParser.contentPattern);

        if (role != null && !role.trim().isEmpty()) {
            result.setRole(role.trim());
        }

        if (workflow != null && !workflow.trim().isEmpty()) {
            result.setWorkflow(workflow.trim());
        }
        if (content != null && !content.trim().isEmpty()) {
            result.setResult(content.trim());
        }
        return result;
    }

    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

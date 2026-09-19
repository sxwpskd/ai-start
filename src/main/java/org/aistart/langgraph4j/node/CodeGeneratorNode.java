package org.aistart.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aistart.constant.AppConstant;
import org.aistart.core.AIfacade.AiCodeGeneratorFacade;
import org.aistart.core.handler.JsonMessageStreamHandler;
import org.aistart.langgraph4j.model.QualityResult;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.aistart.utils.SpringContextUtil;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");

            CodeGenTypeEnum generationType = context.getGenerationType();
            // 应用 id：工作流通道的业务入参，同时作为对话记忆 id
            Long appId = context.getAppId();
            // 构造用户消息（包含原始提示词、构思文档与可能的错误修复信息）
            String userMessage = buildUserMessage(context);

            // 获取 AI 代码生成外观服务
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})", generationType.getValue(), generationType.getText());
            // 调用流式代码生成（appId 打通后工厂按应用维度创建带对话记忆的 AiService）
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);
            // 聚合本轮流内容为落库文本（全图成功后由服务层统一落库，决策记录第 21 条）
            StringBuilder genReplyBuilder = new StringBuilder();
            Set<String> seenToolIds = new HashSet<>();
            JsonMessageStreamHandler jsonMessageStreamHandler = SpringContextUtil.getBean(JsonMessageStreamHandler.class);
            codeStream.doOnNext(chunk -> genReplyBuilder.append(
                            toHistoryText(chunk, generationType, jsonMessageStreamHandler, seenToolIds)))
                    // 同步等待流式输出完成，最多等待 10 分钟
                    .blockLast(Duration.ofMinutes(10));
            // 根据类型设置生成目录
            String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            // 更新状态
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            context.setGenReply(genReplyBuilder.toString());
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 将流中的单个块转换为落库文本
     * VUE_PROJECT 的流是 JSON 消息块（ai_response / tool_request / tool_executed），
     * 复用直连通道的解析规则转文本；HTML / MULTI_FILE 本身就是 AI 纯文本，直接透传
     * （两条通道的落库内容因此保持一致）
     */
    private static String toHistoryText(String chunk, CodeGenTypeEnum generationType,
                                        JsonMessageStreamHandler jsonMessageStreamHandler, Set<String> seenToolIds) {
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            return jsonMessageStreamHandler.collectHistoryText(chunk, seenToolIds);
        }
        return chunk;
    }

    /**
     * 构造用户消息，如果存在质检失败结果则添加错误修复信息
     */
    private static String buildUserMessage(WorkflowContext context) {
        // 检查是否存在质检失败结果
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            // 质检失败重试：直接将错误修复信息作为新的提示词（起到了修改的作用）
            return buildErrorFixPrompt(qualityResult);
        }
        String userMessage = context.getEnhancedPrompt();
        // 注入构思文档全文（独立拼接、不回写 enhancedPrompt；think 文件不存在时为空不拼）
        String thinkContext = context.getThinkContext();
        if (StrUtil.isNotBlank(thinkContext)) {
            userMessage = userMessage + "\n\n以下是当前的应用构思文档：\n" + thinkContext;
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null &&
                !qualityResult.getIsValid() &&
                qualityResult.getErrors() != null &&
                !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        // 添加错误列表
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        // 添加修复建议（如果有）
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }

}


package org.aistart.langgraph4j.node;

import lombok.extern.slf4j.Slf4j;
import org.aistart.ai.AiCodeGenTypeRoutingService;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.aistart.utils.SpringContextUtil;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType = resolveGenerationType(context);

            // 更新状态
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 确定本次的代码生成类型
     * 应用已处于生成期（codeGenType 非空且非 BASE）时，直接使用已锁定类型、不做 AI 路由——
     * 否则可能路由出与锁定值不同的类型，破坏"生成后单向锁定"规则（决策记录第 25 条）
     */
    private static CodeGenTypeEnum resolveGenerationType(WorkflowContext context) {
        CodeGenTypeEnum lockedType = context.getCodeGenType();
        if (lockedType != null && lockedType != CodeGenTypeEnum.BASE) {
            log.info("应用已处于生成期，直接使用已锁定类型: {} ({})", lockedType.getValue(), lockedType.getText());
            return lockedType;
        }
        try {
            // 获取AI路由服务
            AiCodeGenTypeRoutingService routingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
            // 根据原始提示词进行智能路由
            CodeGenTypeEnum routedType = routingService.routeCodeGenType(context.getOriginalPrompt());
            // 路由结果可能为空或 BASE（构思期不是生成目标），均兜底为 HTML
            if (routedType == null || routedType == CodeGenTypeEnum.BASE) {
                log.warn("AI智能路由未返回有效生成类型（{}），使用默认HTML类型", routedType);
                return CodeGenTypeEnum.HTML;
            }
            log.info("AI智能路由完成，选择类型: {} ({})", routedType.getValue(), routedType.getText());
            return routedType;
        } catch (Exception e) {
            log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
            return CodeGenTypeEnum.HTML;
        }
    }
}


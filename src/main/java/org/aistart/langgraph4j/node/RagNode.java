package org.aistart.langgraph4j.node;

import lombok.extern.slf4j.Slf4j;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * RAG 检索节点（占位）
 * RAG 整体搁置中（开发文档决策记录第 9 条），本节点当前只作为图上的接入插槽存在：
 * 零逻辑透传上下文，待 B1（RagService）/ B2 恢复后在此补检索逻辑并注入增强提示词
 */
@Slf4j
public class RagNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: RAG 检索（占位，检索逻辑未接入）");
            context.setCurrentStep("RAG检索");
            return WorkflowContext.saveContext(context);
        });
    }
}

package org.aistart.langgraph4j.workflow;

import lombok.extern.slf4j.Slf4j;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.langgraph4j.node.RagNode;
import org.aistart.langgraph4j.node.ThinkNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 构思期（BASE）工作流图
 * 执行链路：START → RAG 检索（占位）→ 构思生成 → END
 * 与生成图（CodeGenConcurrentWorkflow）相互独立：两条路径无交汇点，
 * 且触发时机、执行耗时、返回体均不同（开发文档 B6/B7）
 */
@Slf4j
public class BaseWorkflow {

    /**
     * 创建构思工作流图
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    .addNode("rag", RagNode.create())
                    .addNode("think", ThinkNode.create())
                    .addEdge(START, "rag")
                    .addEdge("rag", "think")
                    .addEdge("think", END)
                    .compile();
        } catch (GraphStateException e) {
            log.error("构思工作流创建失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "构思工作流创建失败");
        }
    }
}

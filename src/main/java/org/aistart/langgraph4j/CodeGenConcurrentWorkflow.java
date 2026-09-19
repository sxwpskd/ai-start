package org.aistart.langgraph4j;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.langgraph4j.model.QualityResult;
import org.aistart.langgraph4j.node.*;
import org.aistart.langgraph4j.node.concurrent.*;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
* 通过langgraph实现并发
* */
@Slf4j
public class CodeGenConcurrentWorkflow {

    /**
     * 创建并发工作流
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    // 添加节点
                    .addNode("image_plan", ImagePlanNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    // RAG 检索占位节点（RAG 搁置中，B1/B2 恢复后在此补检索并并入 enhancedPrompt）
                    .addNode("rag", RagNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    // 质检节点暂时停用（2026-09-19：耗时高——全目录代码非流式送 AI，且 VUE 在
                    // code_generator 内已真实构建兜底）；恢复时取消注释并还原下方条件边即可
                    // .addNode("code_quality_check", CodeQualityCheckNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    // 添加并发图片收集节点
                    .addNode("content_image_collector", ContentImageCollectorNode.create())
                    .addNode("illustration_collector", IllustrationCollectorNode.create())
                    .addNode("diagram_collector", DiagramCollectorNode.create())
                    .addNode("logo_collector", LogoCollectorNode.create())
                    .addNode("image_aggregator", ImageAggregatorNode.create())

                    // 添加边
                    .addEdge(START, "image_plan")

                    // 并发分支：从计划节点分发到各个收集节点
                    .addEdge("image_plan", "content_image_collector")
                    .addEdge("image_plan", "illustration_collector")
                    .addEdge("image_plan", "diagram_collector")
                    .addEdge("image_plan", "logo_collector")

                    // 汇聚：所有收集节点都汇聚到聚合器
                    .addEdge("content_image_collector", "image_aggregator")
                    .addEdge("illustration_collector", "image_aggregator")
                    .addEdge("diagram_collector", "image_aggregator")
                    .addEdge("logo_collector", "image_aggregator")

                    // 继续串行流程
                    .addEdge("image_aggregator", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    // 路由确定生成类型后再检索，便于按类型检索对应语料规范（流程.txt 3.4）
                    .addEdge("router", "rag")
                    .addEdge("rag", "code_generator")
                    // 质检停用后：生成完直接按类型决定构建或结束（重试环随之消失）
                    .addConditionalEdges("code_generator",
                            edge_async(this::routeAfterCodeGen),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END
                            ))
                    // 旧链路（质检在环上，含 fail 回 code_generator 的重试）：
                    // .addEdge("code_generator", "code_quality_check")
                    //
                    // // 质检条件边
                    // .addConditionalEdges("code_quality_check",
                    //         edge_async(this::routeAfterQualityCheck),
                    //         Map.of(
                    //                 "build", "project_builder",
                    //                 "skip_build", END,
                    //                 "fail", "code_generator"
                    //         ))
                    .addEdge("project_builder", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "并发工作流创建失败");
        }
    }

    /**
     * 执行并发工作流（演示/测试入口：仅传提示词）
     * 业务侧请用 executeWorkflow(WorkflowContext, Consumer)，需传入应用维度的完整上下文
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();
        return executeWorkflow(initialContext, null);
    }

    /**
     * 执行并发工作流（业务入口：完整初始上下文 + 步骤回调）
     *
     * @param initialContext 初始上下文（工作流通道需带 appId / userId / codeGenType / thinkContext）
     * @param stepCallback   每完成一个节点时的回调（可为 null），供工作流通道推送进度
     * @return 终态上下文
     */
    public WorkflowContext executeWorkflow(WorkflowContext initialContext, Consumer<WorkflowContext> stepCallback) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("并发工作流图:\n{}", graph.content());
        log.info("开始执行并发代码生成工作流，appId: {}", initialContext.getAppId());
        WorkflowContext finalContext = null;
        int stepCounter = 1;
        // 配置并发执行
        ExecutorService pool = ExecutorBuilder.create()
                .setCorePoolSize(10)
                .setMaxPoolSize(20)
                .setWorkQueue(new LinkedBlockingQueue<>(100))
                .setThreadFactory(ThreadFactoryBuilder.create().setNamePrefix("Parallel-Image-Collect").build())
                .build();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor("image_plan", pool)
                .build();
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext),
                runnableConfig)) {
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("--- 第 {} 步完成，当前步骤: {} ---", stepCounter, currentContext.getCurrentStep());
                // 节点完成后回调（进度推送），回调异常不影响工作流执行
                if (stepCallback != null) {
                    try {
                        stepCallback.accept(currentContext);
                    } catch (Exception e) {
                        log.error("工作流步骤回调执行失败: {}", e.getMessage(), e);
                    }
                }
            }
            stepCounter++;
        }
        log.info("并发代码生成工作流执行完成！");
        return finalContext;
    }

    /**
     * 路由函数：不经过质检节点时，按生成类型决定下一步
     * VUE_PROJECT 需要 npm 构建产物 → project_builder；其余类型无项目可构建 → 直接 END
     */
    private String routeAfterCodeGen(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            log.info("Vue 项目，进入构建节点");
            return "build";
        }
        log.info("非 Vue 项目，无需构建，流程结束");
        return "skip_build";
    }

    /**
     * 路由函数：根据质检结果决定下一步
     * 当前质检节点已停用（见 createWorkflow 注释），本方法暂不被引用，保留以便随时恢复
     */
    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();

        if (qualityResult == null || !qualityResult.getIsValid()) {
            log.error("代码质检失败，需要重新生成代码");
            return "fail";
        }
        log.info("代码质检通过，继续后续流程");
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            return "build";
        } else {
            return "skip_build";
        }
    }
}

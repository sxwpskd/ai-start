package org.aistart.langgraph4j.workflow;

import lombok.extern.slf4j.Slf4j;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 构思工作流执行器
 * 虚拟线程 + 同步阻塞：提交整图执行后同步等待终态上下文，工作流通道不做流式
 * （开发文档决策记录第 4 条），B7 的 GenWorkflowExecutor 沿用本骨架
 */
@Slf4j
@Component
public class BaseWorkflowExecutor {

    /**
     * 执行超时时间（分钟）
     */
    private static final long TIMEOUT_MINUTES = 10;

    /**
     * 执行线程池：虚拟线程按任务创建
     * 类级共享且不主动关闭——ExecutorService#close 会等待任务结束，用 try-with-resources
     * 会在超时后反而被阻塞；虚拟线程开销极低，超时后中断并任其自然收敛即可
     */
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 执行构思工作流（同步阻塞）
     *
     * @param appId         应用 ID（同时作为对话记忆 ID）
     * @param userId        用户 ID
     * @param codeGenType   应用当前的代码生成类型
     * @param originalPrompt 用户原始提示词
     * @return 执行完成后的终态上下文（thinkReply 为本轮 AI 回复全文）
     */
    public WorkflowContext execute(Long appId, Long userId, CodeGenTypeEnum codeGenType, String originalPrompt) {
        CompiledGraph<MessagesState<String>> workflow = new BaseWorkflow().createWorkflow();
        WorkflowContext initialContext = WorkflowContext.builder()
                .appId(appId)
                .userId(userId)
                .codeGenType(codeGenType)
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();
        Future<WorkflowContext> future = VIRTUAL_THREAD_EXECUTOR
                .submit(() -> runWorkflow(workflow, initialContext));
        try {
            return future.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            // 中断执行线程，避免超时后任务继续占用资源
            future.cancel(true);
            log.error("构思工作流执行超时（{} 分钟），appId: {}", TIMEOUT_MINUTES, appId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "构思工作流执行超时，请重试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("构思工作流执行失败，appId: {}, 原因: {}", appId, cause == null ? e.getMessage() : cause.getMessage(), e);
            // 节点内的业务异常原样上抛（错误码与文案由抛出方决定）
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "构思工作流执行失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("构思工作流执行被中断，appId: {}", appId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "构思工作流执行被中断");
        }
    }

    /**
     * 迭代执行图并取回终态上下文
     */
    private WorkflowContext runWorkflow(CompiledGraph<MessagesState<String>> workflow,
                                        WorkflowContext initialContext) {
        log.info("开始执行构思工作流，appId: {}", initialContext.getAppId());
        WorkflowContext finalContext = null;
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("--- 第 {} 步完成，当前步骤: {} ---", stepCounter, currentContext.getCurrentStep());
            }
            stepCounter++;
        }
        log.info("构思工作流执行完成，appId: {}", initialContext.getAppId());
        return finalContext;
    }
}

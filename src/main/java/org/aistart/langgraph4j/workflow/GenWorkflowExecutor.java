package org.aistart.langgraph4j.workflow;

import lombok.extern.slf4j.Slf4j;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 生成工作流执行器（工作流通道 · 生成期）
 * 生成图耗时数分钟（含 AI 生成、质检重试与 npm 构建），以虚拟线程执行 + 超时控制
 * <p>
 * 线程模型：Redisson RLock 按 threadId 记录持有者，加锁与解锁必须在同一线程，
 * 故本执行器提交的是"整段业务逻辑"（加锁 → 落库 → 跑图 → 落库 → 解锁），而非只有图本身；
 * SSE 端点因此可以立即返回 emitter，由本执行器在虚拟线程中推进，调用方通过终态回调收尾
 * <p>
 * 超时：定时任务对执行线程 cancel(true)（中断），中断从流式阻塞处抛出后由业务逻辑的
 * try/finally 释放分布式锁；超时以业务异常形式回传调用方
 */
@Slf4j
@Component
public class GenWorkflowExecutor {

    /**
     * 执行超时时间（分钟）
     */
    private static final long TIMEOUT_MINUTES = 10;

    /**
     * 执行线程池：虚拟线程按任务创建
     * 类级共享且不主动关闭——ExecutorService#close 会等待任务结束，用 try-with-resources
     * 会在超时后反而被阻塞；虚拟线程开销极低（同 BaseWorkflowExecutor）
     */
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 超时守门调度器：仅在到达超时点时中断执行线程
     * 守护线程且不主动关闭，避免阻塞 JVM 退出
     */
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform().daemon().name("gen-workflow-timeout").unstarted(runnable));

    /**
     * 提交生成工作流的整段业务逻辑到虚拟线程执行
     * 正常结束返回终态上下文；超时中断后以 {@link BusinessException} 结束
     * （加锁/落库/解锁均由传入的业务逻辑自行完成，以保证与执行线程同线程）
     *
     * @param work 业务逻辑（返回值应为终态上下文）
     * @return 终态上下文的 CompletableFuture（成功/失败经 whenComplete 之类的回调处理）
     */
    public CompletableFuture<WorkflowContext> submit(Supplier<WorkflowContext> work) {
        CompletableFuture<WorkflowContext> result = new CompletableFuture<>();
        // 执行线程与解锁线程必须一致（Redisson RLock 按 threadId 记录持有者）
        Future<?> task = VIRTUAL_THREAD_EXECUTOR.submit(() -> {
            try {
                result.complete(work.get());
            } catch (Throwable t) {
                // 业务异常原样传递，由调用方按类型处理
                result.completeExceptionally(t);
            }
        });
        // 超时守门：到点后中断执行线程（业务逻辑的 finally 会释放锁）
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (task.cancel(true)) {
                log.error("生成工作流执行超时（{} 分钟），已中断执行线程", TIMEOUT_MINUTES);
                result.completeExceptionally(new BusinessException(ErrorCode.OPERATION_ERROR, "生成超时，请重试"));
            }
        }, TIMEOUT_MINUTES, TimeUnit.MINUTES);
        // 任务先行结束（成功或失败）则撤销守门；已完成的结果不会被超时覆盖
        result.whenComplete((context, throwable) -> timeoutTask.cancel(false));
        return result;
    }
}

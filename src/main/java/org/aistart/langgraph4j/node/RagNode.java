package org.aistart.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aistart.langgraph4j.state.WorkflowContext;
import org.aistart.rag.RagService;
import org.aistart.utils.SpringContextUtil;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * RAG 检索节点（构思图 / 生成图共用；2026-09-20 接入 RagService，B1/B2 恢复）
 * 分流判据：context.generationType 是否为 null——构思图的 rag 在 think 之前
 * （无 router，该字段恒为 null）；生成图的 rag 在 router 之后（必已赋值）。
 * 不能用 codeGenType 判：构思期应用触发生成图时 codeGenType 仍为 BASE（生成成功后
 * 才落库，决策记录第 16/21 条），会误判。
 * 两条分支的输入 / 去向 / 类目（类目倾向为用户拍板）：
 * - 构思图：检索 originalPrompt（业务场景向类目）→ 写 ragContext，由 ThinkNode 拼接
 * - 生成图：检索 enhancedPrompt（编码规范向类目）→ 并回 enhancedPrompt 自身
 *   （prompt_enhancer 已跑完，节点自行合并；不并入需改动 code_generator，决策第 21/26 条）
 * 检索异常 / 无命中：RagService 内部已降级为空结果，本节点不写字段，链路行为与未装 RAG 一致
 */
@Slf4j
public class RagNode {

    /**
     * 构思图检索类目：业务场景向（页面参考 + 生成计划）
     */
    private static final List<String> THINK_CATEGORIES = List.of("references", "documents");

    /**
     * 生成图检索类目：编码规范向（组件/命名/API 规范 + 生成技能）
     */
    private static final List<String> GEN_CATEGORIES = List.of("rules", "skills");

    /**
     * 生成图检索结果注入 enhancedPrompt 的前缀
     */
    private static final String GEN_RAG_PREFIX = "\n\n以下是项目语料库中与本次生成相关的参考：\n";

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            context.setCurrentStep("RAG检索");
            RagService ragService = SpringContextUtil.getBean(RagService.class);
            if (context.getGenerationType() == null) {
                // 构思图分支：rag 在 think 之前，generationType 尚未产生
                List<RagService.RagFragment> fragments = ragService.search(
                        context.getOriginalPrompt(), RagService.DEFAULT_TOP_K, THINK_CATEGORIES);
                String ragText = ragService.formatFragments(fragments);
                if (StrUtil.isNotBlank(ragText)) {
                    context.setRagContext(ragText);
                }
                log.info("执行节点: RAG 检索（构思图），命中片段数: {}", fragments.size());
            } else {
                // 生成图分支：rag 在 router 之后，generationType 已确定
                List<RagService.RagFragment> fragments = ragService.search(
                        context.getEnhancedPrompt(), RagService.DEFAULT_TOP_K, GEN_CATEGORIES);
                String ragText = ragService.formatFragments(fragments);
                if (StrUtil.isNotBlank(ragText)) {
                    context.setEnhancedPrompt(context.getEnhancedPrompt() + GEN_RAG_PREFIX + ragText);
                }
                log.info("执行节点: RAG 检索（生成图），命中片段数: {}", fragments.size());
            }
            return WorkflowContext.saveContext(context);
        });
    }
}
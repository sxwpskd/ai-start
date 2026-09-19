package org.aistart.langgraph4j.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aistart.langgraph4j.model.ImageCollectionPlan;
import org.aistart.langgraph4j.model.ImageResource;
import org.aistart.langgraph4j.model.QualityResult;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工作流上下文 - 存储所有状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {//支持序列化

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /**
     * 应用 id（工作流通道的业务入参，同时作为对话记忆 id）
     */
    private Long appId;

    /**
     * 用户 id（工作流通道的业务入参）
     */
    private Long userId;

    /**
     * 应用当前的代码生成类型（BASE=构思期；工作流通道的业务入参）
     */
    private CodeGenTypeEnum codeGenType;

    /**
     * 当前执行步骤
     */
    private String currentStep;

    /**
     * 用户原始输入的提示词
     */
    private String originalPrompt;

    /**
     * 图片资源字符串
     */
    private String imageListStr;

    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;

    /**
     * 增强后的提示词
     */
    private String enhancedPrompt;

    /**
     * 代码生成类型
     */
    private CodeGenTypeEnum generationType;

    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;

    /**
     * 构建成功的目录
     */
    private String buildResultDir;

    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;

    /**
     * 图片收集计划
     */
    private ImageCollectionPlan imageCollectionPlan;


    /**
     * 并发图片收集的中间结果字段
     */
    private List<ImageResource> contentImages;
    private List<ImageResource> illustrations;
    private List<ImageResource> diagrams;
    private List<ImageResource> logos;

    /**
     * 构思期（BASE）工作流本轮产出的 AI 回复文本
     */
    private String thinkReply;

    /**
     * 错误信息
     */
    private String errorMessage;

    @Serial
    private static final long serialVersionUID = 1L;//序列化id

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}

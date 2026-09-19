package org.aistart.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 构思期（BASE）工作流对话请求
 */
@Data
public class AppThinkWorkflowRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 用户消息
     */
    private String message;

    private static final long serialVersionUID = 1L;
}

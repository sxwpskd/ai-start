package org.aistart.model.dto.app;

import lombok.Data;

import java.io.Serializable;
/*
*
*
* */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 应用初始生成类型（可选）：空/base = 构思期（默认）；html/multi_file/vue_project = 创建即锁定为生成期
     */
    private String codeGenType;

    private static final long serialVersionUID = 1L;
}

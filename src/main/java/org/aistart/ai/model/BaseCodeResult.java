package org.aistart.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("生成结果")
@Data
public class BaseCodeResult {
    @Description("ai角色")
    private String role;
    @Description("工作流")
    private String workflow;
    @Description("具体内容")
    private String result;
    @Description("输出token数")
    private Integer completionTokens;
    @Description("总token数")
    private Integer totalTokens;
    @Description("处理时间(毫秒)")
    private Long processingTime;
}

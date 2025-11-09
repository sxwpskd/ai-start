package org.aistart.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("生成多个代码文件的结果")
@Data
public class MultiFileCodeResult {

    @Description("HTML代码")
    private String htmlCode;

    @Description("CSS代码")
    private String cssCode;

    @Description("JS代码")
    private String jsCode;

    @Description("生成代码的描述")
    private String description;
    @Description("输出token数")
    private Integer completionTokens;
    @Description("总token数")
    private Integer totalTokens;
    @Description("处理时间(毫秒)")
    private Long processingTime;
}


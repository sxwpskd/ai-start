package org.aistart.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("生成 HTML 代码文件的结果")
@Data
public class HtmlCodeResult {

    @Description("HTML代码")
    private String htmlCode;

    @Description("生成代码的描述")
    private String description;
    @Description("输出token数")
    private Integer completionTokens;
    @Description("总token数")
    private Integer totalTokens;
    @Description("处理时间(毫秒)")
    private Long processingTime;
}


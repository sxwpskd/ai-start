package org.aistart.ai;

import dev.langchain4j.service.SystemMessage;
import org.aistart.model.enums.CodeGenTypeEnum;

/**
 * AI代码生成类型智能路由服务
 * 使用结构化输出直接返回枚举类型
 *
 *  
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    @SystemMessage(fromResource = "prompt/routing-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}

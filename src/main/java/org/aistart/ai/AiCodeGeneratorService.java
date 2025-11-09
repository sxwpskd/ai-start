package org.aistart.ai;

import dev.langchain4j.service.SystemMessage;
import org.aistart.ai.model.BaseCodeResult;
import org.aistart.ai.model.HtmlCodeResult;
import org.aistart.ai.model.MultiFileCodeResult;

public interface AiCodeGeneratorService {

    /*
    * 生成代码
    * */
    @SystemMessage(fromResource = "prompt/base-prompt.txt")
    BaseCodeResult generateBaseCode(String prompt);


        /**
         * 生成 HTML 代码
         *
         * @param userMessage 用户消息
         * @return 生成的代码结果
         */
        @SystemMessage(fromResource = "prompt/html-prompt.txt")
        HtmlCodeResult generateHtmlCode(String userMessage);

        /**
         * 生成多文件代码
         *
         * @param userMessage 用户消息
         * @return 生成的代码结果
         */
        @SystemMessage(fromResource = "prompt/multi-prompt.txt")
        MultiFileCodeResult generateMultiFileCode(String userMessage);
    }



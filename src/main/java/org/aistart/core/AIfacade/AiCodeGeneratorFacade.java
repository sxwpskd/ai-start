package org.aistart.core.AIfacade;

import dev.langchain4j.model.openai.internal.shared.Usage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aistart.ai.AiCodeGeneratorService;
import org.aistart.ai.model.BaseCodeResult;
import org.aistart.ai.model.HtmlCodeResult;
import org.aistart.ai.model.MultiFileCodeResult;
import org.aistart.core.CodeFileSaver;
import org.aistart.core.CodeParser;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> generateAndSaveHtmlCode(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCode(userMessage);
            case BASE -> generateAndSaveBaseCode(userMessage);
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        System.out.println("没有问题");
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> generateAndSaveHtmlCodeStream(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCodeStream(userMessage);
            case BASE -> generateAndSaveBaseCodeStream(userMessage);
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    /**
     * 生成 Base模式的代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
private File generateAndSaveBaseCode(String userMessage) {
        BaseCodeResult result = aiCodeGeneratorService.generateBaseCode(userMessage);
        return CodeFileSaver.saveBaseCodeResult(result);
}
    private Flux<String> generateAndSaveBaseCodeStream(String userMessage) {
        Flux<String> result = aiCodeGeneratorService.generateBaseCodeStream(userMessage);
        long startTime = System.currentTimeMillis();
        //定义字符串拼接器，用于当流式返回所有代码后保存
        StringBuilder codeBuilder = new StringBuilder();
        return result.doOnNext(chunk -> {
            //实时收集代码
            codeBuilder.append(chunk);

        }).doOnComplete(() -> {
            try {
                //保存代码
                String completeBase= codeBuilder.toString();
                BaseCodeResult baseCodeResult = CodeParser.parseBaseCode(completeBase);

                Usage usage = aiCodeGeneratorService.getLastUsage(userMessage);
                if (usage != null) {
                    baseCodeResult.setCompletionTokens(usage.completionTokens());
                    baseCodeResult.setTotalTokens(usage.totalTokens());
                }

                baseCodeResult.setProcessingTime(System.currentTimeMillis() - startTime);
                CodeFileSaver.saveBaseCodeResult(baseCodeResult);
                //解析代码

                File saveDir=CodeFileSaver.saveBaseCodeResult(baseCodeResult);
                log.info("base创建完成，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.info("base创建失败", e.getMessage());
            }
        });
    }
    /**
     * 生成 HTML 模式的代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateAndSaveHtmlCode(String userMessage) {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return CodeFileSaver.saveHtmlCodeResult(result);
    }
    private Flux<String> generateAndSaveHtmlCodeStream(String userMessage) {
        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
        long startTime = System.currentTimeMillis();
        //定义字符串拼接器，用于当流式返回所有代码后保存
        StringBuilder codeBuilder = new StringBuilder();
        return result.doOnNext(chunk -> {
            //实时收集代码
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            try {
                //保存代码
                String completeHtmlCode= codeBuilder.toString();
                //解析代码
                HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(completeHtmlCode);
                Usage usage = aiCodeGeneratorService.getLastUsage(userMessage);
                if (usage != null) {
                    htmlCodeResult.setCompletionTokens(usage.completionTokens());
                    htmlCodeResult.setTotalTokens(usage.totalTokens());
                }
                htmlCodeResult.setProcessingTime(System.currentTimeMillis() - startTime);
                File saveDir=CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
                log.info("html创建完成，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("html创建失败", e.getMessage());
            }
        });
    }
    /**
     * 生成多文件模式的代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateAndSaveMultiFileCode(String userMessage) {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return CodeFileSaver.saveMultiFileCodeResult(result);
    }
    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage) {
        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
        long startTime = System.currentTimeMillis();
        //定义字符串拼接器，用于当流式返回所有代码后保存
        StringBuilder codeBuilder = new StringBuilder();
        return result.doOnNext(chunk -> {
            //实时收集代码
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            //保存代码
            try {
                String completeMultFileCode= codeBuilder.toString();
                //解析代码
                MultiFileCodeResult multiFileCodeResult = CodeParser.parseMultiFileCode(completeMultFileCode);
                Usage usage = aiCodeGeneratorService.getLastUsage(userMessage);
                if (usage != null) {
                    multiFileCodeResult.setCompletionTokens(usage.completionTokens());
                    multiFileCodeResult.setTotalTokens(usage.totalTokens());
                }
                multiFileCodeResult.setProcessingTime(System.currentTimeMillis() - startTime);
                File saveDir=CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
                log.info("多文件创建完成，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("多文件创建失败", e.getMessage());
            }
        });
    }
}

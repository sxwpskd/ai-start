package org.aistart.core.AIfacade;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aistart.ai.AiCodeGeneratorService;
import org.aistart.ai.AiCodeGeneratorServiceFactory;
import org.aistart.ai.model.BaseCodeResult;
import org.aistart.ai.model.HtmlCodeResult;
import org.aistart.ai.model.MultiFileCodeResult;
import org.aistart.core.file_save.CodeFileSaverExecutor;
import org.aistart.core.parser.CodeParserExecutor;
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
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    //非流式，急需优化
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        //用工厂创造服务
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case BASE -> {BaseCodeResult result = aiCodeGeneratorService.generateBaseCode(userMessage);
                //yield  CodeFileSaver.saveBaseCodeResult(result);
                yield  CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum,appId);
            }
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield  CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum,appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield  CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    //流式
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        System.out.println("没有问题");
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        //用工厂创造服务
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case BASE -> {
                Flux<String> result = aiCodeGeneratorService.generateBaseCodeStream(userMessage);
                yield  processCoseStream(result, codeGenTypeEnum,appId);
            }
            case HTML -> {
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield  processCoseStream(result, codeGenTypeEnum,appId);
            }
            case MULTI_FILE -> {
                Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield  processCoseStream(result, codeGenTypeEnum,appId);
            }
            case VUE_PROJECT -> {
                Flux<String> result = aiCodeGeneratorService.generateVueProjectCodeStream(appId,userMessage);
                yield  processCoseStream(result, codeGenTypeEnum,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    private Flux<String> processCoseStream(Flux<String> resultStream,CodeGenTypeEnum codeGenTypeEnum,Long appId) {

        long startTime = System.currentTimeMillis();
        //定义字符串拼接器，用于当流式返回所有代码后保存
        StringBuilder codeBuilder = new StringBuilder();
        return resultStream.doOnNext(chunk -> {
            //实时收集代码
            codeBuilder.append(chunk);

        }).doOnComplete(() -> {
            try {
                //保存代码
                String completeBase= codeBuilder.toString();
                //执行器解析
                Object parserResult = CodeParserExecutor.executeParser(completeBase, codeGenTypeEnum);
                //执行器保存
                File saveDir= CodeFileSaverExecutor.executeSaver(parserResult, codeGenTypeEnum,appId);
                //解析代码
                log.info("创建完成，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.info("创建失败", e.getMessage());
            }
        });
    }


}

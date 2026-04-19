package org.aistart.core;

import jakarta.annotation.Resource;
import org.aistart.core.AIfacade.AiCodeGeneratorFacade;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

@SpringBootTest
class AiCodeGeneratorFacadeTest {
@Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 100 行",
                CodeGenTypeEnum.VUE_PROJECT, 3L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("搞定了", result);
        Assertions.assertNotNull(completeContent);
    }
    /*@Test
    void generateAndSaveCode() {
       File file= aiCodeGeneratorFacade.
                generateAndSaveCode("我不想上学，怎么办？", CodeGenTypeEnum.BASE);
        Assertions.assertNotNull(file);
    }

    @Test
    void generateAndSaveCodeStream() {
        try {
            Flux<String> stringFluxStream = aiCodeGeneratorFacade.
                    generateAndSaveCodeStream("1+1=?",
                            CodeGenTypeEnum.BASE);
            //收集到阻塞，即输出完
            List<String> result = stringFluxStream.collectList().block();
            Assertions.assertNotNull(result);
        } catch (Exception e) {
            System.out.println("失败了");
        }

    }
    */
}
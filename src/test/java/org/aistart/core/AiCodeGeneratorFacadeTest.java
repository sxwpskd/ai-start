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
    void generateAndSaveCode() {
       File file= aiCodeGeneratorFacade.
                generateAndSaveCode("1+1=?", CodeGenTypeEnum.BASE);
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
}
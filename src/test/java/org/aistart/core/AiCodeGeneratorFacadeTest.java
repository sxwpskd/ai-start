package org.aistart.core;

import jakarta.annotation.Resource;
import org.aistart.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
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
}
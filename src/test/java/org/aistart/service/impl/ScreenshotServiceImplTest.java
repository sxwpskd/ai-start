package org.aistart.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@Slf4j
@SpringBootTest
class ScreenshotServiceImplTest {
    @Resource
    private ScreenshotServiceImpl screenshotService;
    @Test
    void generateAndUploadScreenshot() {
        String testUrl = "https://www.baidu.com";
        System.out.println("ready");
        screenshotService.generateAndUploadScreenshot(testUrl);
        System.out.println("ok");
    }
}
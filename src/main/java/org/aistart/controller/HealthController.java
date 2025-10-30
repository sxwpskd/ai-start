package org.aistart.controller;

import org.aistart.common.BaseResponse;
import org.aistart.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {
    @GetMapping("/")
    public BaseResponse<String> check() {
        return ResultUtils.success("OK");
    }
}
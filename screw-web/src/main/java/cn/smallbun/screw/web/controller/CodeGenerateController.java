/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.dto.CodeGenerateRequest;
import cn.smallbun.screw.web.service.CodeGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 代码生成：Java/C# 实体类
 */
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeGenerateController {

    private final CodeGenerateService codeGenerateService;

    /**
     * 生成实体代码
     *
     * @return Map&lt;表名, 代码内容&gt;
     */
    @PostMapping("/generate")
    public ApiResponse<Map<String, String>> generate(@RequestBody CodeGenerateRequest req) {
        return ApiResponse.ok(codeGenerateService.generate(req));
    }
}

/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.dto.DbCompareRequest;
import cn.smallbun.screw.web.service.DbCompareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DB 对比：源/目标库表结构差异
 */
@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class DbCompareController {

    private final DbCompareService dbCompareService;

    /**
     * 对比两个数据库结构
     */
    @PostMapping
    public ApiResponse<DbCompareService.CompareResult> compare(
        @RequestBody DbCompareRequest req) {
        return ApiResponse.ok(dbCompareService.compare(req));
    }
}

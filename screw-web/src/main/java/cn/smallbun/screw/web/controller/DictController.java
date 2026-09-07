/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口桩（/system/dict/*）
 * <p>
 * jimuqu-admin-ui 的 dict store 在登录/退出时可能触发字典缓存重置，但不强制依赖后端数据；
 * 提供空列表即可安全通过，避免 404/异常。
 * </p>
 */
@RestController
@RequestMapping("/system/dict")
public class DictController {

    /**
     * 字典数据列表
     */
    @GetMapping("/data/list")
    public ApiResponse<List<Object>> dataList() {
        return ApiResponse.ok(List.of());
    }

    /**
     * 字典类型列表
     */
    @GetMapping("/type/list")
    public ApiResponse<List<Object>> typeList() {
        return ApiResponse.ok(List.of());
    }

    /**
     * 字典类型下拉（optionselect）路由通常无需鉴权，返回空
     */
    @GetMapping("/type/optionselect")
    public ApiResponse<List<Object>> optionSelect() {
        return ApiResponse.ok(List.of());
    }
}

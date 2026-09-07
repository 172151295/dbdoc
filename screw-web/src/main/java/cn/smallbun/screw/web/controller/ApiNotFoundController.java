/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 兜底 404 控制器
 *
 * 前端使用的全部 API 前缀在此统一兜底：未被具体控制器匹配的请求
 * 返回 JSON 404，避免落入 SPA fallback（forward index.html 返回 HTML）。
 * Spring 模式特异性保证精确映射（如 /system/user/profile）优先于通配段，
 * 因此不会遮蔽任何已实现接口。
 */
@RestController
public class ApiNotFoundController {

    /**
     * 前端已使用的全部 API 前缀（对应 jimuqu-admin-ui/src/api）。
     * 内联数组字面量：注解属性要求常量表达式，static final 数组引用不行。
     */
    @RequestMapping({
        "/system/**", "/auth/**", "/monitor/**", "/resource/**", "/api/**"
    })
    public ApiResponse<Void> notFound() {
        return ApiResponse.error(404, "接口不存在");
    }
}

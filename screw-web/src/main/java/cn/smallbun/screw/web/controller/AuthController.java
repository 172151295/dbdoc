/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.entity.SysUser;
import cn.smallbun.screw.web.repository.SysUserRepository;
import cn.smallbun.screw.web.service.CaptchaService;
import cn.smallbun.screw.web.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口（对齐 jimuqu-admin-ui 登录流程）
 * <p>
 * 注意：前端 dev 代理会剥离 /dev-api，因此后端路径就是 /auth/*。
 * 前端 VITE_GLOB_ENABLE_ENCRYPT=false，登录请求为明文 JSON。
 * </p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserRepository userRepository;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 验证码（返回 captchaEnabled=false 时前端隐藏验证码框）
     */
    @GetMapping("/code")
    public ApiResponse<Map<String, Object>> code() {
        return ApiResponse.ok(captchaService.generate());
    }

    /**
     * 登录
     * body: { username, password, grantType, code?, uuid? }
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest req) {
        String username = req.getUsername();
        String password = req.getPassword();

        if (username == null || username.isBlank()) {
            return ApiResponse.error(500, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            return ApiResponse.error(500, "密码不能为空");
        }

        SysUser user = userRepository.findByUserName(username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ApiResponse.error(500, "用户名或密码错误");
        }
        if ("1".equals(user.getStatus())) {
            return ApiResponse.error(500, "账号已停用");
        }

        String token = tokenService.issue(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("access_token", token);
        data.put("client_id", "jimuqu-admin");
        data.put("expire_in", 86400);
        return ApiResponse.ok(data);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            tokenService.revoke(auth.substring(7));
        }
        return ApiResponse.ok();
    }

    /**
     * 权限码（以 token 中 userId 为准，简单返回空/通配）
     */
    @GetMapping("/codes")
    public ApiResponse<java.util.List<String>> codes(HttpServletRequest request) {
        return ApiResponse.ok(java.util.List.of("*:*:*"));
    }

    /**
     * 登录请求体
     */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private String grantType;
        private String code;
        private String uuid;
    }
}

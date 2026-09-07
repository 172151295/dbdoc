/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.config;

import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：保护系统接口（/system/**、/auth/codes）
 * <p>
 * 校验 Authorization: Bearer <token>，无效返回 401 JSON，与前端 handleUnauthorizedLogout 对齐。
 * 公共接口（/auth/code、/auth/login、/auth/logout、/resource/**）与业务 /api/** 不做强制鉴权，
 * 以保证 screw-web-ui 旧路径与 SSE 不受影响。
 * </p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        String token = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }
        if (token == null || tokenService.validate(token) == null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> body = ApiResponse.error(401, "登录状态已过期，请重新登录");
            response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
            return false;
        }
        return true;
    }
}

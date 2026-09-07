/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * 认证拦截器 + SPA 静态资源与前端路由配置
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public AuthWebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/system/**")
            .addPathPatterns("/auth/codes");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 所有非静态资源且无控制器的路径转发到 index.html（Vue SPA fallback）
        registry.addViewController("/{path:[^\\.]*}")
            .setViewName("forward:/index.html");
        registry.addViewController("/{path1:[^\\.]*}/{path2:[^\\.]*}")
            .setViewName("forward:/index.html");
        registry.addViewController("/{path1:[^\\.]*}/{path2:[^\\.]*}/{path3:[^\\.]*}")
            .setViewName("forward:/index.html");
    }

    /**
     * 生产前端请求带 /prod-api 前缀（Vite dev 代理前缀），后端控制器无此前缀。
     * 此过滤器将 /prod-api/xxx 通过 servlet forward 重写为 /xxx，
     * 使生产 JAR 内嵌前端可直接联调后端。
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> prodApiRewriteFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String uri = request.getRequestURI();
                if (uri.startsWith("/prod-api/") || "/prod-api".equals(uri)) {
                    String raw = uri.substring("/prod-api".length());
                    String newPath = raw.isEmpty() ? "/" : raw;
                    // 必须用真正的 servlet forward 重写路径。
                    // 不能用 HttpServletRequestWrapper 覆盖 getRequestURI()：
                    // SPA fallback 的 forward:/index.html 会把包装后的 API URI 记入
                    // FORWARD_REQUEST_URI，DispatcherServlet 在 forward 分发时按该属性
                    // 重复解析同一 API 路径 → 无限 forward → StackOverflowError。
                    request.getRequestDispatcher(newPath).forward(request, response);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/prod-api/*");
        bean.setOrder(Integer.MIN_VALUE);
        bean.setName("prodApiRewriteFilter");
        return bean;
    }

    /**
     * 请求体解密过滤器：处理前端 encrypt:true 的请求
     * （encrypt-key 头 + AES 密文体，见 EncryptRequestDecryptFilter 线格式说明）。
     * 必须注册 REQUEST + FORWARD 两种分发：/prod-api 重写走 forward 分发，
     * 仅注册 REQUEST 时 forward 后的密文请求将绕过本过滤器。
     */
    @Bean
    public FilterRegistrationBean<EncryptRequestDecryptFilter> encryptRequestDecryptFilter(
            @Value("${screw.security.rsa-private-key}") String rsaPrivateKey) {
        EncryptRequestDecryptFilter filter = new EncryptRequestDecryptFilter(rsaPrivateKey);
        FilterRegistrationBean<EncryptRequestDecryptFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD);
        bean.setOrder(Integer.MIN_VALUE + 1);
        bean.setName("encryptRequestDecryptFilter");
        return bean;
    }
}

/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * screw-web 启动类
 * <p>
 * 基于 screw-core 的数据库表结构文档生成服务，前端使用 soybean-admin。
 * </p>
 */
@SpringBootApplication
public class ScrewWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScrewWebApplication.class, args);
    }

    /**
     * 跨域配置，支持前端开发服务器（jimuqu-admin-ui dev 5666 / screw-web-ui dev 5173）
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .maxAge(3600);
            }
        };
    }
}

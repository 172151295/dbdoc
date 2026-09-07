/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.dto;

import lombok.Data;

import java.util.List;

/**
 * 代码生成请求
 */
@Data
public class CodeGenerateRequest {

    /**
     * 连接 ID
     */
    private Long connectionId;
    /**
     * 语言：JAVA / CSHARP
     */
    private String language;
    /**
     * 包名（Java）
     */
    private String packageName;
    /**
     * 命名空间（C#）
     */
    private String namespace;
    /**
     * 是否使用 Lombok（Java）
     */
    private Boolean lombok;
    /**
     * 是否生成 Swagger 注解
     */
    private Boolean swagger;
    /**
     * 指定表（为空则全部）
     */
    private List<String> tables;
}

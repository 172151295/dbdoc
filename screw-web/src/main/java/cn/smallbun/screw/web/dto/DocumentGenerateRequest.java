/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.dto;

import lombok.Data;

/**
 * 文档生成请求
 */
@Data
public class DocumentGenerateRequest {

    private Long connectionId;
    /**
     * 导出格式：HTML / WORD / MD / EXCEL
     */
    private String format;
    /**
     * 文档标题
     */
    private String title;
    /**
     * 版本号
     */
    private String version;
    /**
     * 描述
     */
    private String description;
    /**
     * 指定导出表（为空则导出全部）
     */
    private java.util.List<String> tables;
    /**
     * Excel Sheet 组织模式：SINGLE（所有表一个Sheet）/ PER_TABLE（每表一个Sheet，默认）
     */
    private String excelSheetMode;
}

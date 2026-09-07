/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.dto;

import lombok.Data;

/**
 * 连接保存/更新请求
 */
@Data
public class ConnectionSaveRequest {

    private Long id;
    private Long groupId;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String database;
    private String schemaName;
    private String username;
    private String password;
    private String remark;
}

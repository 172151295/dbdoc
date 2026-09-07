/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 数据库连接配置（密码密文存储）
 */
@Data
@Entity
@Table(name = "t_db_connection")
public class DbConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属分组
     */
    @Column(nullable = false)
    private Long groupId;

    /**
     * 连接名称
     */
    @Column(nullable = false, length = 64)
    private String name;

    /**
     * 数据库类型，与 screw DatabaseType 一致（MYSQL/ORACLE/POSTGRE_SQL/HIGHGO...）
     */
    @Column(nullable = false, length = 32)
    private String dbType;

    /**
     * 主机
     */
    @Column(length = 128)
    private String host;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 数据库名
     */
    @Column(length = 128)
    private String database;

    /**
     * schema（PG/瀚高 等使用）
     */
    @Column(length = 128)
    private String schemaName;

    /**
     * 用户名
     */
    @Column(length = 128)
    private String username;

    /**
     * 密码（AES 加密后存储）
     */
    @Column(length = 512)
    private String passwordEnc;

    /**
     * 备注
     */
    @Column(length = 512)
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

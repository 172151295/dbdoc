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
 * 系统用户（对齐 jimuqu-admin-ui 的 User 契约字段）
 */
@Data
@Entity
@Table(name = "t_sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 登录用户名
     */
    @Column(nullable = false, unique = true, length = 64)
    private String userName;

    /**
     * BCrypt 密码哈希
     */
    @Column(nullable = false, length = 100)
    private String password;

    /**
     * 昵称
     */
    @Column(length = 64)
    private String nickName;

    @Column(length = 255)
    private String avatar;

    @Column(length = 255)
    private String avatarUrl;

    @Column(length = 64)
    private String email;

    @Column(length = 32)
    private String phoneNumber;

    /**
     * 性别 0未知 1男 2女
     */
    @Column(length = 1)
    private String sex;

    /**
     * 状态 0正常 1停用
     */
    @Column(length = 1)
    private String status;

    /**
     * 用户类型 sys_user/system
     */
    @Column(length = 32)
    private String userType;

    @Column(length = 255)
    private String remark;

    private Long deptId;

    @Column(length = 64)
    private String deptName;

    @Column(length = 64)
    private String loginIp;

    private LocalDateTime loginDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

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
 * 系统菜单（对齐 jimuqu-admin-ui 的 Menu 契约；meta 展开存储，装配时组装 meta 对象）
 */
@Data
@Entity
@Table(name = "t_sys_menu")
public class SysMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 父级菜单ID，顶层为 0
     */
    private Long parentId;

    /**
     * 路由 name（与前端路由/菜单按 name 合并对齐）
     */
    @Column(nullable = false, length = 64)
    private String name;

    /**
     * 路由路径
     */
    @Column(nullable = false, length = 255)
    private String path;

    /**
     * 组件：Layout(顶层) | ParentView(分组) | views 相对路径如 screw/connection/index
     */
    @Column(length = 255)
    private String component;

    @Column(length = 255)
    private String redirect;

    /**
     * 是否在菜单隐藏
     */
    private Boolean hidden;

    private Boolean alwaysShow;

    /**
     * 路由 query（JSON 字符串）
     */
    @Column(length = 1000)
    private String query;

    /**
     * 扩展 meta（JSON 字符串，可含 order/badge/keepAlive 等）
     */
    @Column(length = 2000)
    private String ext;

    /* ------- meta 展开 ------- */

    /**
     * 菜单标题
     */
    @Column(length = 128)
    private String title;

    /**
     * 菜单图标（lucide:xxx 等）
     */
    @Column(length = 128)
    private String icon;

    /**
     * 外链地址
     */
    @Column(length = 1000)
    private String link;

    /**
     * 是否不缓存（keepAlive = !noCache）
     */
    private Boolean noCache;

    /**
     * 激活菜单路径
     */
    @Column(length = 255)
    private String activeMenu;

    /**
     * 排序
     */
    private Integer orderNum;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

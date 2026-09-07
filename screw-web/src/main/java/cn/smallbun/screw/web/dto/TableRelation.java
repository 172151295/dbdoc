/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.dto;

/**
 * 表外键关系（子表 → 父表）
 * <p>
 * 复合外键每列一行，同一 fkName 的各行构成一条关系。
 *
 * @param fkName       外键约束名
 * @param childTable   子表（外键所在表）
 * @param childColumn  子表列
 * @param parentTable  父表（被引用表，主键/唯一键所在表）
 * @param parentColumn 父表列
 */
public record TableRelation(String fkName, String childTable, String childColumn,
                            String parentTable, String parentColumn) {
}

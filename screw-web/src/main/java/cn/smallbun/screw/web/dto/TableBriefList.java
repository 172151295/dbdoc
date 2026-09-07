/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.dto;

import cn.smallbun.screw.core.metadata.model.TableModel;

import java.util.List;

/**
 * 轻量级表清单（"先清单、后按需拉列"的第一步）
 *
 * @param databaseType 数据库类型名（如 MySQL、Oracle），供页头展示
 * @param tables       表/视图清单（仅表名/说明/类型，不含列，千表大库秒级返回）
 */
public record TableBriefList(String databaseType, List<TableModel> tables) {
}

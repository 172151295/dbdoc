/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.controller;

import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.TableModel;
import cn.smallbun.screw.web.common.ApiResponse;
import cn.smallbun.screw.web.dto.TableBriefList;
import cn.smallbun.screw.web.dto.TableRelation;
import cn.smallbun.screw.web.service.MetadataService;
import cn.smallbun.screw.web.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据查询
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;
    private final RelationService relationService;

    /**
     * 获取完整表结构（表/列/主键/注释）——全量模型，千表大库耗时长，保留供旧功能使用
     */
    @GetMapping("/{connectionId}")
    public ApiResponse<DataModel> metadata(@PathVariable Long connectionId) {
        return ApiResponse.ok(metadataService.metadata(connectionId));
    }

    /**
     * 轻量级表清单（仅表名/说明/类型，不读列；千表大库秒级返回）
     * 供对象导出/代码生成页"先清单、后按需拉列"的渐进式加载。
     */
    @GetMapping("/{connectionId}/tables")
    public ApiResponse<TableBriefList> tables(@PathVariable Long connectionId) {
        return ApiResponse.ok(metadataService.tableList(connectionId));
    }

    /**
     * 单表列元数据（列+主键标记，按需加载）。
     * table 参数须使用表清单下发的精确名称（部分库对大小写敏感）。
     */
    @GetMapping("/{connectionId}/columns")
    public ApiResponse<TableModel> tableColumns(@PathVariable Long connectionId,
        @RequestParam String table) {
        return ApiResponse.ok(metadataService.tableColumns(connectionId, table));
    }

    /**
     * 获取外键关系（关系图数据源，单条 SQL 采集，不依赖完整元数据）
     */
    @GetMapping("/{connectionId}/relations")
    public ApiResponse<List<TableRelation>> relations(@PathVariable Long connectionId) {
        return ApiResponse.ok(relationService.relations(connectionId));
    }
}

/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.core.metadata.model.ColumnModel;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.TableModel;
import cn.smallbun.screw.web.dto.DbCompareRequest;
import cn.smallbun.screw.web.entity.DbConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * DB 对比服务：对比源/目标库的表结构差异
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbCompareService {

    private final MetadataService metadataService;
    private final ConnectionService connectionService;
    private final CompareSyncService compareSyncService;

    /**
     * 对比结果
     */
    public record ColumnDiff(String table, String column, String source, String target,
                             String type) {
    }

    public record TableDiff(String table, String type, String detail) {
    }

    /**
     * 对比结果（含同步脚本：以源库为准，在目标库执行）
     */
    public record CompareResult(List<String> onlySourceTables, List<String> onlyTargetTables,
                                List<TableDiff> tableDiffs, List<ColumnDiff> columnDiffs,
                                Map<String, Integer> summary, String syncScript,
                                String syncDialect, List<String> syncWarnings) {
    }

    /**
     * 对比两个库
     */
    public CompareResult compare(DbCompareRequest req) {
        DataModel source = metadataService.metadata(req.getSourceId());
        DataModel target = metadataService.metadata(req.getTargetId());

        Map<String, TableModel> srcTables = index(source);
        Map<String, TableModel> tgtTables = index(target);

        List<String> onlySource = new ArrayList<>();
        List<String> onlyTarget = new ArrayList<>();
        List<TableDiff> tableDiffs = new ArrayList<>();
        List<ColumnDiff> columnDiffs = new ArrayList<>();

        Set<String> all = new TreeSet<>();
        all.addAll(srcTables.keySet());
        all.addAll(tgtTables.keySet());

        for (String table : all) {
            TableModel s = srcTables.get(table);
            TableModel t = tgtTables.get(table);
            if (s == null) {
                onlyTarget.add(table);
                continue;
            }
            if (t == null) {
                onlySource.add(table);
                continue;
            }
            // 表注释差异
            if (!nvl(s.getRemarks()).equals(nvl(t.getRemarks()))) {
                tableDiffs.add(new TableDiff(table, "REMARK",
                    nvl(s.getRemarks()) + " → " + nvl(t.getRemarks())));
            }
            // 列对比
            Map<String, ColumnModel> srcCols = indexCols(s);
            Map<String, ColumnModel> tgtCols = indexCols(t);
            Set<String> colAll = new TreeSet<>();
            colAll.addAll(srcCols.keySet());
            colAll.addAll(tgtCols.keySet());
            for (String col : colAll) {
                ColumnModel sc = srcCols.get(col);
                ColumnModel tc = tgtCols.get(col);
                if (sc == null) {
                    columnDiffs.add(new ColumnDiff(table, col, "∅", describe(tc), "COLUMN_MISSING_IN_SOURCE"));
                } else if (tc == null) {
                    columnDiffs.add(new ColumnDiff(table, col, describe(sc), "∅", "COLUMN_MISSING_IN_TARGET"));
                } else {
                    // 类型/长度/可空/默认值差异
                    String srcDesc = describe(sc);
                    String tgtDesc = describe(tc);
                    if (!srcDesc.equals(tgtDesc)) {
                        columnDiffs.add(new ColumnDiff(table, col, srcDesc, tgtDesc, "COLUMN_DIFF"));
                    }
                    // 主键差异
                    boolean srcPk = isPk(sc);
                    boolean tgtPk = isPk(tc);
                    if (srcPk != tgtPk) {
                        columnDiffs.add(new ColumnDiff(table, col,
                            srcPk ? "PK" : "非PK", tgtPk ? "PK" : "非PK", "PK_DIFF"));
                    }
                }
            }
        }

        // 汇总
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("onlySourceTables", onlySource.size());
        summary.put("onlyTargetTables", onlyTarget.size());
        summary.put("tableDiffs", tableDiffs.size());
        summary.put("columnDiffs", columnDiffs.size());

        // 同步脚本：同一次元数据扫描内生成（千表库零额外开销）
        String syncScript = null;
        String syncDialect = null;
        List<String> syncWarnings = null;
        try {
            DbConnection srcConn = connectionService.get(req.getSourceId());
            DbConnection tgtConn = connectionService.get(req.getTargetId());
            CompareSyncService.SyncPlan plan = compareSyncService.generate(source, target,
                srcConn.getDbType(), srcConn.getName(), tgtConn.getDbType(), tgtConn.getName());
            summary.putAll(plan.counts());
            syncScript = plan.script();
            syncDialect = plan.dialect();
            syncWarnings = plan.warnings();
        } catch (Exception e) {
            log.warn("同步脚本生成失败（不影响对比结果）: {}", e.getMessage());
            syncWarnings = List.of("同步脚本生成失败: " + e.getMessage());
        }

        return new CompareResult(onlySource, onlyTarget, tableDiffs, columnDiffs, summary,
            syncScript, syncDialect, syncWarnings);
    }

    // ==================== 工具 ====================

    private Map<String, TableModel> index(DataModel model) {
        Map<String, TableModel> map = new LinkedHashMap<>();
        if (model.getTables() != null) {
            for (TableModel t : model.getTables()) {
                map.put(t.getTableName(), t);
            }
        }
        return map;
    }

    private Map<String, ColumnModel> indexCols(TableModel table) {
        Map<String, ColumnModel> map = new LinkedHashMap<>();
        if (table.getColumns() != null) {
            for (ColumnModel c : table.getColumns()) {
                map.put(c.getColumnName(), c);
            }
        }
        return map;
    }

    private String describe(ColumnModel c) {
        StringBuilder sb = new StringBuilder();
        sb.append(nvl(c.getColumnType()).isEmpty() ? nvl(c.getTypeName()) : nvl(c.getColumnType()));
        if (c.getColumnLength() != null && !c.getColumnLength().isEmpty()
            && !"0".equals(c.getColumnLength())) {
            sb.append("(").append(c.getColumnLength());
            if (c.getDecimalDigits() != null && !c.getDecimalDigits().isEmpty()
                && !"0".equals(c.getDecimalDigits())) {
                sb.append(",").append(c.getDecimalDigits());
            }
            sb.append(")");
        }
        if ("NO".equalsIgnoreCase(nvl(c.getNullable())) || "false".equalsIgnoreCase(nvl(c.getNullable()))) {
            sb.append(" NOT NULL");
        }
        if (c.getColumnDef() != null && !c.getColumnDef().isEmpty()) {
            sb.append(" DEFAULT ").append(c.getColumnDef());
        }
        return sb.toString();
    }

    private boolean isPk(ColumnModel c) {
        String pk = nvl(c.getPrimaryKey());
        return "true".equalsIgnoreCase(pk) || "YES".equalsIgnoreCase(pk)
            || "PRI".equalsIgnoreCase(pk);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}

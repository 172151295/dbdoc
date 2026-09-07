/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.core.metadata.model.ColumnModel;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.TableModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 对比同步脚本生成：以源库为准，生成在目标库执行的方言适配 DDL。
 * <p>
 * 覆盖：建表（目标缺失）、加列、改列（类型/可空/默认值/注释）、表注释。
 * 不做破坏性操作：目标多出的表/列仅提示，不生成 DROP。
 */
@Slf4j
@Service
public class CompareSyncService {

    /** 方言语法族（目标库执行语法按此生成） */
    public enum Dialect {
        MYSQL("MySQL 系"), PG("PostgreSQL/瀚高 系"), ORACLE("Oracle");

        private final String label;

        Dialect(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** 按连接 dbType 判定方言族；不支持的返回 null */
        public static Dialect of(String dbType) {
            switch (dbType == null ? "" : dbType) {
                case "MYSQL":
                case "MARIADB":
                case "DORIS":
                case "TIDB":
                case "OCEANBASE":
                    return MYSQL;
                case "POSTGRE_SQL":
                case "HIGHGO":
                case "KINGBASE_ES":
                    return PG;
                case "ORACLE":
                    return ORACLE;
                default:
                    return null;
            }
        }
    }

    /** 生成结果：脚本 + 方言 + 统计 + 警告 */
    public record SyncPlan(String script, String dialect, Map<String, Integer> counts,
                           List<String> warnings) {
    }

    private static final SimpleDateFormat TS_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成同步脚本（compare 单次元数据扫描内调用，零额外查询）
     */
    public SyncPlan generate(DataModel source, DataModel target, String sourceDbType,
                             String sourceName, String targetDbType, String targetName) {
        Dialect srcD = Dialect.of(sourceDbType);
        Dialect tgtD = Dialect.of(targetDbType);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("syncCreateTables", 0);
        counts.put("syncAddColumns", 0);
        counts.put("syncModifyColumns", 0);
        counts.put("syncTableComments", 0);
        counts.put("syncColumnComments", 0);
        counts.put("syncSkippedTargetOnlyTables", 0);
        counts.put("syncSkippedTargetOnlyColumns", 0);
        List<String> warnings = new ArrayList<>();
        String dialectName = tgtD == null ? targetDbType : tgtD.label();

        if (tgtD == null) {
            warnings.add("目标库类型 " + targetDbType
                + " 暂不支持生成同步脚本（当前支持 MySQL 系 / PostgreSQL·瀚高 系 / Oracle）");
            return new SyncPlan(null, dialectName, counts, warnings);
        }
        if (srcD == null) {
            warnings.add("源库类型 " + sourceDbType + " 未内置类型映射，列类型将按原样生成，请人工复核");
        }

        Ctx ctx = new Ctx();
        ctx.srcD = srcD == null ? tgtD : srcD;
        ctx.tgtD = tgtD;
        ctx.cross = srcD != null && srcD != tgtD;
        ctx.counts = counts;
        ctx.warnings = warnings;
        ctx.warnedTypes = new LinkedHashSet<>();

        Map<String, TableModel> srcT = indexTables(source);
        Map<String, TableModel> tgtT = indexTables(target);
        Set<String> all = new TreeSet<>();
        all.addAll(srcT.keySet());
        all.addAll(tgtT.keySet());

        List<String> onlyTargetTables = new ArrayList<>();
        List<String> views = new ArrayList<>();
        StringBuilder body = new StringBuilder(4096);

        for (String table : all) {
            TableModel s = srcT.get(table);
            TableModel t = tgtT.get(table);
            if (isView(s) || isView(t)) {
                views.add(table);
                continue;
            }
            if (s == null) {
                onlyTargetTables.add(table);
                continue;
            }
            if (t == null) {
                emitCreateTable(body, ctx, table, s);
                continue;
            }
            emitAlter(body, ctx, table, s, t);
        }

        if (!onlyTargetTables.isEmpty()) {
            counts.put("syncSkippedTargetOnlyTables", onlyTargetTables.size());
            warnings.add("目标多出 " + onlyTargetTables.size() + " 张表未处理（不做删除），请人工确认："
                + brief(onlyTargetTables));
        }
        if (counts.get("syncSkippedTargetOnlyColumns") > 0) {
            warnings.add("目标多出 " + counts.get("syncSkippedTargetOnlyColumns")
                + " 个列未处理（不做删除），请人工确认");
        }
        if (ctx.pkDiffTables.size() > 0) {
            warnings.add("主键差异 " + ctx.pkDiffTables.size()
                + " 张表未自动同步（涉及表：" + brief(new ArrayList<>(ctx.pkDiffTables)) + "），请手工处理");
        }
        if (ctx.cross) {
            warnings.add("跨方言同步（" + (srcD == null ? sourceDbType : srcD.label()) + " → "
                + tgtD.label() + "），列类型已按常见映射转换，执行前请人工复核类型与默认值");
        }
        if (ctx.oracleNumberLoose) {
            warnings.add("Oracle NUMBER 精度信息在元数据中不可靠，相关列按无约束 NUMBER 生成，请人工复核");
        }
        if (ctx.notNullNoDefaultCols > 0) {
            warnings.add("有 " + ctx.notNullNoDefaultCols
                + " 个新增列为 NOT NULL 且无默认值，若目标表已有数据，执行会失败，请先补默认值");
        }
        if (!views.isEmpty()) {
            warnings.add("视图不参��同步（" + views.size() + " 个）");
        }

        StringBuilder sb = new StringBuilder(4096);
        emitHeader(sb, ctx, sourceName, sourceDbType, targetName, targetDbType);
        sb.append(body);
        if (body.length() == 0) {
            sb.append("-- 未发现需要同步的结构差异\n");
        }
        return new SyncPlan(sb.toString(), dialectName, counts, warnings);
    }

    // ==================== 上下文 ====================

    private static class Ctx {
        Dialect srcD;
        Dialect tgtD;
        boolean cross;
        Map<String, Integer> counts;
        List<String> warnings;
        Set<String> warnedTypes;
        final Set<String> pkDiffTables = new LinkedHashSet<>();
        boolean oracleNumberLoose;
        int notNullNoDefaultCols;
    }

    private static void inc(Ctx ctx, String key) {
        ctx.counts.merge(key, 1, Integer::sum);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /** 长列表摘要：前 10 个 + 省略 */
    private static String brief(List<String> list) {
        List<String> sub = list.subList(0, Math.min(10, list.size()));
        return String.join(", ", sub) + (list.size() > 10 ? " 等" : "");
    }

    private static boolean isView(TableModel t) {
        return t != null && t.getTableType() != null
            && t.getTableType().toUpperCase(Locale.ROOT).contains("VIEW");
    }

    private static Map<String, TableModel> indexTables(DataModel model) {
        Map<String, TableModel> map = new LinkedHashMap<>();
        if (model != null && model.getTables() != null) {
            for (TableModel t : model.getTables()) {
                map.put(t.getTableName(), t);
            }
        }
        return map;
    }

    private static boolean isPk(ColumnModel c) {
        String pk = nvl(c.getPrimaryKey());
        return "true".equalsIgnoreCase(pk) || "YES".equalsIgnoreCase(pk) || "PRI".equalsIgnoreCase(pk);
    }

    private static boolean notNull(ColumnModel c) {
        return "NO".equalsIgnoreCase(nvl(c.getNullable())) || "false".equalsIgnoreCase(nvl(c.getNullable()));
    }

    private static List<ColumnModel> sortedCols(TableModel t) {
        List<ColumnModel> cols = new ArrayList<>(t.getColumns() == null ? List.of() : t.getColumns());
        cols.sort((a, b) -> {
            int ia = parseIntOrMax(a.getOrdinalPosition());
            int ib = parseIntOrMax(b.getOrdinalPosition());
            return ia != ib ? Integer.compare(ia, ib) : a.getColumnName().compareTo(b.getColumnName());
        });
        return cols;
    }

    private static int parseIntOrMax(String s) {
        try {
            return s == null ? Integer.MAX_VALUE : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    // ==================== 头部 ====================

    private static void emitHeader(StringBuilder sb, Ctx ctx, String srcName, String srcDbType,
                                   String tgtName, String tgtDbType) {
        sb.append("-- =====================================================================\n");
        sb.append("-- 数据库结构同步脚本（以源库为准）\n");
        sb.append("-- 源库: ").append(nvl(srcName)).append(" [").append(srcDbType)
            .append("]  ->  目标库: ").append(nvl(tgtName)).append(" [").append(tgtDbType)
            .append("]（脚本在目标库执行）\n");
        sb.append("-- 目标方言: ").append(ctx.tgtD.label()).append("    生成时间: ")
            .append(TS_FMT.format(new Date())).append('\n');
        sb.append("-- 统计: 建表 ").append(ctx.counts.get("syncCreateTables"))
            .append(" | 新增列 ").append(ctx.counts.get("syncAddColumns"))
            .append(" | 修改列 ").append(ctx.counts.get("syncModifyColumns"))
            .append(" | 表注释 ").append(ctx.counts.get("syncTableComments"))
            .append(" | 列注释 ").append(ctx.counts.get("syncColumnComments"))
            .append('\n');
        sb.append("-- ⚠ 执行前请备份目标库，并先在测试环境验证\n");
        if (!ctx.warnings.isEmpty()) {
            sb.append("-- 注意事项:\n");
            for (String w : ctx.warnings) {
                sb.append("--   ! ").append(w.replace("\n", " ")).append('\n');
            }
        }
        sb.append("-- =====================================================================\n\n");
    }

    // ==================== 建表（目标缺失的源表） ====================

    private static void emitCreateTable(StringBuilder sb, Ctx ctx, String table, TableModel s) {
        List<ColumnModel> cols = sortedCols(s);
        if (cols.isEmpty()) {
            return;
        }
        List<String> defs = new ArrayList<>();
        List<String> pkCols = new ArrayList<>();
        for (ColumnModel c : cols) {
            String type = typeExpr(c, ctx, true);
            if (type == null) {
                continue;
            }
            if (isPk(c)) {
                pkCols.add(c.getColumnName());
            }
            if (notNull(c) && nvl(c.getColumnDef()).isEmpty()) {
                ctx.notNullNoDefaultCols++;
            }
            StringBuilder d = new StringBuilder("  ").append(c.getColumnName()).append(' ')
                .append(type);
            if (ctx.tgtD == Dialect.MYSQL) {
                if (notNull(c)) {
                    d.append(" NOT NULL");
                }
                if (defaultExpr(c, ctx) != null && !defaultExpr(c, ctx).isEmpty()) {
                    d.append(" DEFAULT ").append(defaultExpr(c, ctx));
                }
                if (!nvl(c.getRemarks()).isEmpty()) {
                    d.append(" COMMENT '").append(esc(c.getRemarks())).append('\'');
                }
            } else if (ctx.tgtD == Dialect.ORACLE) {
                if (defaultExpr(c, ctx) != null && !defaultExpr(c, ctx).isEmpty()) {
                    d.append(" DEFAULT ").append(defaultExpr(c, ctx));
                }
                if (notNull(c)) {
                    d.append(" NOT NULL");
                }
            } else {
                if (defaultExpr(c, ctx) != null && !defaultExpr(c, ctx).isEmpty()) {
                    d.append(" DEFAULT ").append(defaultExpr(c, ctx));
                }
                if (notNull(c)) {
                    d.append(" NOT NULL");
                }
            }
            defs.add(d.toString());
        }
        if (defs.isEmpty()) {
            return;
        }
        sb.append("-- ---------- 表 ").append(table).append(": 目标缺失，创建 ----------\n");
        String ifNotExists = ctx.tgtD == Dialect.ORACLE ? "" : "IF NOT EXISTS ";
        sb.append("CREATE TABLE ").append(ifNotExists).append(table).append(" (\n");
        sb.append(String.join(",\n", defs));
        if (!pkCols.isEmpty()) {
            sb.append(",\n  PRIMARY KEY (").append(String.join(", ", pkCols)).append(')');
        }
        sb.append("\n)");
        if (ctx.tgtD == Dialect.MYSQL && !nvl(s.getRemarks()).isEmpty()) {
            sb.append(" COMMENT='").append(esc(s.getRemarks())).append('\'');
        }
        sb.append(";\n");
        emitComments(sb, ctx, table, s, cols, pkCols);
        sb.append('\n');
        inc(ctx, "syncCreateTables");
    }

    /** PG/Oracle 建表后的独立注释语句（MySQL 注释已内联） */
    private static void emitComments(StringBuilder sb, Ctx ctx, String table, TableModel t,
                                     List<ColumnModel> cols, List<String> pkCols) {
        if (ctx.tgtD == Dialect.MYSQL) {
            return;
        }
        if (!nvl(t.getRemarks()).isEmpty()) {
            sb.append("COMMENT ON TABLE ").append(table).append(" IS '")
                .append(esc(t.getRemarks())).append("';\n");
            inc(ctx, "syncTableComments");
        }
        for (ColumnModel c : cols) {
            if (!nvl(c.getRemarks()).isEmpty()) {
                sb.append("COMMENT ON COLUMN ").append(table).append('.').append(c.getColumnName())
                    .append(" IS '").append(esc(c.getRemarks())).append("';\n");
                inc(ctx, "syncColumnComments");
            }
        }
    }

    // ==================== 已有表：加列 / 改列 / 注释 ====================

    private static void emitAlter(StringBuilder sb, Ctx ctx, String table, TableModel s,
                                  TableModel t) {
        Map<String, ColumnModel> srcCols = indexCols(s);
        Map<String, ColumnModel> tgtCols = indexCols(t);
        List<String> lines = new ArrayList<>();
        List<String> comments = new ArrayList<>();

        // 表注释差异
        if (!nvl(s.getRemarks()).equals(nvl(t.getRemarks())) && !nvl(s.getRemarks()).isEmpty()) {
            if (ctx.tgtD == Dialect.MYSQL) {
                lines.add("ALTER TABLE " + table + " COMMENT='" + esc(s.getRemarks()) + "';");
            } else {
                comments.add("COMMENT ON TABLE " + table + " IS '" + esc(s.getRemarks()) + "';");
            }
            inc(ctx, "syncTableComments");
        }

        // 目标多出的列（不做删除）
        int targetOnly = 0;
        for (String col : tgtCols.keySet()) {
            if (!srcCols.containsKey(col)) {
                targetOnly++;
            }
        }
        if (targetOnly > 0) {
            ctx.counts.merge("syncSkippedTargetOnlyColumns", targetOnly, Integer::sum);
        }

        // 列级差异
        for (Map.Entry<String, ColumnModel> e : srcCols.entrySet()) {
            String col = e.getKey();
            ColumnModel sc = e.getValue();
            ColumnModel tc = tgtCols.get(col);

            if (tc == null) {
                // 目标缺失列 -> ADD
                lines.add("-- " + col + ": 目标缺失列（源: " + describeBrief(sc) + "）");
                String type = typeExpr(sc, ctx, true);
                if (type == null) {
                    continue;
                }
                String def = defaultExpr(sc, ctx);
                if (notNull(sc) && def.isEmpty()) {
                    ctx.notNullNoDefaultCols++;
                }
                String comment = nvl(sc.getRemarks());
                if (ctx.tgtD == Dialect.MYSQL) {
                    StringBuilder d = new StringBuilder("ALTER TABLE ").append(table)
                        .append(" ADD COLUMN ").append(col).append(' ').append(type);
                    if (notNull(sc)) {
                        d.append(" NOT NULL");
                    }
                    if (!def.isEmpty()) {
                        d.append(" DEFAULT ").append(def);
                    }
                    if (!comment.isEmpty()) {
                        d.append(" COMMENT '").append(esc(comment)).append('\'');
                    }
                    d.append(';');
                    lines.add(d.toString());
                } else if (ctx.tgtD == Dialect.ORACLE) {
                    StringBuilder d = new StringBuilder("ALTER TABLE ").append(table)
                        .append(" ADD (").append(col).append(' ').append(type);
                    if (!def.isEmpty()) {
                        d.append(" DEFAULT ").append(def);
                    }
                    if (notNull(sc)) {
                        d.append(" NOT NULL");
                    }
                    d.append(");");
                    lines.add(d.toString());
                    if (!comment.isEmpty()) {
                        comments.add("COMMENT ON COLUMN " + table + '.' + col + " IS '"
                            + esc(comment) + "';");
                    }
                } else {
                    StringBuilder d = new StringBuilder("ALTER TABLE ").append(table)
                        .append(" ADD COLUMN ").append(col).append(' ').append(type);
                    if (!def.isEmpty()) {
                        d.append(" DEFAULT ").append(def);
                    }
                    if (notNull(sc)) {
                        d.append(" NOT NULL");
                    }
                    d.append(';');
                    lines.add(d.toString());
                    if (!comment.isEmpty()) {
                        comments.add("COMMENT ON COLUMN " + table + '.' + col + " IS '"
                            + esc(comment) + "';");
                    }
                }
                inc(ctx, "syncAddColumns");
                continue;
            }

            // 主键差异：仅提示
            if (isPk(sc) != isPk(tc)) {
                ctx.pkDiffTables.add(table);
            }

            // 同名列：类型 / 可空 / 默认值 / 注释 差异
            String srcType = typeExpr(sc, ctx, false);
            String tgtType = typeExpr(tc, ctx, false);
            boolean typeDiff = srcType != null && !srcType.equalsIgnoreCase(tgtType);
            boolean nullDiff = notNull(sc) != notNull(tc);
            boolean defDiff = !nvl(sc.getColumnDef()).trim()
                .equals(nvl(tc.getColumnDef()).trim());
            boolean remarkDiff = !nvl(sc.getRemarks()).equals(nvl(tc.getRemarks()));

            if (!typeDiff && !nullDiff && !defDiff && !remarkDiff) {
                continue;
            }

            String changeDesc = " -- " + col + ": " + describeBrief(sc) + " -> " + describeBrief(tc);

            if (ctx.tgtD == Dialect.MYSQL) {
                // MySQL MODIFY 会重置未声明的属性，必须带全源列定义
                StringBuilder d = new StringBuilder("ALTER TABLE ").append(table)
                    .append(" MODIFY COLUMN ").append(col).append(' ').append(srcType);
                if (notNull(sc)) {
                    d.append(" NOT NULL");
                }
                if (!defaultExpr(sc, ctx).isEmpty()) {
                    d.append(" DEFAULT ").append(defaultExpr(sc, ctx));
                }
                if (!nvl(sc.getRemarks()).isEmpty()) {
                    d.append(" COMMENT '").append(esc(sc.getRemarks())).append('\'');
                }
                d.append(';');
                lines.add(d.toString() + changeDesc);
                inc(ctx, "syncModifyColumns");
                if (remarkDiff) {
                    inc(ctx, "syncColumnComments");
                }
            } else if (ctx.tgtD == Dialect.ORACLE) {
                List<String> parts = new ArrayList<>();
                if (typeDiff) {
                    parts.add(srcType);
                }
                if (!defaultExpr(sc, ctx).isEmpty()) {
                    parts.add("DEFAULT " + defaultExpr(sc, ctx));
                } else if (defDiff) {
                    parts.add("DEFAULT NULL");
                }
                if (nullDiff && notNull(sc)) {
                    parts.add("NOT NULL");
                } else if (nullDiff) {
                    parts.add("NULL");
                }
                if (!parts.isEmpty()) {
                    lines.add("ALTER TABLE " + table + " MODIFY (" + col + ' '
                        + String.join(" ", parts) + ");" + changeDesc);
                    inc(ctx, "syncModifyColumns");
                }
                if (remarkDiff) {
                    comments.add("COMMENT ON COLUMN " + table + '.' + col + " IS '"
                        + esc(sc.getRemarks()) + "';");
                    inc(ctx, "syncColumnComments");
                }
            } else {
                // PG 系：可合并为单条 ALTER COLUMN
                List<String> parts = new ArrayList<>();
                if (typeDiff) {
                    parts.add("ALTER COLUMN " + col + " TYPE " + srcType);
                }
                if (nullDiff) {
                    parts.add("ALTER COLUMN " + col + (notNull(sc) ? " SET NOT NULL" : " DROP NOT NULL"));
                }
                if (!defaultExpr(sc, ctx).isEmpty()) {
                    parts.add("ALTER COLUMN " + col + " SET DEFAULT " + defaultExpr(sc, ctx));
                } else if (defDiff) {
                    parts.add("ALTER COLUMN " + col + " DROP DEFAULT");
                }
                if (!parts.isEmpty()) {
                    lines.add("ALTER TABLE " + table + ' ' + String.join(", ", parts) + ";"
                        + changeDesc);
                    inc(ctx, "syncModifyColumns");
                }
                if (remarkDiff) {
                    comments.add("COMMENT ON COLUMN " + table + '.' + col + " IS '"
                        + esc(sc.getRemarks()) + "';");
                    inc(ctx, "syncColumnComments");
                }
            }
        }

        if (!lines.isEmpty() || !comments.isEmpty()) {
            sb.append("-- ---------- 表 ").append(table).append(": 差异同步 ----------\n");
            for (String l : lines) {
                sb.append(l).append('\n');
            }
            for (String cm : comments) {
                sb.append(cm).append('\n');
            }
            sb.append('\n');
        }
    }

    private static Map<String, ColumnModel> indexCols(TableModel t) {
        Map<String, ColumnModel> map = new LinkedHashMap<>();
        if (t.getColumns() != null) {
            for (ColumnModel c : t.getColumns()) {
                map.put(c.getColumnName(), c);
            }
        }
        return map;
    }

    private static String describeBrief(ColumnModel c) {
        String type = nvl(c.getColumnType()).isEmpty() ? nvl(c.getTypeName()) : nvl(c.getColumnType());
        String s = c.getColumnName() + ' ' + type;
        if (notNull(c)) {
            s += " NOT NULL";
        }
        return s;
    }

    // ==================== 类型表达式 ====================

    /** 不允许拼长度的类型（跨方言并集；对目标方言多删不拼，安全） */
    private static final Set<String> NO_LENGTH_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
        "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
        "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
        "JSON", "JSONB", "CLOB", "NCLOB", "BOOL", "BOOLEAN",
        "GEOMETRY", "POINT", "LINESTRING", "POLYGON",
        "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION",
        "BYTEA", "UUID", "INET", "CIDR", "MACADDR", "MONEY", "XML", "XMLTYPE",
        "TSVECTOR", "TSQUERY", "DOUBLE PRECISION", "BINARY_DOUBLE", "BINARY_FLOAT",
        "INTERVAL", "ENUM", "SET", "YEAR", "LONG", "LONG RAW"));

    /** 整数/浮点：不拼显示宽度（int4(10) 之类即非法） */
    private static final Set<String> NUMERIC_NO_WIDTH_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
        "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
        "INT2", "INT4", "INT8", "OID",
        "SERIAL2", "SERIAL4", "SERIAL8", "SMALLSERIAL", "BIGSERIAL", "SERIAL",
        "FLOAT", "DOUBLE", "REAL"));

    /** 日期时间：仅小数秒精度 >0 时拼 (digits) */
    private static final Set<String> DATETIME_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
        "DATETIME", "TIMESTAMP", "TIME", "DATE", "DATETIME2", "TIMESTAMPTZ", "TIMETZ"));

    /** 列长超过该值视为无约束（PG unconstrained varchar=2147483647 等） */
    private static final int UNCONSTRAINED_THRESHOLD = 100000;

    private static final Map<String, String> MYSQL_TO_PG = Map.ofEntries(
        Map.entry("tinyint", "smallint"), Map.entry("smallint", "smallint"),
        Map.entry("mediumint", "integer"), Map.entry("int", "integer"), Map.entry("integer", "integer"),
        Map.entry("bigint", "bigint"), Map.entry("year", "smallint"),
        Map.entry("varchar", "varchar"), Map.entry("char", "char"),
        Map.entry("tinytext", "text"), Map.entry("text", "text"), Map.entry("mediumtext", "text"),
        Map.entry("longtext", "text"),
        Map.entry("tinyblob", "bytea"), Map.entry("blob", "bytea"), Map.entry("mediumblob", "bytea"),
        Map.entry("longblob", "bytea"), Map.entry("binary", "bytea"), Map.entry("varbinary", "bytea"),
        Map.entry("bit", "boolean"), Map.entry("bool", "boolean"), Map.entry("boolean", "boolean"),
        Map.entry("float", "real"), Map.entry("double", "double precision"), Map.entry("real", "real"),
        Map.entry("decimal", "numeric"), Map.entry("numeric", "numeric"),
        Map.entry("datetime", "timestamp"), Map.entry("date", "date"), Map.entry("time", "time"),
        Map.entry("timestamp", "timestamp"),
        Map.entry("json", "jsonb"), Map.entry("enum", "varchar"), Map.entry("set", "varchar"),
        Map.entry("geometry", "geometry"), Map.entry("point", "point"));

    private static final Map<String, String> MYSQL_TO_ORACLE = Map.ofEntries(
        Map.entry("tinyint", "number"), Map.entry("smallint", "number"), Map.entry("mediumint", "number"),
        Map.entry("int", "number"), Map.entry("integer", "number"), Map.entry("bigint", "number"),
        Map.entry("year", "number"),
        Map.entry("varchar", "varchar2"), Map.entry("char", "char"),
        Map.entry("tinytext", "clob"), Map.entry("text", "clob"), Map.entry("mediumtext", "clob"),
        Map.entry("longtext", "clob"),
        Map.entry("tinyblob", "blob"), Map.entry("blob", "blob"), Map.entry("mediumblob", "blob"),
        Map.entry("longblob", "blob"), Map.entry("binary", "raw"), Map.entry("varbinary", "raw"),
        Map.entry("bit", "number"), Map.entry("bool", "number"), Map.entry("boolean", "number"),
        Map.entry("float", "binary_float"), Map.entry("double", "binary_double"), Map.entry("real", "binary_float"),
        Map.entry("decimal", "number"), Map.entry("numeric", "number"),
        Map.entry("datetime", "date"), Map.entry("date", "date"), Map.entry("time", "date"),
        Map.entry("timestamp", "timestamp"),
        Map.entry("json", "clob"), Map.entry("enum", "varchar2"), Map.entry("set", "varchar2"));

    private static final Map<String, String> PG_TO_MYSQL = Map.ofEntries(
        Map.entry("int2", "smallint"), Map.entry("int4", "int"), Map.entry("int8", "bigint"),
        Map.entry("oid", "int"), Map.entry("smallserial", "smallint"), Map.entry("serial2", "smallint"),
        Map.entry("serial", "int"), Map.entry("serial4", "int"), Map.entry("bigserial", "bigint"),
        Map.entry("serial8", "bigint"),
        Map.entry("varchar", "varchar"), Map.entry("character varying", "varchar"), Map.entry("bpchar", "char"),
        Map.entry("char", "char"), Map.entry("character", "char"),
        Map.entry("text", "longtext"), Map.entry("bool", "tinyint"), Map.entry("boolean", "tinyint"),
        Map.entry("numeric", "decimal"), Map.entry("money", "decimal"),
        Map.entry("float4", "float"), Map.entry("float8", "double"), Map.entry("real", "float"),
        Map.entry("double precision", "double"),
        Map.entry("timestamp", "datetime"), Map.entry("timestamptz", "datetime"),
        Map.entry("date", "date"), Map.entry("time", "time"), Map.entry("timetz", "time"),
        Map.entry("json", "json"), Map.entry("jsonb", "json"), Map.entry("xml", "longtext"),
        Map.entry("uuid", "varchar"), Map.entry("bytea", "longblob"),
        Map.entry("inet", "varchar"), Map.entry("cidr", "varchar"), Map.entry("macaddr", "varchar"),
        Map.entry("interval", "varchar"));

    private static final Map<String, String> PG_TO_ORACLE = Map.ofEntries(
        Map.entry("int2", "number"), Map.entry("int4", "number"), Map.entry("int8", "number"),
        Map.entry("oid", "number"), Map.entry("smallserial", "number"), Map.entry("serial2", "number"),
        Map.entry("serial", "number"), Map.entry("serial4", "number"), Map.entry("bigserial", "number"),
        Map.entry("serial8", "number"),
        Map.entry("varchar", "varchar2"), Map.entry("character varying", "varchar2"),
        Map.entry("bpchar", "char"), Map.entry("char", "char"), Map.entry("character", "char"),
        Map.entry("text", "clob"), Map.entry("bool", "number"), Map.entry("boolean", "number"),
        Map.entry("numeric", "number"), Map.entry("money", "number"),
        Map.entry("float4", "binary_float"), Map.entry("float8", "binary_double"),
        Map.entry("real", "binary_float"), Map.entry("double precision", "binary_double"),
        Map.entry("timestamp", "timestamp"), Map.entry("timestamptz", "timestamp"),
        Map.entry("date", "date"), Map.entry("time", "date"), Map.entry("timetz", "date"),
        Map.entry("json", "clob"), Map.entry("jsonb", "clob"), Map.entry("xml", "clob"),
        Map.entry("uuid", "varchar2"), Map.entry("bytea", "blob"),
        Map.entry("inet", "varchar2"), Map.entry("cidr", "varchar2"), Map.entry("macaddr", "varchar2"),
        Map.entry("interval", "varchar2"));

    private static final Map<String, String> ORACLE_TO_MYSQL = Map.ofEntries(
        Map.entry("varchar2", "varchar"), Map.entry("nvarchar2", "varchar"),
        Map.entry("char", "char"), Map.entry("nchar", "char"),
        Map.entry("number", "decimal"), Map.entry("integer", "int"), Map.entry("smallint", "smallint"),
        Map.entry("float", "double"),
        Map.entry("clob", "longtext"), Map.entry("nclob", "longtext"), Map.entry("long", "longtext"),
        Map.entry("date", "datetime"), Map.entry("timestamp", "datetime"),
        Map.entry("raw", "varbinary"), Map.entry("long raw", "longblob"),
        Map.entry("binary_float", "float"), Map.entry("binary_double", "double"),
        Map.entry("blob", "longblob"), Map.entry("bfile", "varchar"), Map.entry("xmltype", "longtext"));

    private static final Map<String, String> ORACLE_TO_PG = Map.ofEntries(
        Map.entry("varchar2", "varchar"), Map.entry("nvarchar2", "varchar"),
        Map.entry("char", "char"), Map.entry("nchar", "char"),
        Map.entry("number", "numeric"), Map.entry("integer", "integer"), Map.entry("smallint", "smallint"),
        Map.entry("float", "double precision"),
        Map.entry("clob", "text"), Map.entry("nclob", "text"), Map.entry("long", "text"),
        Map.entry("date", "timestamp"), Map.entry("timestamp", "timestamp"),
        Map.entry("raw", "bytea"), Map.entry("long raw", "bytea"),
        Map.entry("binary_float", "real"), Map.entry("binary_double", "double precision"),
        Map.entry("blob", "bytea"), Map.entry("bfile", "bytea"), Map.entry("xmltype", "xml"));

    /** 目标为 Oracle 时整数类型的默认精度（源类型 -> number(p)） */
    private static final Map<String, String> ORACLE_INT_PRECISION = Map.of(
        "tinyint", "3", "smallint", "5", "mediumint", "7", "int", "10", "integer", "10", "bigint", "19");

    /** 目标为 PG 时无符号整型升级 */
    private static final Map<String, String> PG_UNSIGNED_BUMP = Map.of(
        "tinyint", "smallint", "smallint", "integer", "mediumint", "integer",
        "int", "bigint", "integer", "bigint", "bigint", "numeric");

    /** 目标方言下"无约束大文本"类型 */
    private static String unconstrained(Dialect d) {
        switch (d) {
            case MYSQL:
                return "longtext";
            case ORACLE:
                return "clob";
            default:
                return "text";
        }
    }

    /**
     * 生成目标方言的列类型表达式；无法解析返回 null。
     *
     * @param forCreate 建表场景（与改列在护栏上无差别，预留）
     */
    private static String typeExpr(ColumnModel c, Ctx ctx, boolean forCreate) {
        String raw = firstNonEmpty(c.getColumnType(), c.getTypeName());
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = raw.trim();

        // 同方言 MySQL：COLUMN_TYPE 即权威类型，原样返回
        if (!ctx.cross && ctx.tgtD == Dialect.MYSQL) {
            return s;
        }

        String base;
        String argStr = null;
        int p = s.indexOf('(');
        int last = s.lastIndexOf(')');
        if (p >= 0 && last > p) {
            base = s.substring(0, p).trim();
            argStr = s.substring(p + 1, last).trim();
        } else {
            base = s.trim();
        }
        boolean unsigned = false;
        String lowerBase = base.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String suffix : new String[] {" unsigned", " zerofill"}) {
            if (lowerBase.endsWith(suffix)) {
                if (suffix.contains("unsigned")) {
                    unsigned = true;
                }
                lowerBase = lowerBase.substring(0, lowerBase.length() - suffix.length()).trim();
            }
        }
        int digits = parseIntSafe(c.getDecimalDigits());

        String mapped;
        if (ctx.cross) {
            mapped = mapType(lowerBase, argStr, unsigned, digits, ctx);
        } else {
            mapped = lowerBase;
        }
        if (mapped == null) {
            return null;
        }
        String mappedUpper = mapped.toUpperCase(Locale.ROOT);

        // Oracle 源的 NUMBER(n) 参数来自 DATA_LENGTH（字节数），不可信 → 无约束
        if (ctx.srcD == Dialect.ORACLE && "number".equals(lowerBase)) {
            ctx.oracleNumberLoose = true;
            return mapped;
        }

        // 按目标方言护栏重建参数
        if (DATETIME_TYPES.contains(mappedUpper)) {
            return digits > 0 ? mapped + "(" + digits + ")" : mapped;
        }
        if (NO_LENGTH_TYPES.contains(mappedUpper)) {
            return mapped;
        }
        if (NUMERIC_NO_WIDTH_TYPES.contains(mappedUpper)) {
            return mapped;
        }
        if ("NUMBER".equals(mappedUpper) || "NUMERIC".equals(mappedUpper) || "DECIMAL".equals(mappedUpper)) {
            // 精度/标度：参数优先（PG numeric(10) 丢失标度时用 decimalDigits 补齐）
            String prec;
            String scale = null;
            if (argStr != null && !argStr.isEmpty()) {
                int comma = argStr.indexOf(',');
                if (comma > 0) {
                    prec = argStr.substring(0, comma).trim();
                    scale = argStr.substring(comma + 1).trim();
                } else {
                    prec = argStr;
                    scale = digits > 0 ? String.valueOf(digits) : null;
                }
            } else {
                prec = c.getColumnLength();
                scale = digits > 0 ? String.valueOf(digits) : null;
            }
            long pv = parseLongSafe(prec);
            int sv = parseIntSafe(scale);
            if (pv <= 0 || pv >= UNCONSTRAINED_THRESHOLD) {
                return mapped;
            }
            return sv > 0 ? mapped + "(" + pv + "," + sv + ")" : mapped + "(" + pv + ")";
        }
        // 字符串族（带长度）；无参数且目标不允许裸 varchar 时转文本类型
        long len = parseLongSafe(argStr != null && !argStr.isEmpty() ? argStr : c.getColumnLength());
        if (len >= UNCONSTRAINED_THRESHOLD) {
            warnOnce(ctx, mapped, "超长/无约束 " + mapped + " 以 " + unconstrained(ctx.tgtD) + " 生成");
            return unconstrained(ctx.tgtD);
        }
        if (len > 0) {
            return mapped + "(" + len + ")";
        }
        if (ctx.tgtD == Dialect.MYSQL || ctx.tgtD == Dialect.ORACLE) {
            // MySQL varchar / Oracle varchar2 不允许无长度
            warnOnce(ctx, mapped, "无长度 " + mapped + " 以 " + unconstrained(ctx.tgtD) + " 生成");
            return unconstrained(ctx.tgtD);
        }
        return mapped;
    }

    private static String mapType(String base, String argStr, boolean unsigned, int digits, Ctx ctx) {
        Map<String, String> map;
        if (ctx.srcD == Dialect.MYSQL) {
            map = ctx.tgtD == Dialect.PG ? MYSQL_TO_PG : MYSQL_TO_ORACLE;
        } else if (ctx.srcD == Dialect.PG) {
            map = ctx.tgtD == Dialect.MYSQL ? PG_TO_MYSQL : PG_TO_ORACLE;
        } else {
            map = ctx.tgtD == Dialect.MYSQL ? ORACLE_TO_MYSQL : ORACLE_TO_PG;
        }
        String mapped = map.get(base);
        if (mapped == null) {
            if (!ctx.warnedTypes.contains(base)) {
                ctx.warnedTypes.add(base);
                ctx.warnings.add("类型 " + base + " 未内置映射，按原样生成，请人工复核");
            }
            mapped = base;
        }
        // 无符号整型升级
        if (unsigned && ctx.tgtD == Dialect.PG && PG_UNSIGNED_BUMP.containsKey(base)) {
            mapped = PG_UNSIGNED_BUMP.get(base);
        }
        // 目标 Oracle：整数补默认精度
        if (ctx.tgtD == Dialect.ORACLE && "number".equals(mapped)
            && (argStr == null || argStr.isEmpty())) {
            String prec = ORACLE_INT_PRECISION.get(base);
            if (prec == null && unsigned) {
                prec = "bigint".equals(base) ? "20"
                    : ("int".equals(base) || "integer".equals(base)) ? "10" : null;
            }
            if (prec != null) {
                mapped = "number(" + prec + ")";
            }
        }
        // enum/set 跨方言：映射为 varchar 族，不保留取值列表
        if (("enum".equals(base) || "set".equals(base))) {
            if (ctx.tgtD == Dialect.ORACLE) {
                mapped = "varchar2(255)";
            } else if (ctx.tgtD == Dialect.PG) {
                mapped = "varchar";
            }
            warnOnce(ctx, "enum", "enum/set 类型跨方言以 varchar 生成，取值约束丢失");
        }
        // 布尔默认值迁移
        return mapped;
    }

    /** 默认值表达式：跨方言清洗 PG 的 ::cast 后缀、布尔字面量 */
    private static String defaultExpr(ColumnModel c, Ctx ctx) {
        String def = nvl(c.getColumnDef()).trim();
        if (def.isEmpty()) {
            return "";
        }
        if (!ctx.cross) {
            return def;
        }
        // 去掉 PG ::character varying 之类 cast
        def = def.replaceAll("::[a-zA-Z_][a-zA-Z0-9_\\s]*(\\(\\d+(\\s*,\\s*\\d+)?\\))?", "").trim();
        if (ctx.tgtD == Dialect.MYSQL) {
            if ("true".equalsIgnoreCase(def)) {
                return "1";
            }
            if ("false".equalsIgnoreCase(def)) {
                return "0";
            }
        }
        return def;
    }

    private static void warnOnce(Ctx ctx, String key, String msg) {
        if (ctx.warnedTypes.add(key)) {
            ctx.warnings.add(msg);
        }
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.trim().isEmpty() ? a : b;
    }

    private static int parseIntSafe(String s) {
        try {
            return s == null || s.trim().isEmpty() ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLongSafe(String s) {
        try {
            return s == null || s.trim().isEmpty() ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.core.process.ProgressListener;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DDL 生成服务
 *
 * <p>通过 JDBC DatabaseMetaData 获取数据库元数据，生成完整的 DDL 语句：</p>
 * <ol>
 *     <li>CREATE TABLE（含列定义、NOT NULL、DEFAULT）</li>
 *     <li>PRIMARY KEY</li>
 *     <li>INDEX / UNIQUE INDEX</li>
 *     <li>COMMENT ON TABLE / COMMENT ON COLUMN</li>
 *     <li>CREATE VIEW（视图定义）</li>
 *     <li>CREATE FUNCTION / PROCEDURE（MySQL 和 PostgreSQL 方言）</li>
 * </ol>
 *
 * @author screw-web
 */
@Slf4j
public class DdlService {

    private final DataSource dataSource;
    private final String databaseName;
    private List<String> tableFilter;
    private ProgressListener progressListener;

    public DdlService(DataSource dataSource, String databaseName) {
        this.dataSource = dataSource;
        this.databaseName = databaseName;
    }

    public void setTableFilter(List<String> tableFilter) {
        this.tableFilter = tableFilter;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * 生成 DDL 文件
     */
    public void generate(File outputFile) throws Exception {
        try (Connection conn = dataSource.getConnection();
             BufferedWriter writer = new BufferedWriter(
                 new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {

            DatabaseMetaData meta = conn.getMetaData();
            String dbProduct = meta.getDatabaseProductName();
            // 方言识别：MySQL（注释内联）/ Oracle（无 IF EXISTS、ALL_VIEWS 取视图）/ 其余按 PG 系处理（PostgreSQL、瀚高 HighGo、金仓 Kingbase 等）
            String product = dbProduct == null ? "" : dbProduct.toLowerCase(Locale.ROOT);
            boolean isMysql = product.contains("mysql");
            boolean isOracle = product.contains("oracle");
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            // 写入头部
            writeHeader(writer, dbProduct, meta.getDatabaseProductVersion());

            // 获取所有表和视图
            List<TableInfo> tables = getTables(meta, catalog, schema);
            List<ViewInfo> views = getViews(meta, catalog, schema);

            int totalObjects = tables.size() + views.size();
            int current = 0;

            // 1. 生成表 DDL（CREATE TABLE + PK + INDEX + COMMENT）
            for (TableInfo table : tables) {
                current++;
                if (progressListener != null) {
                    progressListener.onProgress(current, totalObjects, table.tableName);
                }
                writeTableDdl(writer, conn, meta, catalog, schema, table, isMysql, isOracle);
            }

            // 2. 生成视图 DDL
            for (ViewInfo view : views) {
                current++;
                if (progressListener != null) {
                    progressListener.onProgress(current, totalObjects, view.tableName);
                }
                writeViewDdl(writer, conn, catalog, schema, view, isMysql, isOracle);
            }

            // 3. 生成函数/存储过程 DDL（按数据库方言）
            writeFunctionsDdl(writer, conn, dbProduct, schema);

            writer.flush();
        }
    }

    // ======================== 表 DDL ========================

    private void writeTableDdl(BufferedWriter writer, Connection conn,
                               DatabaseMetaData meta, String catalog, String schema,
                               TableInfo table, boolean isMysql, boolean isOracle) throws Exception {
        writer.write("\n-- ------------------------------------------------------------");
        writer.write("\n-- 表: " + table.tableName);
        if (table.remarks != null && !table.remarks.isEmpty()) {
            writer.write("\n-- 备注: " + table.remarks);
        }
        writer.write("\n-- ------------------------------------------------------------\n");
        // Oracle 无 DROP TABLE IF EXISTS 语法，退化为普通 DROP
        writer.write(isOracle
            ? "DROP TABLE " + table.tableName + ";\n"
            : "DROP TABLE IF EXISTS " + table.tableName + ";\n");
        writer.write("CREATE TABLE " + table.tableName + " (\n");

        // 列定义
        List<ColumnInfo> columns = getColumns(meta, catalog, schema, table.tableName);
        List<String> primaryKeys = getPrimaryKeys(meta, catalog, schema, table.tableName);

        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            // 列间逗号前缀式：前一行不写尾逗号，避免最后一列与 PRIMARY KEY 之间漏逗号
            if (i > 0) {
                writer.write(",\n");
            }
            writer.write("    " + col.columnName + " " + col.columnType);
            if ("NO".equalsIgnoreCase(col.isNullable)) {
                writer.write(" NOT NULL");
            }
            if (col.columnDefault != null && !col.columnDefault.isEmpty()) {
                writer.write(" DEFAULT " + col.columnDefault);
            }
            // AUTO_INCREMENT 仅 MySQL 语法；PG 系 serial 已由 DEFAULT nextval(...) 表达，Oracle 用 IDENTITY/序列
            if (isMysql && col.autoIncrement != null && "YES".equalsIgnoreCase(col.autoIncrement)) {
                writer.write(" AUTO_INCREMENT");
            }
            // MySQL 不支持 COMMENT ON，列注释内联在 CREATE TABLE 中
            if (isMysql && col.remarks != null && !col.remarks.isEmpty()) {
                writer.write(" COMMENT '" + escapeSql(col.remarks) + "'");
            }
        }

        // 主键约束（补上与最后一列之间的逗号，保证语法合法）
        if (!primaryKeys.isEmpty()) {
            writer.write(",\n    PRIMARY KEY (" + String.join(", ", primaryKeys) + ")");
        }

        // MySQL：表注释内联在 CREATE TABLE 结尾；其余方言用 COMMENT ON
        if (isMysql && table.remarks != null && !table.remarks.isEmpty()) {
            writer.write("\n) COMMENT='" + escapeSql(table.remarks) + "';\n");
        } else {
            writer.write("\n);\n");
        }

        // 索引（跳过与主键列完全相同的索引，避免 PG/Oracle 主键索引重复创建）
        writeIndexes(writer, meta, catalog, schema, table.tableName, new HashSet<>(primaryKeys));

        if (!isMysql) {
            // 表注释（MySQL 已内联）
            if (table.remarks != null && !table.remarks.isEmpty()) {
                writer.write("COMMENT ON TABLE " + table.tableName
                    + " IS '" + escapeSql(table.remarks) + "';\n");
            }

            // 列注释
            for (ColumnInfo col : columns) {
                if (col.remarks != null && !col.remarks.isEmpty()) {
                    writer.write("COMMENT ON COLUMN " + table.tableName + "." + col.columnName
                        + " IS '" + escapeSql(col.remarks) + "';\n");
                }
            }
        }
    }

    // ======================== 视图 DDL ========================

    private void writeViewDdl(BufferedWriter writer, Connection conn,
                              String catalog, String schema, ViewInfo view,
                              boolean isMysql, boolean isOracle) throws Exception {
        writer.write("\n-- ------------------------------------------------------------");
        writer.write("\n-- 视图: " + view.tableName);
        if (view.remarks != null && !view.remarks.isEmpty()) {
            writer.write("\n-- 备注: " + view.remarks);
        }
        writer.write("\n-- ------------------------------------------------------------\n");
        // Oracle 无 DROP VIEW IF EXISTS 语法，退化为普通 DROP
        writer.write(isOracle
            ? "DROP VIEW " + view.tableName + ";\n"
            : "DROP VIEW IF EXISTS " + view.tableName + ";\n");

        // 尝试获取视图定义（MySQL 用 SHOW CREATE VIEW；Oracle 优先 GET_DDL 回退 ALL_VIEWS.TEXT；PG/瀚高用 information_schema）
        String viewDef = getViewDefinition(conn, catalog, schema, view.tableName, isMysql, isOracle);
        if (viewDef != null && !viewDef.isEmpty()) {
            if (isMysql) {
                // SHOW CREATE VIEW 返回完整语句，去掉 DEFINER 避免目标库权限问题
                writer.write(viewDef.replaceAll("(?i)DEFINER=`[^`]+`@`[^`]+`\\s+", ""));
            } else {
                String trimmed = viewDef.trim();
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("CREATE")) {
                    // Oracle GET_DDL 返回完整 CREATE VIEW 语句，直接回放
                    writer.write(trimmed);
                } else {
                    // PG/瀚高返回的是 SELECT 查询体，需补 CREATE VIEW 头
                    writer.write("CREATE OR REPLACE VIEW " + view.tableName + " AS\n");
                    writer.write(trimmed);
                }
            }
            if (!viewDef.trim().endsWith(";")) {
                writer.write(";");
            }
            writer.write("\n");
        } else {
            // 回退：生成简单的 CREATE OR REPLACE VIEW ... AS SELECT * 占位
            writer.write("-- 视图定义无法自动获取，请手动补充\n");
            writer.write("CREATE OR REPLACE VIEW " + view.tableName + " AS\n");
            writer.write("SELECT * FROM " + view.tableName + "_base; -- TODO: replace with actual definition\n");
        }
    }

    // ======================== 索引 DDL ========================

    private void writeIndexes(BufferedWriter writer, DatabaseMetaData meta,
                              String catalog, String schema, String tableName,
                              Set<String> primaryKeyCols) throws Exception {
        try (ResultSet rs = meta.getIndexInfo(catalog, schema, tableName, false, false)) {
            // 按索引名分组
            Map<String, List<IndexColInfo>> indexMap = new LinkedHashMap<>();
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null || indexName.equalsIgnoreCase("PRIMARY")) {
                    continue; // 跳过主键索引（MySQL 约定名，已在 CREATE TABLE 中定义）
                }
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName == null) {
                    continue; // 统计信息行无列名
                }
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                IndexColInfo info = new IndexColInfo();
                info.indexName = indexName;
                info.columnName = columnName;
                info.nonUnique = nonUnique;
                info.ordinal = rs.getShort("ORDINAL_POSITION");
                indexMap.computeIfAbsent(indexName, k -> new ArrayList<>()).add(info);
            }
            // 主键列集合（小写，用于比较）
            Set<String> pkCols = primaryKeyCols == null ? Collections.emptySet()
                : primaryKeyCols.stream().map(String::toLowerCase).collect(Collectors.toSet());
            for (Map.Entry<String, List<IndexColInfo>> entry : indexMap.entrySet()) {
                List<IndexColInfo> cols = entry.getValue();
                cols.sort(Comparator.comparingInt(c -> c.ordinal));
                // PG/Oracle 主键索引有真实索引名（如 t_pkey），列集合同主键时跳过以免重复创建
                if (cols.size() == pkCols.size() && cols.stream()
                    .allMatch(c -> pkCols.contains(c.columnName.toLowerCase()))) {
                    continue;
                }
                boolean nonUnique = cols.get(0).nonUnique;
                String colList = cols.stream()
                    .map(c -> c.columnName)
                    .collect(Collectors.joining(", "));
                writer.write("CREATE " + (nonUnique ? "" : "UNIQUE ") + "INDEX "
                    + entry.getKey() + " ON " + tableName + " (" + colList + ");\n");
            }
        } catch (SQLException e) {
            log.debug("获取索引信息失败: {}", e.getMessage());
        }
    }

    // ======================== 函数/存储过程 DDL ========================

    private void writeFunctionsDdl(BufferedWriter writer, Connection conn,
                                    String dbProduct, String schema) throws Exception {
        writer.write("\n-- ============================================================");
        writer.write("\n-- 函数 / 存储过程");
        writer.write("\n-- ============================================================\n");

        try {
            if (dbProduct.toLowerCase().contains("mysql")) {
                writeMySQLFunctions(writer, conn, schema);
            } else if (dbProduct.toLowerCase().contains("postgresql")
                       || dbProduct.toLowerCase().contains("highgo")
                       || dbProduct.toLowerCase().contains("kingbase")) {
                writePostgresFunctions(writer, conn, schema);
            } else if (dbProduct.toLowerCase().contains("oracle")) {
                writeOracleFunctions(writer, conn, schema);
            } else {
                writer.write("-- 当前数据库类型 (" + dbProduct + ") 的函数导出暂不支持\n");
            }
        } catch (Exception e) {
            log.debug("获取函数定义失败: {}", e.getMessage());
            writer.write("-- 获取函数定义失败: " + e.getMessage() + "\n");
        }
    }

    private void writeMySQLFunctions(BufferedWriter writer, Connection conn,
                                      String schema) throws Exception {
        // 注意：INFORMATION_SCHEMA.ROUTINES.ROUTINE_DEFINITION 只有函数体（BEGIN...END），
        // 不含 CREATE 签名头，直接输出为非法 SQL；须用 SHOW CREATE FUNCTION/PROCEDURE 取完整定义
        String sql = "SELECT ROUTINE_NAME, ROUTINE_TYPE "
            + "FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conn.getCatalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("ROUTINE_NAME");
                    String type = rs.getString("ROUTINE_TYPE");
                    boolean isProc = "PROCEDURE".equalsIgnoreCase(type);
                    try (Statement st = conn.createStatement();
                         ResultSet rs2 = st.executeQuery(
                             "SHOW CREATE " + (isProc ? "PROCEDURE" : "FUNCTION")
                                 + " `" + name.replace("`", "") + "`")) {
                        if (rs2.next()) {
                            String def = isProc
                                ? rs2.getString("Create Procedure")
                                : rs2.getString("Create Function");
                            if (def != null && !def.isEmpty()) {
                                writer.write("\n-- " + type + ": " + name + "\n");
                                writer.write("DROP " + (isProc ? "PROCEDURE" : "FUNCTION")
                                    + " IF EXISTS `" + name.replace("`", "") + "`;\n");
                                // 去 DEFINER，避免目标库权限问题
                                writer.write(def.replaceAll("(?i)DEFINER=`[^`]+`@`[^`]+`\\s+", ""));
                                if (!def.trim().endsWith(";")) {
                                    writer.write(";");
                                }
                                writer.write("\n");
                            }
                        }
                    } catch (SQLException e) {
                        log.debug("SHOW CREATE {} 失败 ({}): {}", type, name, e.getMessage());
                        writer.write("\n-- " + type + ": " + name + "（定义获取失败，请手动导出）\n");
                    }
                }
            }
        }
    }

    private void writePostgresFunctions(BufferedWriter writer, Connection conn,
                                          String schema) throws Exception {
        // 优先 pg_get_functiondef：返回完整可回放的 CREATE OR REPLACE FUNCTION
        // （information_schema.routines.routine_definition 只有函数体，拼不出完整 DDL）
        // 覆盖所有用户 schema（排除系统模式、扩展依赖函数、聚合函数），避免函数建在非 public 模式时漏导
        try {
            String sql = "SELECT n.nspname AS schema, p.proname AS name, "
                + "CASE WHEN p.prokind = 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END AS type, "
                + "pg_get_functiondef(p.oid) AS def "
                + "FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid "
                + "WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') "
                + "AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e') "
                + "AND NOT EXISTS (SELECT 1 FROM pg_aggregate a WHERE a.aggfnoid = p.oid) "
                + "ORDER BY n.nspname, p.proname";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String schemaOf = rs.getString("schema");
                        String name = rs.getString("name");
                        String type = rs.getString("type");
                        String def = rs.getString("def");
                        if (def != null && !def.isEmpty()) {
                            writer.write("\n-- " + type + ": " + name + "（schema: " + schemaOf + "）\n");
                            // 返回的是 CREATE OR REPLACE，幂等无需 DROP
                            writer.write(def.trim().endsWith(";") ? def : def + ";");
                            writer.write("\n");
                        }
                    }
                }
            }
            return;
        } catch (SQLException e) {
            log.debug("pg_get_functiondef 不可用（低版本兼容），跳过函数导出: {}", e.getMessage());
            writer.write("-- 函数定义获取不可用（pg_get_functiondef 不可用），请手动导出\n");
        }
    }

    private void writeOracleFunctions(BufferedWriter writer, Connection conn,
                                        String schema) throws Exception {
        String owner = schema != null ? schema.toUpperCase(Locale.ROOT)
            : conn.getMetaData().getUserName();
        // 用 DBMS_METADATA.GET_DDL 取完整定义（CREATE OR REPLACE，可回放、无需 DROP）
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT OBJECT_NAME, OBJECT_TYPE FROM ALL_OBJECTS "
                 + "WHERE OWNER = ? AND OBJECT_TYPE IN ('FUNCTION', 'PROCEDURE') "
                 + "AND OBJECT_NAME NOT LIKE 'BIN$%' ORDER BY OBJECT_NAME");
             PreparedStatement getDdl = conn.prepareStatement(
                 "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL")) {
            ps.setString(1, owner);
            getDdl.setString(3, owner);
            // 先收集对象清单并关闭列表游标，再逐个 GET_DDL：
            // GET_DDL 返回 CLOB，与另一条未读完的游标交叉执行存在流失效风险
            List<String[]> objects = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objects.add(new String[]{rs.getString("OBJECT_TYPE"),
                        rs.getString("OBJECT_NAME")});
                }
            }
            for (String[] obj : objects) {
                String type = obj[0];
                String name = obj[1];
                writer.write("\n-- " + type + ": " + name + "\n");
                boolean ok = false;
                try {
                    getDdl.setString(1, type);
                    getDdl.setString(2, name);
                    try (ResultSet rs2 = getDdl.executeQuery()) {
                        if (rs2.next()) {
                            String def = rs2.getString(1);
                            if (def != null && !def.isEmpty()) {
                                writer.write(def.trim());
                                if (!def.trim().endsWith(";")) {
                                    writer.write(";");
                                }
                                writer.write("\n");
                                ok = true;
                            }
                        }
                    }
                } catch (SQLException e) {
                    log.debug("GET_DDL 失败 ({} {}): {}", type, name, e.getMessage());
                }
                if (!ok) {
                    writer.write("-- 定义获取失败，需通过 DBMS_METADATA.GET_DDL 手动导出\n");
                }
            }
        }
    }

    // ======================== 元数据获取 ========================

    private List<TableInfo> getTables(DatabaseMetaData meta, String catalog, String schema) throws SQLException {
        List<TableInfo> result = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (isFiltered(tableName)) {
                    TableInfo t = new TableInfo();
                    t.tableName = tableName;
                    t.remarks = rs.getString("REMARKS");
                    result.add(t);
                }
            }
        }
        return result;
    }

    private List<ViewInfo> getViews(DatabaseMetaData meta, String catalog, String schema) throws SQLException {
        List<ViewInfo> result = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"VIEW"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (isFiltered(tableName)) {
                    ViewInfo v = new ViewInfo();
                    v.tableName = tableName;
                    v.remarks = rs.getString("REMARKS");
                    result.add(v);
                }
            }
        }
        return result;
    }

    /**
     * 不允许拼接长度的类型（拼 "(n)" 会产生非法 DDL，如 MySQL 的 TEXT(65535)、PG 的 text(2147483647)）
     */
    private static final Set<String> NO_LENGTH_TYPES = new HashSet<>(Arrays.asList(
        "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
        "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
        "JSON", "JSONB", "CLOB", "NCLOB", "BOOL", "BOOLEAN",
        "GEOMETRY", "POINT", "LINESTRING", "POLYGON",
        "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION",
        "BYTEA", "UUID", "INET", "CIDR", "MACADDR", "MONEY", "XML",
        "TSVECTOR", "TSQUERY"));
    /**
     * 日期时间类型：仅当小数秒精度（DECIMAL_DIGITS）> 0 时拼 (digits)，不拼长度
     */
    private static final Set<String> DATETIME_TYPES = new HashSet<>(Arrays.asList(
        "DATETIME", "TIMESTAMP", "TIME", "DATE", "DATETIME2", "TIMESTAMPTZ", "TIMETZ"));
    /**
     * 整数/浮点类型：不拼显示宽度（避免 INT(10)、BIGINT(19) 之类冗余）
     * 含 PostgreSQL/瀚高 JDBC 返回的内部类型名（拼出 int4(10) 之类即非法）
     */
    private static final Set<String> NUMERIC_NO_WIDTH_TYPES = new HashSet<>(Arrays.asList(
        "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
        "INT2", "INT4", "INT8", "OID",
        "SERIAL2", "SERIAL4", "SERIAL8", "SMALLSERIAL", "BIGSERIAL",
        "FLOAT", "DOUBLE", "REAL", "SERIAL"));
    /**
     * 列长超过该值视为无约束（PG/瀚高 unconstrained varchar=2147483647、
     * unconstrained numeric=131089 等），拼长度会生成非法 DDL
     */
    private static final int UNCONSTRAINED_SIZE_THRESHOLD = 100000;

    private List<ColumnInfo> getColumns(DatabaseMetaData meta, String catalog,
                                         String schema, String tableName) throws SQLException {
        List<ColumnInfo> result = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                ColumnInfo col = new ColumnInfo();
                col.columnName = rs.getString("COLUMN_NAME");
                String baseType = rs.getString("TYPE_NAME");
                col.columnType = baseType;
                int columnSize = rs.getInt("COLUMN_SIZE");
                int decimalDigits = rs.getInt("DECIMAL_DIGITS");
                String upper = baseType == null ? "" : baseType.toUpperCase(Locale.ROOT);
                if (DATETIME_TYPES.contains(upper)) {
                    // DATETIME(fsp)：仅小数秒精度 > 0 时拼，如 DATETIME(3)
                    if (decimalDigits > 0) {
                        col.columnType += "(" + decimalDigits + ")";
                    }
                } else if (columnSize > 0 && columnSize < UNCONSTRAINED_SIZE_THRESHOLD
                    && !NO_LENGTH_TYPES.contains(upper)
                    && !NUMERIC_NO_WIDTH_TYPES.contains(upper)) {
                    col.columnType += "(" + columnSize;
                    if (decimalDigits > 0) {
                        col.columnType += "," + decimalDigits;
                    }
                    col.columnType += ")";
                }
                col.isNullable = rs.getString("IS_NULLABLE");
                col.remarks = rs.getString("REMARKS");
                col.autoIncrement = rs.getString("IS_AUTOINCREMENT");
                // Oracle：COLUMN_DEF 映射 ALL_TAB_COLS.DATA_DEFAULT（LONG 流式列）。
                // 流式列必须在行内最后读取，先读它再取同行其它列会抛 ORA-17027（流已被关闭）
                col.columnDefault = rs.getString("COLUMN_DEF");
                result.add(col);
            }
        }
        return result;
    }

    private List<String> getPrimaryKeys(DatabaseMetaData meta, String catalog,
                                         String schema, String tableName) throws SQLException {
        // 按 KEY_SEQ 排序，保证复合主键列顺序正确
        TreeMap<Short, String> keyed = new TreeMap<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                keyed.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return new ArrayList<>(keyed.values());
    }

    private String getViewDefinition(Connection conn, String catalog,
                                      String schema, String viewName, boolean isMysql, boolean isOracle) {
        // MySQL：SHOW CREATE VIEW 返回完整可回放的定义
        if (isMysql) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SHOW CREATE VIEW `" + viewName + "`")) {
                if (rs.next()) {
                    String ddl = rs.getString(2);
                    if (ddl != null && !ddl.isEmpty()) {
                        return ddl;
                    }
                }
            } catch (SQLException e) {
                log.debug("获取视图定义失败 (SHOW CREATE VIEW): {}", e.getMessage());
            }
            return null;
        }
        // Oracle：优先 DBMS_METADATA.GET_DDL（CLOB 列，getString 行为稳定，
        // 返回完整可回放的 CREATE VIEW 语句）；ALL_VIEWS.TEXT 为 LONG 流式列
        //（ORA-17027 风险），仅作回退
        if (isOracle) {
            String owner;
            try {
                owner = schema != null ? schema.toUpperCase(Locale.ROOT)
                    : conn.getMetaData().getUserName();
            } catch (SQLException e) {
                log.debug("获取 owner 失败: {}", e.getMessage());
                return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DBMS_METADATA.GET_DDL('VIEW', ?, ?) FROM DUAL")) {
                ps.setString(1, viewName.toUpperCase(Locale.ROOT));
                ps.setString(2, owner);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String def = rs.getString(1);
                        if (def != null && !def.isEmpty()) {
                            return def.trim();
                        }
                    }
                }
            } catch (SQLException e) {
                log.debug("获取视图定义失败 (GET_DDL): {}", e.getMessage());
            }
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TEXT FROM ALL_VIEWS WHERE OWNER = ? AND VIEW_NAME = ?")) {
                ps.setString(1, owner);
                ps.setString(2, viewName.toUpperCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String def = rs.getString(1);
                        if (def != null && !def.isEmpty()) {
                            return def;
                        }
                    }
                }
            } catch (SQLException e) {
                log.debug("获取视图定义失败 (ALL_VIEWS): {}", e.getMessage());
            }
            return null;
        }
        // 尝试 PostgreSQL / HighGo 的 information_schema
        try {
            String sql = "SELECT view_definition FROM information_schema.views "
                + "WHERE table_schema = ? AND table_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema != null ? schema : "public");
                ps.setString(2, viewName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("view_definition");
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("获取视图定义失败 (information_schema): {}", e.getMessage());
        }
        return null;
    }

    // ======================== 工具方法 ========================

    private boolean isFiltered(String tableName) {
        if (tableFilter == null || tableFilter.isEmpty()) {
            return true;
        }
        return tableFilter.contains(tableName);
    }

    private void writeHeader(BufferedWriter writer, String dbProduct,
                              String dbVersion) throws Exception {
        writer.write("-- ============================================================\n");
        writer.write("-- 数据库: " + databaseName + "\n");
        writer.write("-- 数据库类型: " + dbProduct + " " + dbVersion + "\n");
        writer.write("-- 生成工具: screw-web DdlService\n");
        writer.write("-- 生成时间: " + new java.util.Date() + "\n");
        writer.write("-- ============================================================\n");
    }

    private String escapeSql(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("'", "''");
    }

    // ======================== 内部数据类 ========================

    private static class TableInfo {
        String tableName;
        String remarks;
    }

    private static class ViewInfo {
        String tableName;
        String remarks;
    }

    private static class ColumnInfo {
        String columnName;
        String columnType;
        String isNullable;
        String columnDefault;
        String remarks;
        String autoIncrement;
    }

    private static class IndexColInfo {
        String indexName;
        String columnName;
        boolean nonUnique;
        int ordinal;
    }
}

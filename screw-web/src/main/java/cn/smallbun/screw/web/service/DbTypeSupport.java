/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.web.entity.DbConnection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库类型支撑：驱动、默认端口、JDBC URL 构建、前端选项
 */
public final class DbTypeSupport {

    private DbTypeSupport() {
    }

    /**
     * 数据库类型信息
     */
    public record DbTypeInfo(String code, String name, String driver, int defaultPort,
                             boolean schemaSupported) {
    }

    private static final Map<String, DbTypeInfo> TYPES = new LinkedHashMap<>();

    static {
        put("MYSQL", "MySQL", "com.mysql.cj.jdbc.Driver", 3306, false);
        put("MARIADB", "MariaDB", "org.mariadb.jdbc.Driver", 3306, false);
        // 国产化/云原生数据库：均兼容 MySQL 协议，复用 MySQL 驱动与元数据查询
        put("DORIS", "Doris", "com.mysql.cj.jdbc.Driver", 9030, false);
        put("TIDB", "TiDB", "com.mysql.cj.jdbc.Driver", 4000, false);
        put("OCEANBASE", "OceanBase", "com.mysql.cj.jdbc.Driver", 2881, false);
        put("ORACLE", "Oracle", "oracle.jdbc.driver.OracleDriver", 1521, false);
        put("SQL_SERVER", "SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433,
            false);
        put("POSTGRE_SQL", "PostgreSQL", "org.postgresql.Driver", 5432, true);
        put("HIGHGO", "瀚高 HighGo", "com.highgo.jdbc.Driver", 5866, true);
        put("KINGBASE_ES", "人大金仓 KingbaseES", "com.kingbase8.Driver", 54321, true);
        put("DM", "达梦 DM", "dm.jdbc.driver.DmDriver", 5236, false);
        put("XU_GU", "虚谷 Xugu", "com.xugudb.jdbc.Driver", 5138, true);
        put("DB2", "DB2", "com.ibm.db2.jcc.DB2Driver", 50000, false);
        put("H2", "H2", "org.h2.Driver", 0, false);
        put("HSQL", "HSQLDB", "org.hsqldb.jdbc.JDBCDriver", 9001, false);
        put("SQLITE", "SQLite", "org.sqlite.JDBC", 0, false);
    }

    private static void put(String code, String name, String driver, int port, boolean schema) {
        TYPES.put(code, new DbTypeInfo(code, name, driver, port, schema));
    }

    /**
     * 全部支持类型（用于前端下拉）
     */
    public static List<DbTypeInfo> all() {
        return new ArrayList<>(TYPES.values());
    }

    /**
     * 根据类型获取信息
     */
    public static DbTypeInfo of(String code) {
        DbTypeInfo info = TYPES.get(code);
        if (info == null) {
            throw new IllegalArgumentException("不支持的数据库类型: " + code);
        }
        return info;
    }

    /**
     * 获取驱动类名
     */
    public static String driver(String code) {
        return of(code).driver();
    }

    /**
     * 构建 JDBC URL
     */
    public static String buildUrl(DbConnection conn) {
        String code = conn.getDbType();
        String host = nvl(conn.getHost());
        int port = conn.getPort() == null ? of(code).defaultPort() : conn.getPort();
        String db = nvl(conn.getDatabase());
        String schema = nvl(conn.getSchemaName());
        switch (code) {
            case "MYSQL":
            case "DORIS":
            case "TIDB":
            case "OCEANBASE":
                // 必须带 useInformationSchema=true，screw-core 的 MySqlDataBaseQuery 依赖
                // INFORMATION_SCHEMA 元数据（否则走 SHOW 路径，表列表可能为空）
                return "jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                    + "&useInformationSchema=true"
                    + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
            case "MARIADB":
                return "jdbc:mariadb://" + host + ":" + port + "/" + db;
            case "ORACLE":
                return "jdbc:oracle:thin:@" + host + ":" + port + ":" + db;
            case "SQL_SERVER":
                return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db;
            case "POSTGRE_SQL":
                return "jdbc:postgresql://" + host + ":" + port + "/" + db
                    + (schema.isEmpty() ? "" : "?currentSchema=" + schema);
            case "HIGHGO":
                return "jdbc:highgo://" + host + ":" + port + "/" + db
                    + (schema.isEmpty() ? "" : "?currentSchema=" + schema);
            case "KINGBASE_ES":
                return "jdbc:kingbase8://" + host + ":" + port + "/" + db
                    + (schema.isEmpty() ? "" : "?currentSchema=" + schema);
            case "DM":
                return "jdbc:dm://" + host + ":" + port + "/" + db;
            case "XU_GU":
                return "jdbc:xugu://" + host + ":" + port + "/" + db
                    + (schema.isEmpty() ? "" : "?schema=" + schema);
            case "DB2":
                return "jdbc:db2://" + host + ":" + port + "/" + db;
            case "HSQL":
                return "jdbc:hsqldb:hsql://" + host + ":" + port + "/" + db;
            case "H2":
                // db 为数据库文件路径/名称
                return db.startsWith("jdbc:") ? db : "jdbc:h2:file:./data/" + db + ";AUTO_SERVER=TRUE";
            case "SQLITE":
                return "jdbc:sqlite:" + db;
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + code);
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}

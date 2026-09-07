/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.web.common.BizException;
import cn.smallbun.screw.web.dto.TableRelation;
import cn.smallbun.screw.web.entity.DbConnection;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 外键关系采集服务
 * <p>
 * screw-core 元数据模型无外键，此处自建采集：MySQL 协议系 / PostgreSQL 兼容系 / Oracle
 * 各用一条 information_schema 或字典视图 SQL（千表库零循环开销）；
 * 其余类型退回 JDBC 标准 {@code DatabaseMetaData.getImportedKeys}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationService {

    private final ConnectionService connectionService;

    /** MySQL 协议系：MYSQL / MARIADB / DORIS / TIDB / OCEANBASE。
     *  注意：MySQL 系 PK 约束名恒为 PRIMARY（不具 schema 唯一性），
     *  父侧 JOIN 必须限定 pkcu.TABLE_NAME = rc.REFERENCED_TABLE_NAME 才不会交叉匹配。 */
    private static final String MY_SQL_FK_SQL = """
        SELECT rc.CONSTRAINT_NAME,
               kcu.TABLE_NAME,
               kcu.COLUMN_NAME,
               rc.REFERENCED_TABLE_NAME,
               pkcu.COLUMN_NAME
          FROM information_schema.REFERENTIAL_CONSTRAINTS rc
          JOIN information_schema.KEY_COLUMN_USAGE kcu
            ON kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
           AND kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
          LEFT JOIN information_schema.KEY_COLUMN_USAGE pkcu
            ON pkcu.CONSTRAINT_SCHEMA = rc.UNIQUE_CONSTRAINT_SCHEMA
           AND pkcu.TABLE_NAME = rc.REFERENCED_TABLE_NAME
           AND pkcu.CONSTRAINT_NAME = rc.UNIQUE_CONSTRAINT_NAME
           AND pkcu.ORDINAL_POSITION = kcu.ORDINAL_POSITION
         WHERE rc.CONSTRAINT_SCHEMA = ?
         ORDER BY rc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
        """;

    /** PostgreSQL 兼容系（POSTGRE_SQL / HIGHGO / KINGBASE_ES / XU_GU）及 H2。
     *  标准 information_schema 三表连接；PK 约束名在 schema 内唯一（PG 靠唯一索引名），
     *  故父侧按 (CONSTRAINT_SCHEMA, CONSTRAINT_NAME, ORDINAL_POSITION) 定位即可。 */
    private static final String PG_FK_SQL = """
        SELECT rc.CONSTRAINT_NAME,
               fk.TABLE_NAME,
               fk.COLUMN_NAME,
               pk.TABLE_NAME,
               pk.COLUMN_NAME
          FROM information_schema.REFERENTIAL_CONSTRAINTS rc
          JOIN information_schema.KEY_COLUMN_USAGE fk
            ON fk.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
           AND fk.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
          LEFT JOIN information_schema.KEY_COLUMN_USAGE pk
            ON pk.CONSTRAINT_SCHEMA = rc.UNIQUE_CONSTRAINT_SCHEMA
           AND pk.CONSTRAINT_NAME = rc.UNIQUE_CONSTRAINT_NAME
           AND pk.ORDINAL_POSITION = fk.ORDINAL_POSITION
        """;

    /** Oracle：ALL_CONSTRAINTS + ALL_CONS_COLUMNS（仅启用状态外键）。
     *  列名依据 Oracle 字典：OWNER / R_OWNER / R_CONSTRAINT_NAME / POSITION。 */
    private static final String ORACLE_FK_SQL = """
        SELECT kc.CONSTRAINT_NAME,
               kc.TABLE_NAME,
               kcc.COLUMN_NAME,
               pc.TABLE_NAME,
               pcc.COLUMN_NAME
          FROM ALL_CONSTRAINTS kc
          JOIN ALL_CONSTRAINTS pc
            ON pc.OWNER = kc.R_OWNER
           AND pc.CONSTRAINT_NAME = kc.R_CONSTRAINT_NAME
          JOIN ALL_CONS_COLUMNS kcc
            ON kcc.OWNER = kc.OWNER
           AND kcc.CONSTRAINT_NAME = kc.CONSTRAINT_NAME
          JOIN ALL_CONS_COLUMNS pcc
            ON pcc.OWNER = pc.OWNER
           AND pcc.CONSTRAINT_NAME = pc.CONSTRAINT_NAME
           AND pcc.POSITION = kcc.POSITION
         WHERE kc.CONSTRAINT_TYPE = 'R'
           AND kc.STATUS <> 'DISABLED'
           AND kc.OWNER = ?
         ORDER BY kc.CONSTRAINT_NAME, kcc.POSITION
        """;

    /**
     * 采集指定连接的外键关系
     */
    public List<TableRelation> relations(Long connectionId) {
        DbConnection conn = connectionService.get(connectionId);
        try (HikariDataSource ds = connectionService.buildDataSource(conn);
             Connection c = ds.getConnection()) {
            return query(conn.getDbType(), conn.getDatabase(), conn.getSchemaName(),
                conn.getUsername(), c);
        } catch (SQLException e) {
            throw new BizException("外键关系获取失败: " + e.getMessage());
        }
    }

    private List<TableRelation> query(String dbType, String database, String schema, String username,
                                      Connection conn) throws SQLException {
        switch (dbType) {
            case "MYSQL":
            case "MARIADB":
            case "DORIS":
            case "TIDB":
            case "OCEANBASE":
                // MySQL 协议系：CONSTRAINT_SCHEMA 即数据库名
                return run(conn, MY_SQL_FK_SQL, List.of(database));
            case "POSTGRE_SQL":
            case "HIGHGO":
            case "KINGBASE_ES":
            case "XU_GU":
                // PostgreSQL 兼容系：CONSTRAINT_SCHEMA 为 schema；未指定则查全库
                if (StringUtils.hasText(schema)) {
                    return run(conn, PG_FK_SQL + " WHERE rc.CONSTRAINT_SCHEMA = ?\n"
                        + "ORDER BY rc.CONSTRAINT_NAME, fk.ORDINAL_POSITION", List.of(schema));
                }
                return run(conn, PG_FK_SQL + "\nORDER BY rc.CONSTRAINT_NAME, fk.ORDINAL_POSITION",
                    List.of());
            case "H2":
                // H2 2.x：information_schema 标准形态；无 schema 时默认 PUBLIC
                return run(conn, PG_FK_SQL + " WHERE rc.CONSTRAINT_SCHEMA = ?\n"
                    + "ORDER BY rc.CONSTRAINT_NAME, fk.ORDINAL_POSITION",
                    List.of(StringUtils.hasText(schema) ? schema : "PUBLIC"));
            case "ORACLE":
                return run(conn, ORACLE_FK_SQL, List.of(oracleSchema(schema, username)));
            default:
                // SQL Server / 达梦 / DB2 等：JDBC 标准元数据（每表一次查询）
                return jdbcMetadata(conn, dbType, schema, username);
        }
    }

    private List<TableRelation> run(Connection conn, String sql, List<Object> args)
            throws SQLException {
        List<TableRelation> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TableRelation(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)));
                }
            }
        }
        return out;
    }

    /** JDBC 标准回退路径 */
    private List<TableRelation> jdbcMetadata(Connection conn, String dbType, String schema,
                                             String username) throws SQLException {
        List<TableRelation> out = new ArrayList<>();
        String resolvedSchema = resolveJdbcSchema(dbType, schema, username);
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet tables = md.getTables(null, resolvedSchema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                if (!StringUtils.hasText(table)) {
                    continue;
                }
                try {
                    collectImportedKeys(md, null, resolvedSchema, table, out);
                } catch (SQLException e) {
                    log.warn("采集表 {} 外键失败: {}", table, e.getMessage());
                }
            }
        }
        return out;
    }

    private void collectImportedKeys(DatabaseMetaData md, String catalog, String schema, String table,
                                     List<TableRelation> out) throws SQLException {
        try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String fkTable = rs.getString("FKTABLE_NAME");
                out.add(new TableRelation(
                    StringUtils.hasText(fkName) ? fkName : table + "->" + fkTable,
                    rs.getString("TABLE_NAME"),
                    rs.getString("COLUMN_NAME"),
                    fkTable,
                    rs.getString("FKCOLUMN_NAME")));
            }
        }
    }

    private static String resolveJdbcSchema(String dbType, String schema, String username) {
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        if ("SQL_SERVER".equals(dbType)) {
            return "dbo";
        }
        if ("DM".equals(dbType) || "DB2".equals(dbType)) {
            return username == null ? null : username.toUpperCase();
        }
        return null;
    }

    private static String oracleSchema(String schema, String username) {
        String s = StringUtils.hasText(schema) ? schema : username;
        return s == null ? null : s.toUpperCase();
    }
}

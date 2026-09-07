/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.web.entity.DbConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DbTypeSupport} 单元测试
 */
class DbTypeSupportTest {

    @Test
    void all_shouldContainSupportedTypes() {
        List<DbTypeSupport.DbTypeInfo> types = DbTypeSupport.all();
        assertNotNull(types);
        // 支持 11 种数据库
        assertTrue(types.size() >= 11);
        // 关键类型必须在列中
        List<String> codes = types.stream().map(DbTypeSupport.DbTypeInfo::code).toList();
        assertTrue(codes.containsAll(List.of(
            "MYSQL", "MARIADB", "ORACLE", "SQL_SERVER", "POSTGRE_SQL",
            "HIGHGO", "DM", "DB2", "H2", "HSQL", "SQLITE")));
    }

    @Test
    void of_shouldReturnDriver() {
        DbTypeSupport.DbTypeInfo mysql = DbTypeSupport.of("MYSQL");
        assertEquals("com.mysql.cj.jdbc.Driver", mysql.driver());
        assertEquals(3306, mysql.defaultPort());

        DbTypeSupport.DbTypeInfo dm = DbTypeSupport.of("DM");
        assertEquals("dm.jdbc.driver.DmDriver", dm.driver());
        assertEquals(5236, dm.defaultPort());
    }

    @Test
    void of_unknownType_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> DbTypeSupport.of("UNKNOWN"));
    }

    @Test
    void buildUrl_mysql_shouldIncludeParams() {
        DbConnection conn = new DbConnection();
        conn.setDbType("MYSQL");
        conn.setHost("127.0.0.1");
        conn.setPort(3306);
        conn.setDatabase("test_db");
        String url = DbTypeSupport.buildUrl(conn);
        assertEquals(
            "jdbc:mysql://127.0.0.1:3306/test_db"
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
            url);
    }

    @Test
    void buildUrl_postgres_schemaOptional() {
        DbConnection conn = new DbConnection();
        conn.setDbType("POSTGRE_SQL");
        conn.setHost("localhost");
        conn.setPort(5432);
        conn.setDatabase("pgdb");
        // 无 schema
        assertEquals("jdbc:postgresql://localhost:5432/pgdb", DbTypeSupport.buildUrl(conn));
        // 有 schema
        conn.setSchemaName("public");
        assertEquals("jdbc:postgresql://localhost:5432/pgdb?currentSchema=public",
            DbTypeSupport.buildUrl(conn));
    }

    @Test
    void buildUrl_h2_shouldUseFilePath() {
        DbConnection conn = new DbConnection();
        conn.setDbType("H2");
        conn.setDatabase("screwweb");
        String url = DbTypeSupport.buildUrl(conn);
        assertTrue(url.startsWith("jdbc:h2:file:./data/screwweb"));
    }

    @Test
    void buildUrl_sqlite_shouldUseRawPath() {
        DbConnection conn = new DbConnection();
        conn.setDbType("SQLITE");
        conn.setDatabase("C:/data/test.db");
        assertEquals("jdbc:sqlite:C:/data/test.db", DbTypeSupport.buildUrl(conn));
    }
}

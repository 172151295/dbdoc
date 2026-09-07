/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.core.Configuration;
import cn.smallbun.screw.core.engine.EngineConfig;
import cn.smallbun.screw.core.engine.EngineFileType;
import cn.smallbun.screw.core.engine.EngineTemplateType;
import cn.smallbun.screw.core.metadata.Column;
import cn.smallbun.screw.core.metadata.PrimaryKey;
import cn.smallbun.screw.core.metadata.Table;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.ModelConverter;
import cn.smallbun.screw.core.metadata.model.TableModel;
import cn.smallbun.screw.core.process.DataModelProcess;
import cn.smallbun.screw.core.process.ProcessConfig;
import cn.smallbun.screw.core.query.DatabaseQuery;
import cn.smallbun.screw.core.query.DatabaseQueryFactory;
import cn.smallbun.screw.core.util.JdbcUtils;
import cn.smallbun.screw.web.common.BizException;
import cn.smallbun.screw.web.dto.TableBriefList;
import cn.smallbun.screw.web.entity.DbConnection;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 元数据查询服务：复用 screw 的 DataModelProcess 获取完整表结构（表/列/主键/注释）
 */
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final ConnectionService connectionService;

    /**
     * 轻量级表清单：仅表名/说明/类型，不读列与主键
     * <p>
     * 千表大库亦可秒级返回（仅一次 JDBC getTables），供对象导出/代码生成页做
     * "先清单、后按需拉列"的渐进式加载。与 {@link #metadata(Long)} 的全量模型互补。
     */
    public TableBriefList tableList(Long connectionId) {
        DbConnection conn = connectionService.get(connectionId);
        try (HikariDataSource ds = connectionService.buildDataSource(conn)) {
            //与全量口径一致：包含视图
            DatabaseQuery query = new DatabaseQueryFactory(ds, true).newInstance();
            List<? extends Table> tables = query.getTables();
            List<TableModel> result = new ArrayList<>(tables.size());
            for (Table table : tables) {
                TableModel tableModel = new TableModel();
                tableModel.setTableName(table.getTableName());
                tableModel.setRemarks(table.getRemarks());
                tableModel.setTableType(table.getTableType());
                result.add(tableModel);
            }
            return new TableBriefList(databaseType(ds), result);
        } catch (Exception e) {
            throw new BizException("表清单获取失败: " + e.getMessage());
        }
    }

    /**
     * 数据库类型名（仅读连接 URL，不访问库）
     */
    private String databaseType(HikariDataSource ds) {
        try (Connection connection = ds.getConnection()) {
            return JdbcUtils.getDbType(connection.getMetaData().getURL()).getName();
        } catch (SQLException e) {
            return "";
        }
    }

    /**
     * 单表列元数据：按需读取指定表的列与主键并标记（与全量模型同一转换规则）
     * <p>
     * 表名必须使用清单下发的精确名称（部分库对大小写敏感）。仅单表 JDBC 查询，毫秒~秒级返回。
     */
    public TableModel tableColumns(Long connectionId, String tableName) {
        DbConnection conn = connectionService.get(connectionId);
        try (HikariDataSource ds = connectionService.buildDataSource(conn)) {
            DatabaseQuery query = new DatabaseQueryFactory(ds, true).newInstance();
            List<? extends Column> columns = query.getTableColumns(tableName);
            List<? extends PrimaryKey> primaryKeys = query.getPrimaryKeys(tableName);
            List<String> pkNames = primaryKeys.stream()
                .map(PrimaryKey::getColumnName).collect(Collectors.toList());
            TableModel tableModel = new TableModel();
            tableModel.setTableName(tableName);
            tableModel.setColumns(ModelConverter.convertColumns(columns, pkNames));
            return tableModel;
        } catch (Exception e) {
            throw new BizException("列信息获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取完整元数据模型
     */
    public DataModel metadata(Long connectionId) {
        DbConnection conn = connectionService.get(connectionId);
        try (HikariDataSource ds = connectionService.buildDataSource(conn)) {
            Configuration config = Configuration.builder()
                .title(conn.getName())
                .dataSource(ds)
                .includeView(true)
                .produceConfig(ProcessConfig.builder().build())
                .engineConfig(EngineConfig.builder()
                    .fileType(EngineFileType.HTML)
                    .produceType(EngineTemplateType.freemarker)
                    .build())
                .build();
            return new DataModelProcess(config).process();
        } catch (Exception e) {
            throw new BizException("元数据获取失败: " + e.getMessage());
        }
    }
}

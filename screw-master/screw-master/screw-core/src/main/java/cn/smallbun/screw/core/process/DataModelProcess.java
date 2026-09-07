/*
 * screw-core - 简洁好用的数据库表结构文档生成工具
 * Copyright © 2020 SanLi (qinggang.zuo@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cn.smallbun.screw.core.process;

import cn.smallbun.screw.core.Configuration;
import cn.smallbun.screw.core.metadata.Column;
import cn.smallbun.screw.core.metadata.Database;
import cn.smallbun.screw.core.metadata.PrimaryKey;
import cn.smallbun.screw.core.metadata.Table;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.ModelConverter;
import cn.smallbun.screw.core.metadata.model.TableModel;
import cn.smallbun.screw.core.query.DatabaseQuery;
import cn.smallbun.screw.core.query.DatabaseQueryFactory;
import cn.smallbun.screw.core.util.CollectionUtils;
import cn.smallbun.screw.core.util.JdbcUtils;
import cn.smallbun.screw.core.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.smallbun.screw.core.constant.DefaultConstants.*;

/**
 * 数据模型处理
 *
 * @author SanLi
 * Created by qinggang.zuo@gmail.com / 2689170096@qq.com on 2020/3/22 21:12
 */
public class DataModelProcess extends AbstractProcess {

    /**
     * 构造方法
     *
     * @param configuration     {@link Configuration}
     */
    public DataModelProcess(Configuration configuration) {
        super(configuration);
    }

    /**
     * 处理
     *
     * @return {@link DataModel}
     */
    @Override
    public DataModel process() {
        //获取query对象（config.isIncludeView() 控制是否包含视图）
        DatabaseQuery query = new DatabaseQueryFactory(config.getDataSource(),
            config.isIncludeView()).newInstance();
        DataModel model = new DataModel();
        //Title
        model.setTitle(config.getTitle());
        //org
        model.setOrganization(config.getOrganization());
        //org url
        model.setOrganizationUrl(config.getOrganizationUrl());
        //version
        model.setVersion(config.getVersion());
        //description
        model.setDescription(config.getDescription());

        /*查询操作开始*/
        long start = System.currentTimeMillis();
        //获取数据库
        Database database = query.getDataBase();
        logger.debug("query the database time consuming:{}ms",
            (System.currentTimeMillis() - start));
        model.setDatabase(database.getDatabase());
        //数据库类型（用于模板中做 Oracle 等条件渲染）
        model.setDatabaseType(getDatabaseType(config.getDataSource()));
        start = System.currentTimeMillis();
        //获取全部表
        List<? extends Table> tables = query.getTables();
        logger.debug("query the table time consuming:{}ms", (System.currentTimeMillis() - start));
        //指定表导出：在读取列/主键之前提前过滤，仅逐表读取选中对象的元数据，
        //避免为了一张表导出而遍历全库（大库下全量读取列信息非常耗时）
        ProcessConfig produceConfig = config.getProduceConfig();
        List<String> designatedTableNames = produceConfig == null ? null
            : produceConfig.getDesignatedTableName();
        List<? extends Column> columns;
        List<? extends PrimaryKey> primaryKeys;
        if (CollectionUtils.isNotEmpty(designatedTableNames)) {
            Set<String> designated = new HashSet<>(designatedTableNames);
            tables = tables.stream().filter(t -> designated.contains(t.getTableName()))
                .collect(Collectors.toList());
            //逐表读取选中表的列与主键
            List<Column> columnList = new ArrayList<>();
            List<PrimaryKey> primaryKeyList = new ArrayList<>();
            for (Table table : tables) {
                columnList.addAll(query.getTableColumns(table.getTableName()));
                primaryKeyList.addAll(query.getPrimaryKeys(table.getTableName()));
            }
            columns = columnList;
            primaryKeys = primaryKeyList;
            logger.debug("designated tables read, table size:{}", tables.size());
        } else {
            //获取全部列
            start = System.currentTimeMillis();
            columns = query.getTableColumns();
            logger.debug("query the column time consuming:{}ms",
                (System.currentTimeMillis() - start));
            //获取主键
            start = System.currentTimeMillis();
            primaryKeys = query.getPrimaryKeys();
            logger.debug("query the primary key time consuming:{}ms",
                (System.currentTimeMillis() - start));
        }
        /*查询操作结束*/

        /*处理数据开始*/
        start = System.currentTimeMillis();
        List<TableModel> tableModels = new ArrayList<>();
        int progressIndex = 0;
        tablesCaching.put(database.getDatabase(), tables);
        for (Table table : tables) {
            //处理列，表名为key，列名为值
            columnsCaching.put(table.getTableName(),
                columns.stream().filter(i -> i.getTableName().equals(table.getTableName()))
                    .collect(Collectors.toList()));
            //处理主键，表名为key，主键为值
            primaryKeysCaching.put(table.getTableName(),
                primaryKeys.stream().filter(i -> i.getTableName().equals(table.getTableName()))
                    .collect(Collectors.toList()));
        }
        for (Table table : tables) {
            /*封装数据开始*/
            TableModel tableModel = new TableModel();
            //表名称
            tableModel.setTableName(table.getTableName());
            //说明
            tableModel.setRemarks(table.getRemarks());
            //表类型（TABLE / VIEW），用于区分普通表和视图
            tableModel.setTableType(table.getTableType());
            //添加表
            tableModels.add(tableModel);
            //进度回调（自增计数，避免 indexOf 的 O(n^2) 开销）
            progressIndex++;
            if (config.getProgressListener() != null) {
                config.getProgressListener().onProgress(progressIndex, tables.size(),
                    table.getTableName());
            }
            //获取主键
            List<String> key = primaryKeysCaching.get(table.getTableName()).stream()
                .map(PrimaryKey::getColumnName).collect(Collectors.toList());
            //处理列（转换逻辑统一收敛到 ModelConverter，供单表按需查询等场景复用）
            tableModel.setColumns(
                ModelConverter.convertColumns(columnsCaching.get(table.getTableName()), key));
        }
        //设置表
        model.setTables(filterTables(tableModels));
        //优化数据
        optimizeData(model);
        /*封装数据结束*/
        logger.debug("encapsulation processing data time consuming:{}ms",
            (System.currentTimeMillis() - start));
        return model;
    }

    /**
     * 获取数据库类型名称（如 MySQL、Oracle、PostgreSql 等），用于模板条件渲染
     *
     * @param dataSource {@link DataSource}
     * @return 数据库类型名称，无法获取时返回空字符串
     */
    private String getDatabaseType(DataSource dataSource) {
        if (dataSource == null) {
            return "";
        }
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            return JdbcUtils.getDbType(url).getName();
        } catch (SQLException e) {
            logger.warn("获取数据库类型失败：{}", e.getMessage());
            return "";
        }
    }

}

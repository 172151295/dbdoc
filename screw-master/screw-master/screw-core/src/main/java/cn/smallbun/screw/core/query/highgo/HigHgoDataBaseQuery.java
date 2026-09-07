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
package cn.smallbun.screw.core.query.highgo;

import cn.smallbun.screw.core.exception.QueryException;
import cn.smallbun.screw.core.mapping.Mapping;
import cn.smallbun.screw.core.metadata.Column;
import cn.smallbun.screw.core.metadata.Database;
import cn.smallbun.screw.core.metadata.PrimaryKey;
import cn.smallbun.screw.core.query.AbstractDatabaseQuery;
import cn.smallbun.screw.core.query.highgo.model.HigHgoColumnModel;
import cn.smallbun.screw.core.query.highgo.model.HigHgoDatabaseModel;
import cn.smallbun.screw.core.query.highgo.model.HigHgoPrimaryKeyModel;
import cn.smallbun.screw.core.query.highgo.model.HigHgoTableModel;
import cn.smallbun.screw.core.util.Assert;
import cn.smallbun.screw.core.util.CollectionUtils;
import cn.smallbun.screw.core.util.ExceptionUtils;
import cn.smallbun.screw.core.util.JdbcUtils;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static cn.smallbun.screw.core.constant.DefaultConstants.PERCENT_SIGN;

/**
 * 瀚高(HighGo)数据库查询
 *
 * <p>瀚高数据库兼容 PostgreSQL 生态，使用官方驱动 com.highgo.jdbc.Driver，
 * JDBC URL 前缀为 jdbc:highgo://。基于 information_schema 与 pg_catalog
 * 元数据完成库/表/列/主键的全链路查询。</p>
 *
 * @author SanLi
 * Created by qinggang.zuo@gmail.com / 2689170096@qq.com on 2020/3/20 17:40
 */
public class HigHgoDataBaseQuery extends AbstractDatabaseQuery {

    /**
     * 构造函数
     *
     * @param dataSource {@link DataSource}
     */
    public HigHgoDataBaseQuery(DataSource dataSource) {
        super(dataSource);
    }

    public HigHgoDataBaseQuery(DataSource dataSource, boolean includeView) {
        super(dataSource, includeView);
    }

    /**
     * 获取数据库
     *
     * @return {@link Database} 数据库信息
     */
    @Override
    public Database getDataBase() throws QueryException {
        HigHgoDatabaseModel model = new HigHgoDatabaseModel();
        //当前数据库名称
        model.setDatabase(getCatalog());
        return model;
    }

    /**
     * 获取表信息
     *
     * @return {@link List} 所有表信息
     */
    @Override
    public List<HigHgoTableModel> getTables() throws QueryException {
        ResultSet resultSet = null;
        try {
            //查询
            resultSet = getMetaData().getTables(getCatalog(), getSchema(), PERCENT_SIGN,
                getTableTypes());
            //映射
            return Mapping.convertList(resultSet, HigHgoTableModel.class);
        } catch (SQLException e) {
            throw ExceptionUtils.mpe(e);
        } finally {
            JdbcUtils.close(resultSet);
        }

    }

    /**
     * 获取列信息
     *
     * @param table {@link String} 表名
     * @return {@link List} 表字段信息
     */
    @Override
    public List<HigHgoColumnModel> getTableColumns(String table) throws QueryException {
        Assert.notEmpty(table, "Table name can not be empty!");
        ResultSet resultSet = null;
        try {
            //查询
            resultSet = getMetaData().getColumns(getCatalog(), getSchema(), table, PERCENT_SIGN);
            //映射
            List<HigHgoColumnModel> list = Mapping.convertList(resultSet, HigHgoColumnModel.class);
            //这里处理是为了如果是查询全部列呢？所以处理并获取唯一表名
            List<String> tableNames = list.stream().map(HigHgoColumnModel::getTableName).distinct()
                .collect(Collectors.toList());
            //查询全部：缓存为空时执行全库列查询（仅首次）
            if (table.equals(PERCENT_SIGN)) {
                if (CollectionUtils.isEmpty(columnsCaching)) {
                    //获取全部表列信息SQL
                    String sql = "SELECT \"TABLE_NAME\", \"TABLE_SCHEMA\", \"COLUMN_NAME\", \"COLUMN_LENGTH\", concat(\"UDT_NAME\", case when \"COLUMN_LENGTH\" isnull then '' else concat('(', concat(\"COLUMN_LENGTH\", ')')) end) \"COLUMN_TYPE\" FROM(select table_schema as \"TABLE_SCHEMA\", column_name as \"COLUMN_NAME\", table_name as \"TABLE_NAME\", udt_name as \"UDT_NAME\", case when coalesce(character_maximum_length, numeric_precision, -1) = -1 then null else coalesce(character_maximum_length, numeric_precision, -1) end as \"COLUMN_LENGTH\" from information_schema.columns a where table_schema = '%s' and table_catalog = '%s') t";
                    PreparedStatement statement = prepareStatement(
                        String.format(sql, getSchema(), getDataBase().getDatabase()));
                    resultSet = statement.executeQuery();
                    int fetchSize = 4284;
                    if (resultSet.getFetchSize() < fetchSize) {
                        resultSet.setFetchSize(fetchSize);
                    }
                    List<HigHgoColumnModel> inquires = Mapping.convertList(resultSet,
                        HigHgoColumnModel.class);
                    //处理列，表名为key，列名为值
                    tableNames.forEach(name -> columnsCaching.put(name, inquires.stream()
                        .filter(i -> i.getTableName().equals(name)).collect(Collectors.toList())));
                }
            }
            //单表查询：未缓存过该表时执行单表列查询，支持多次逐表调用（指定表导出场景）
            else if (!columnsCaching.containsKey(table)) {
                //获取表列信息SQL 查询表名、列名、说明、数据类型
                String sql = "SELECT \"TABLE_NAME\", \"TABLE_SCHEMA\", \"COLUMN_NAME\", \"COLUMN_LENGTH\", concat(\"UDT_NAME\", case when \"COLUMN_LENGTH\" isnull then '' else concat('(', concat(\"COLUMN_LENGTH\", ')')) end) \"COLUMN_TYPE\" FROM(select table_schema as \"TABLE_SCHEMA\", column_name as \"COLUMN_NAME\", table_name as \"TABLE_NAME\", udt_name as \"UDT_NAME\", case when coalesce(character_maximum_length, numeric_precision, -1) = -1 then null else coalesce(character_maximum_length, numeric_precision, -1) end as \"COLUMN_LENGTH\" from information_schema.columns a where table_name = '%s' and table_schema = '%s' and table_catalog = '%s') t";
                resultSet = prepareStatement(
                    String.format(sql, table, getSchema(), getDataBase().getDatabase()))
                    .executeQuery();
                List<HigHgoColumnModel> inquires = Mapping.convertList(resultSet,
                    HigHgoColumnModel.class);
                //单表结果全部属于该表，直接按表名缓存
                columnsCaching.put(table, inquires.stream().collect(Collectors.toList()));
            }
            //处理备注信息
            list.forEach(i -> {
                //从缓存中根据表名获取列信息
                List<Column> columns = columnsCaching.get(i.getTableName());
                if (columns != null) {
                    columns.forEach(j -> {
                        //列名表名一致
                        if (i.getColumnName().equals(j.getColumnName())
                            && i.getTableName().equals(j.getTableName())) {
                            //放入备注
                            i.setColumnLength(j.getColumnLength());
                            i.setColumnType(j.getColumnType());
                        }
                    });
                }
            });
            return list;
        } catch (SQLException e) {
            throw ExceptionUtils.mpe(e);
        } finally {
            JdbcUtils.close(resultSet);
        }
    }

    /**
     * 获取所有列信息
     *
     * @return {@link List} 表字段信息
     * @throws QueryException QueryException
     */
    @Override
    public List<? extends Column> getTableColumns() throws QueryException {
        return getTableColumns(PERCENT_SIGN);
    }

    /**
     * 根据表名获取主键
     *
     * @param table {@link String}
     * @return {@link List}
     * @throws QueryException QueryException
     */
    @Override
    public List<? extends PrimaryKey> getPrimaryKeys(String table) throws QueryException {
        ResultSet resultSet = null;
        try {
            //查询
            resultSet = getMetaData().getPrimaryKeys(getCatalog(), getSchema(), table);
            //映射
            return Mapping.convertList(resultSet, HigHgoPrimaryKeyModel.class);
        } catch (SQLException e) {
            throw ExceptionUtils.mpe(e);
        } finally {
            //仅关闭结果集，不关闭共享连接（逐表查询主键时连接需复用）
            JdbcUtils.close(resultSet);
        }
    }

    /**
     * 根据表名获取主键（批量）
     *
     * @return {@link List}
     * @throws QueryException QueryException
     */
    @Override
    public List<? extends PrimaryKey> getPrimaryKeys() throws QueryException {
        ResultSet resultSet = null;
        try {
            // 由于单条循环查询存在性能问题，所以这里通过自定义SQL查询数据库主键信息
            // 注意：此处使用动态 schema（getSchema()），完美支持任意 schema 下的主键查询，
            // 修正了 PostgreSQL 实现中硬编码 nspname = 'public' 的缺陷
            String sql = "SELECT result.TABLE_CAT, result.TABLE_SCHEM, result.TABLE_NAME, result.COLUMN_NAME, result.KEY_SEQ, result.PK_NAME FROM(SELECT NULL AS TABLE_CAT, n.nspname AS TABLE_SCHEM, ct.relname AS TABLE_NAME, a.attname AS COLUMN_NAME, (information_schema._pg_expandarray(i.indkey)).n AS KEY_SEQ, ci.relname AS PK_NAME, information_schema._pg_expandarray(i.indkey) AS KEYS, a.attnum AS A_ATTNUM FROM pg_catalog.pg_class ct JOIN pg_catalog.pg_attribute a ON (ct.oid = a.attrelid) JOIN pg_catalog.pg_namespace n ON (ct.relnamespace = n.oid) JOIN pg_catalog.pg_index i ON (a.attrelid = i.indrelid) JOIN pg_catalog.pg_class ci ON (ci.oid = i.indexrelid) WHERE true AND n.nspname = '%s' AND i.indisprimary) result where result.A_ATTNUM = (result.KEYS).x ORDER BY result.table_name, result.pk_name, result.key_seq";
            // 拼接 schema
            resultSet = prepareStatement(String.format(sql, getSchema())).executeQuery();
            return Mapping.convertList(resultSet, HigHgoPrimaryKeyModel.class);
        } catch (SQLException e) {
            throw new QueryException(e);
        } finally {
            JdbcUtils.close(resultSet);
        }
    }
}

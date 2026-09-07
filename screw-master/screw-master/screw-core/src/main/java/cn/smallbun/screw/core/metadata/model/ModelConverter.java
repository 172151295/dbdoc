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
package cn.smallbun.screw.core.metadata.model;

import cn.smallbun.screw.core.metadata.Column;
import cn.smallbun.screw.core.metadata.PrimaryKey;
import cn.smallbun.screw.core.metadata.Table;
import cn.smallbun.screw.core.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static cn.smallbun.screw.core.constant.DefaultConstants.N;
import static cn.smallbun.screw.core.constant.DefaultConstants.Y;
import static cn.smallbun.screw.core.constant.DefaultConstants.ZERO;
import static cn.smallbun.screw.core.constant.DefaultConstants.ZERO_DECIMAL_DIGITS;

/**
 * 元数据领域对象转换器（公共工具）
 * <p>
 * 将 {@link Table} / {@link Column} / {@link PrimaryKey} 接口对象转换为对应的 Model 领域对象。
 * 供 {@link cn.smallbun.screw.core.process.DataModelProcess} 与上层应用（如 screw-web 的
 * 单表按需查询接口）共用，保证列映射规则（类型、长度名、主键标记等）单一来源，避免多处复制导致行为漂移。
 *
 * @author SanLi
 * Created by qinggang.zuo@gmail.com / 2689170096@qq.com on 2026/9/7 13:31
 */
public final class ModelConverter implements Serializable {

    private ModelConverter() {
    }

    /**
     * 构建表模型（表名/说明/表类型/列列表）
     *
     * @param table                {@link Table} 表信息
     * @param columns              {@link List} 该表的列信息
     * @param primaryKeyColumnNames {@link List} 该表的主键列名集合
     * @return {@link TableModel}
     */
    public static TableModel buildTableModel(Table table, List<? extends Column> columns,
                                             List<String> primaryKeyColumnNames) {
        TableModel tableModel = new TableModel();
        //表名称
        tableModel.setTableName(table.getTableName());
        //说明
        tableModel.setRemarks(table.getRemarks());
        //表类型（TABLE / VIEW），用于区分普通表和视图
        tableModel.setTableType(table.getTableType());
        //列
        tableModel.setColumns(convertColumns(columns, primaryKeyColumnNames));
        return tableModel;
    }

    /**
     * 列接口列表转 ColumnModel 列表，并按主键列名标记 primaryKey
     *
     * @param columns              {@link List} 列信息
     * @param primaryKeyColumnNames {@link List} 主键列名集合
     * @return {@link List} ColumnModel
     */
    public static List<ColumnModel> convertColumns(List<? extends Column> columns,
                                                   List<String> primaryKeyColumnNames) {
        List<ColumnModel> columnModels = new ArrayList<>();
        for (Column column : columns) {
            packageColumn(columnModels, primaryKeyColumnNames, column);
        }
        return columnModels;
    }

    /**
     * packageColumn
     * @param columnModels {@link List}
     * @param keyList {@link List}
     * @param column {@link Column}
     */
    private static void packageColumn(List<ColumnModel> columnModels, List<String> keyList,
                                      Column column) {
        ColumnModel columnModel = new ColumnModel();
        //表中的列的索引（从 1 开始）
        columnModel.setOrdinalPosition(column.getOrdinalPosition());
        //列名称
        columnModel.setColumnName(column.getColumnName());
        //类型
        columnModel.setColumnType(column.getColumnType());
        //字段名称
        columnModel.setTypeName(column.getTypeName());
        //长度
        columnModel.setColumnLength(column.getColumnLength());
        //size
        columnModel.setColumnSize(column.getColumnSize());
        //小数位
        String decimalDigits = StringUtils.defaultString(column.getDecimalDigits(),
            ZERO_DECIMAL_DIGITS);
        columnModel.setDecimalDigits(decimalDigits);
        //长度名称（对标 SmartSQL LengthName）
        columnModel.setLengthName(
            buildLengthName(column.getTypeName(), column.getColumnLength(), decimalDigits));
        //可为空
        columnModel.setNullable(ZERO.equals(column.getNullable()) ? N : Y);
        //是否主键
        columnModel.setPrimaryKey(keyList.contains(column.getColumnName()) ? Y : N);
        //是否自增
        columnModel.setAutoIncrement(column.getAutoIncrement());
        //默认值
        columnModel.setColumnDef(column.getColumnDef());
        //说明
        columnModel.setRemarks(column.getRemarks());
        //放入集合
        columnModels.add(columnModel);
    }

    /**
     * 构建长度名称（对标 SmartSQL LengthName），形如 (10)、(10,2)
     * <p>
     * 规则（对齐 SmartSQL MySqlExporter/OracleExporter）：
     * <ul>
     *     <li>char/nchar/varchar/varchar2/nvarchar/nvarchar2/binary/varbinary/text/string/time 等 → ({长度})</li>
     *     <li>numeric/decimal/number → ({长度},{小数位})，小数位为 0 或缺失时仅 ({长度})</li>
     *     <li>int/bigint/date/timestamp 等 → 空字符串</li>
     * </ul>
     * 兼容性说明：MySQL 的 columnLength 经 SQL 截取 COLUMN_TYPE 括号内内容，decimal(10,2) 时为 "10,2"；
     * HighGo/PostgreSQL 的 columnLength 为 precision（numeric(10,2) 时为 "10"），小数位取自 decimalDigits。
     *
     * @param typeName      类型名称（如 VARCHAR、numeric）
     * @param columnLength  列长度
     * @param decimalDigits 小数位
     * @return 长度名称
     */
    public static String buildLengthName(String typeName, String columnLength,
                                         String decimalDigits) {
        if (StringUtils.isBlank(typeName) || StringUtils.isBlank(columnLength)) {
            return "";
        }
        String type = typeName.toLowerCase();
        //字符/二进制/文本/时间/字符串类：长度直接展示
        boolean lengthOnly = type.contains("char") || type.contains("binary")
                             || type.contains("text") || type.contains("time")
                             || type.contains("string");
        //数值类：长度 + 小数位
        boolean decimal = type.contains("numeric") || type.contains("decimal")
                          || type.contains("number");
        if (!lengthOnly && !decimal) {
            return "";
        }
        //MySQL 等数据库 columnLength 可能已包含小数位（如 10,2）
        if (columnLength.contains(",")) {
            return "(" + columnLength + ")";
        }
        //数值类型：拼接小数位
        if (decimal && StringUtils.isNotBlank(decimalDigits) && !ZERO.equals(decimalDigits)
            && !"-1".equals(decimalDigits)) {
            return "(" + columnLength + "," + decimalDigits + ")";
        }
        return "(" + columnLength + ")";
    }
}

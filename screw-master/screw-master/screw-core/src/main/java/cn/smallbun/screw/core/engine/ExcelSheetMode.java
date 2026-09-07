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
package cn.smallbun.screw.core.engine;

import lombok.Getter;

import java.io.Serializable;

/**
 * Excel 导出 Sheet 组织模式
 *
 * <ul>
 *     <li>{@link #PER_TABLE}：每个表一个 Sheet（默认，兼容原行为）</li>
 *     <li>{@link #SINGLE}：所有表合并到一个 Sheet（列头含「表名」列）</li>
 * </ul>
 *
 * @author screw-web
 */
@Getter
public enum ExcelSheetMode implements Serializable {

                                                    /**
                                                     * 多 Sheet：每个表一个 Sheet 页
                                                     */
                                                    PER_TABLE("多Sheet(每个表一个Sheet页)"),
                                                    /**
                                                     * 单 Sheet：所有表在一个 Sheet 页
                                                     */
                                                    SINGLE("单Sheet(所有表都在一个Sheet页)");

    private final String desc;

    ExcelSheetMode(String desc) {
        this.desc = desc;
    }
}

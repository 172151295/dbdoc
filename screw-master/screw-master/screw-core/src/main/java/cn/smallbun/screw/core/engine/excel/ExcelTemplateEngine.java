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
package cn.smallbun.screw.core.engine.excel;

import cn.smallbun.screw.core.engine.AbstractTemplateEngine;
import cn.smallbun.screw.core.engine.EngineConfig;
import cn.smallbun.screw.core.engine.ExcelSheetMode;
import cn.smallbun.screw.core.exception.ProduceException;
import cn.smallbun.screw.core.metadata.model.ColumnModel;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.TableModel;
import cn.smallbun.screw.core.util.Assert;
import cn.smallbun.screw.core.util.ExceptionUtils;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Excel 模板引擎（POI 直写 .xlsx）
 *
 * <p>1:1 还原 SmartSQL（EPPlus）的 Excel 数据库表结构文档：</p>
 * <ol>
 *     <li>修订日志 sheet：合并标题行 + 版本号/修订日期/修订内容/修订人/审核人 + 空修订行（边框）</li>
 *     <li>目录 sheet：合并标题行（灰底加粗）+ 序号/表名/注释/说明，多 Sheet 模式下表名带超链接跳转表 Sheet</li>
 *     <li>表结构 sheet：
 *         <ul>
 *             <li>合并表名行（表名+注释，灰底加粗）</li>
 *             <li>列头：序号/列名/数据类型/长度/主键/允许空/默认值/列说明（严格对齐用户模版，不输出「自增」列）</li>
 *             <li>主键/允许空使用「√」标记</li>
 *             <li>多 Sheet 模式末列带「返回目录」超链接（列宽 9.14）</li>
 *             <li>列宽/边框/自动换行与用户模版一致</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * @author SanLi
 * Created by qinggang.zuo@gmail.com / 2689170096@qq.com on 2020/3/21 21:20
 */
public class ExcelTemplateEngine extends AbstractTemplateEngine {

    /**
     * 修订日志 sheet 名称（对齐 SmartSQL AppConst.LOG_CHAPTER_NAME）
     */
    private static final String LOG_CHAPTER_NAME             = "修订日志";
    /**
     * 目录 sheet 名称（对齐 SmartSQL AppConst.TABLE_CHAPTER_NAME）
     */
    private static final String TABLE_CHAPTER_NAME           = "目录";
    /**
     * 单 Sheet 模式表结构 sheet 名称（对齐 SmartSQL AppConst.TABLE_STRUCTURE_CHAPTER_NAME）
     */
    private static final String TABLE_STRUCTURE_CHAPTER_NAME = "表";
    /**
     * 浅灰底色（对齐 SmartSQL #f2f2f2）
     */
    private static final byte[] GRAY_BG                      = new byte[] { (byte) 242, (byte) 242,
                                                                            (byte) 242 };

    /**
     * 构造函数
     *
     * @param templateConfig {@link EngineConfig }
     */
    public ExcelTemplateEngine(EngineConfig templateConfig) {
        super(templateConfig);
    }

    /**
     * 生成文档
     *
     * @param info    {@link DataModel}
     * @param docName 文档名称
     * @throws ProduceException ProduceException
     */
    @Override
    public void produce(DataModel info, String docName) throws ProduceException {
        Assert.notNull(info, "DataModel can not be empty!");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            boolean single = getExcelSheetMode() == ExcelSheetMode.SINGLE;
            // 1. 修订日志
            createLogSheet(workbook);
            // 2. 目录（多 Sheet 模式预计算表 sheet 名，供表名超链接使用；同名表去重避免
            // createSheet 抛 "already contains a sheet"）
            List<String> sheetNames = null;
            if (!single && info.getTables() != null) {
                sheetNames = new ArrayList<>(info.getTables().size());
                Set<String> usedNames = new HashSet<>();
                usedNames.add(LOG_CHAPTER_NAME);
                usedNames.add(TABLE_CHAPTER_NAME);
                for (TableModel table : info.getTables()) {
                    sheetNames.add(uniqueSheetName(validSheetName(table), usedNames));
                }
            }
            createOverviewSheet(workbook, info, sheetNames);
            // 3. 表结构
            if (single) {
                createTableSheet(workbook, TABLE_STRUCTURE_CHAPTER_NAME, info.getTables(), info,
                    true);
            } else if (info.getTables() != null) {
                int total = info.getTables().size();
                for (int i = 0; i < total; i++) {
                    fireProduceProgress(info, i, total);
                    createTableSheet(workbook, sheetNames.get(i),
                        Collections.singletonList(info.getTables().get(i)), info, false);
                }
            }
            // 写文件
            File file = getFile(docName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
            // 打开输出目录
            openOutputDir();
        } catch (Throwable e) {
            throw ExceptionUtils.mpe(e);
        }
    }

    /**
     * 获取 Excel Sheet 组织模式（默认 PER_TABLE 兼容原行为）
     */
    private ExcelSheetMode getExcelSheetMode() {
        ExcelSheetMode mode = super.getEngineConfig().getExcelSheetMode();
        return mode == null ? ExcelSheetMode.PER_TABLE : mode;
    }

    /**
     * 创建修订日志 sheet（1:1 对齐 SmartSQL CreateLogSheet）
     *
     * <p>布局：合并标题行（空）+ 版本号/修订日期/修订内容/修订人/审核人表头 + 16 行空修订行，
     * 全部水平垂直居中 + 细边框，列宽 25/25/50/25/25。</p>
     *
     * @param workbook {@link XSSFWorkbook}
     */
    private void createLogSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(LOG_CHAPTER_NAME);
        // 1. 标题行（合并 A1:E1，SmartSQL 中为空白合并区）
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(14.5f);
        titleRow.createCell(0).setCellStyle(centerStyle(workbook));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        // 2. 表头行：版本号/修订日期/修订内容/修订人/审核人（加粗 10 号居中）
        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(14.5f);
        String[] heads = { "版本号", "修订日期", "修订内容", "修订人", "审核人" };
        CellStyle boldCenter = boldCenterStyle(workbook, (short) 10);
        for (int i = 0; i < heads.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(heads[i]);
            cell.setCellStyle(boldCenter);
        }
        // 3. 16 行空修订行
        int lastRow = 2;
        CellStyle center = centerStyle(workbook);
        for (int i = 0; i < 16; i++) {
            Row row = sheet.createRow(lastRow);
            row.setHeightInPoints(14.5f);
            for (int c = 0; c < 5; c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue("");
                cell.setCellStyle(center);
            }
            lastRow++;
        }
        // 列宽 25/25/50/25/25（EPPlus Width 字符 → POI 1/256 字符宽）
        sheet.setColumnWidth(0, 25 * 256);
        sheet.setColumnWidth(1, 25 * 256);
        sheet.setColumnWidth(2, 50 * 256);
        sheet.setColumnWidth(3, 25 * 256);
        sheet.setColumnWidth(4, 25 * 256);
    }

    /**
     * 创建目录 sheet（1:1 对齐 SmartSQL CreateOverviewSheet）
     *
     * <p>布局：合并标题行（灰底加粗居中，行高 20）+ 序号/表名/注释/说明表头 + 数据行（行高 14.5），
     * 全部细边框，列宽 10/50/50。多 Sheet 模式下表名带超链接跳转对应表 sheet。</p>
     *
     * @param workbook   {@link XSSFWorkbook}
     * @param info       {@link DataModel}
     * @param sheetNames 多 Sheet 模式下各表 sheet 名（与 tables 一一对应），单 Sheet 模式为 null
     */
    private void createOverviewSheet(XSSFWorkbook workbook, DataModel info,
                                     List<String> sheetNames) {
        Sheet sheet = workbook.createSheet(TABLE_CHAPTER_NAME);
        // 1. 标题行：合并 A1:C1，灰底加粗居中
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(20f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(nvl(info.getTitle()));
        titleCell.setCellStyle(grayBoldCenterStyle(workbook, (short) 11));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
        // 2. 表头行：序号/表名/注释/说明（加粗 10 号居中）
        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(14.5f);
        String[] heads = { "序号", "表名", "注释/说明" };
        CellStyle boldCenter = boldCenterStyle(workbook, (short) 10);
        for (int i = 0; i < heads.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(heads[i]);
            cell.setCellStyle(boldCenter);
        }
        // 3. 数据行
        CellStyle center = centerStyle(workbook);
        CellStyle left = leftStyle(workbook);
        CellStyle link = linkStyle(workbook);
        int rowIdx = 2;
        if (info.getTables() != null) {
            for (int i = 0; i < info.getTables().size(); i++) {
                TableModel table = info.getTables().get(i);
                Row row = sheet.createRow(rowIdx);
                row.setHeightInPoints(14.5f);
                // 序号（居中）
                Cell orderCell = row.createCell(0);
                setOrderNum(orderCell, i + 1);
                orderCell.setCellStyle(center);
                // 表名（左对齐；多 Sheet 模式带超链接）
                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(nvl(table.getTableName()));
                if (sheetNames != null) {
                    Hyperlink hyperlink = workbook.getCreationHelper()
                        .createHyperlink(HyperlinkType.DOCUMENT);
                    hyperlink.setAddress(sheetNames.get(i) + "!A1");
                    nameCell.setHyperlink(hyperlink);
                    nameCell.setCellStyle(link);
                } else {
                    nameCell.setCellStyle(left);
                }
                // 注释/说明（左对齐）
                Cell commentCell = row.createCell(2);
                commentCell.setCellValue(nvl(table.getRemarks()));
                commentCell.setCellStyle(left);
                rowIdx++;
            }
        }
        // 列宽 10/50/50
        sheet.setColumnWidth(0, 10 * 256);
        sheet.setColumnWidth(1, 50 * 256);
        sheet.setColumnWidth(2, 50 * 256);
    }

    /**
     * 创建表结构 sheet（1:1 对齐 SmartSQL CreateTableSheet）
     *
     * <p>布局：每张表一段——合并表名行（表名+注释，灰底加粗居中，行高 16）→ 列头行
     * （序号/列名/数据类型/长度/主键/自增/允许空/默认值/列说明，加粗居中；Oracle 系隐藏「自增」列）→
     * 数据行（序号/主键/自增/允许空/默认值居中，列名/数据类型/列说明左对齐）→ 分隔空行（左右边框+合并）。
     * 多 Sheet 模式下列头行末列追加「返回目录」超链接（蓝色下划线，指向 目录!A1）。
     * 边框范围：fromRow..rowNum-1 的 1..spColCount 列（不含返回目录列），整表自动换行 + 缩放到适应。</p>
     *
     * @param workbook  {@link XSSFWorkbook}
     * @param sheetName sheet 名称
     * @param tables    表集合（单 Sheet 模式为全部表；多 Sheet 模式为单表）
     * @param info      {@link DataModel}
     * @param single    是否单 Sheet 模式（true 时不加「返回目录」列）
     */
    private void createTableSheet(XSSFWorkbook workbook, String sheetName, List<TableModel> tables,
                                  DataModel info, boolean single) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowNum = 0, count = 0; // 行号计数器（0-based）
        // 循环数据库表名
        if (tables == null) {
            return;
        }
        for (TableModel table : tables) {
            // 严格对齐用户模版：固定 序号/列名/数据类型/长度/主键/允许空/默认值/列说明，不输出「自增」列
            List<String> lstName = new ArrayList<>(
                Arrays.asList("序号", "列名", "数据类型", "长度", "主键", "允许空", "默认值", "列说明"));
            int spColCount = lstName.size();
            // 表名称行：表名 + 注释（合并，灰底加粗居中，行高 16）
            String comment = nvl(table.getTableName()) + " "
                             + (table.getRemarks() == null || table.getRemarks().trim().isEmpty()
                                 ? ""
                                 : table.getRemarks().trim());
            Row tableRow = sheet.createRow(rowNum);
            tableRow.setHeightInPoints(16f);
            Cell tableCell = tableRow.createCell(0);
            tableCell.setCellValue(comment);
            tableCell.setCellStyle(grayBoldCenterStyle(workbook, (short) 10));
            if (spColCount > 1) {
                sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, spColCount - 1));
            }
            rowNum++; // 行号+1
            // 列头行：加粗居中
            Row headerRow = sheet.createRow(rowNum);
            headerRow.setHeightInPoints(14.5f);
            CellStyle boldCenter = boldCenterStyle(workbook, (short) 10);
            for (int j = 0; j < lstName.size(); j++) {
                Cell cell = headerRow.createCell(j);
                cell.setCellValue(lstName.get(j));
                cell.setCellStyle(boldCenter);
            }
            // 列头行末列加「返回目录」超链接（蓝色下划线）——多 Sheet 与单 Sheet 均输出
            {
                Cell backCell = headerRow.createCell(lstName.size());
                backCell.setCellValue("返回目录");
                Hyperlink hyperlink = workbook.getCreationHelper()
                    .createHyperlink(HyperlinkType.DOCUMENT);
                hyperlink.setAddress(TABLE_CHAPTER_NAME + "!A1");
                backCell.setHyperlink(hyperlink);
                backCell.setCellStyle(linkStyle(workbook));
            }
            rowNum++; // 行号+1
            // 数据行
            CellStyle center = centerStyle(workbook);
            CellStyle left = leftStyle(workbook);
            if (table.getColumns() != null) {
                for (int i = 0; i < table.getColumns().size(); i++) {
                    ColumnModel column = table.getColumns().get(i);
                    Row row = sheet.createRow(rowNum);
                    row.setHeightInPoints(14.5f);
                    // 序号（居中）
                    Cell c1 = row.createCell(0);
                    setOrderNum(c1, i + 1);
                    c1.setCellStyle(center);
                    // 列名（左对齐）
                    Cell c2 = row.createCell(1);
                    c2.setCellValue(nvl(column.getColumnName()));
                    c2.setCellStyle(left);
                    // 数据类型（左对齐）
                    Cell c3 = row.createCell(2);
                    c3.setCellValue(nvl(column.getTypeName()));
                    c3.setCellStyle(left);
                    // 长度（居中）
                    Cell c4 = row.createCell(3);
                    c4.setCellValue(nvl(column.getLengthName()));
                    c4.setCellStyle(center);
                    // 主键（居中，√ 标记）
                    Cell c5 = row.createCell(4);
                    c5.setCellValue(yesOrNo(column.getPrimaryKey()));
                    c5.setCellStyle(center);
                    // 严格对齐用户模版：不输出「自增」列，固定 允许空/默认值/列说明
                    // 允许空（居中，√ 标记）
                    Cell c6 = row.createCell(5);
                    c6.setCellValue(yesOrNo(column.getNullable()));
                    c6.setCellStyle(center);
                    // 默认值（居中）
                    Cell c7 = row.createCell(6);
                    c7.setCellValue(nvl(column.getColumnDef()));
                    c7.setCellStyle(center);
                    // 列说明（左对齐）
                    Cell c8 = row.createCell(7);
                    c8.setCellValue(nvl(column.getRemarks()));
                    c8.setCellStyle(left);
                    rowNum++; // 行号+1
                }
            }
            // 边框：表名行(灰底加粗)/列头行(加粗)/数据行(center/left)样式均已含四边细边框，
            // 范围覆盖 SmartSQL 的 fromRow..rowNum-1 的 1..spColCount 列
            // 处理空白行，分割用（左右边框 + 合并）
            if (count < tables.size() - 1) {
                Row blankRow = sheet.createRow(rowNum);
                blankRow.setHeightInPoints(14.5f);
                Cell blankCell = blankRow.createCell(0);
                blankCell.setCellStyle(blankBorderStyle(workbook));
                if (spColCount > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, spColCount - 1));
                }
            }
            rowNum++; // 行号+1
            count++; // 计数器+1
        }
        // 列宽（严格对齐用户模版：序号10/列名35/数据类型15/长度10/主键10/允许空10/默认值10/列说明35）
        sheet.setColumnWidth(0, 10 * 256);
        sheet.setColumnWidth(1, 35 * 256);
        sheet.setColumnWidth(2, 15 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 10 * 256);
        sheet.setColumnWidth(5, 10 * 256);
        sheet.setColumnWidth(6, 10 * 256);
        sheet.setColumnWidth(7, 35 * 256);
        // 返回目录列宽 9.14（严格对齐模版 I 列）——单 Sheet 与多 Sheet 均输出
        sheet.setColumnWidth(8, (int) Math.round(9.14 * 256));
        // 整表自动换行 + 缩放到适应
        CellStyle wrapShrink = wrapShrinkStyle(workbook);
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellStyle() == null) {
                    cell.setCellStyle(wrapShrink);
                }
            }
        }
    }

    /**
     * 居中样式：水平垂直居中 + 细边框 + 自动换行
     */
    private CellStyle centerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setShrinkToFit(true);
        setThinBorder(style);
        return style;
    }

    /**
     * 左对齐样式：水平左对齐 + 垂直居中 + 细边框 + 自动换行
     */
    private CellStyle leftStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setShrinkToFit(true);
        setThinBorder(style);
        return style;
    }

    /**
     * 加粗居中样式（用于表头/列头）
     */
    private CellStyle boldCenterStyle(XSSFWorkbook workbook, short fontSize) {
        CellStyle style = centerStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints(fontSize);
        style.setFont(font);
        return style;
    }

    /**
     * 灰底加粗居中样式（用于标题行/表名行）
     */
    private CellStyle grayBoldCenterStyle(XSSFWorkbook workbook, short fontSize) {
        CellStyle style = boldCenterStyle(workbook, fontSize);
        style.setFillForegroundColor(new XSSFColor(GRAY_BG, new DefaultIndexedColorMap()));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 超链接样式：蓝色下划线 + 左对齐（对齐 SmartSQL 蓝色下划线）
     */
    private CellStyle linkStyle(XSSFWorkbook workbook) {
        CellStyle style = leftStyle(workbook);
        Font font = workbook.createFont();
        font.setUnderline(Font.U_SINGLE);
        font.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    /**
     * 空白分隔行样式：左右边框 + 自动换行
     */
    private CellStyle blankBorderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        style.setShrinkToFit(true);
        return style;
    }

    /**
     * 自动换行 + 缩放到适应样式（整表兜底）
     */
    private CellStyle wrapShrinkStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setShrinkToFit(true);
        return style;
    }

    /**
     * 设置细边框（上下左右）
     */
    private void setThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /**
     * sheet 名称合法化（严格对齐用户模版：sheet 标签名仅为表名，如 ZC_T_SYS_ORGANIZATION，
     * 不追加注释；Excel 限制 31 字符内，不能包含 \ / ? * [ ] : 等）
     */
    private String validSheetName(TableModel table) {
        String name = table == null || table.getTableName() == null ? "Sheet"
            : table.getTableName();
        // 替换非法字符
        name = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (name.length() > 31) {
            name = name.substring(0, 31);
        }
        return name;
    }

    /**
     * sheet 名去重：同名表（如不同 schema 下同表名）或 31 字符截断撞名时，追加 _2、_3…
     * 序号，确保 workbook 内 sheet 名唯一，避免 createSheet 抛 already contains a sheet
     *
     * @param name      合法化后的候选名
     * @param usedNames 已占用的 sheet 名集合（含「修订日志」「目录」等固定 sheet）
     * @return 唯一后的 sheet 名，并登记进 usedNames
     */
    private String uniqueSheetName(String name, Set<String> usedNames) {
        if (usedNames.add(name)) {
            return name;
        }
        // 预留序号后缀空间，防止追加序号后超 31 字符
        int maxLen = 31 - 4;
        String base = name.length() > maxLen ? name.substring(0, maxLen) : name;
        for (int i = 2;; i++) {
            String candidate = base + "_" + i;
            if (usedNames.add(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * 空值转换
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /**
     * 触发 produce 阶段逐表进度回调（EngineConfig.produceProgressListener 可选）
     *
     * @param info   {@link DataModel}
     * @param index  当前表下标（0-based）
     * @param total  总表数
     */
    private void fireProduceProgress(DataModel info, int index, int total) {
        cn.smallbun.screw.core.process.ProgressListener listener = getEngineConfig()
            .getProduceProgressListener();
        if (listener == null || info.getTables() == null || info.getTables().isEmpty()) {
            return;
        }
        try {
            listener.onProgress(index + 1, total, info.getTables().get(index).getTableName());
        } catch (Exception e) {
            // 进度回调异常不影响文档生成
        }
    }

    /**
     * 是/否转换（对齐 SmartSQL：true → "√"，false → ""）
     *
     * <p>兼容 Y/N、YES/NO、1/0、true/false 等取值；无法识别时原样返回。</p>
     */
    private String yesOrNo(String value) {
        if (value == null) {
            return "";
        }
        if ("YES".equalsIgnoreCase(value) || "Y".equalsIgnoreCase(value) || "1".equals(value)
            || "true".equalsIgnoreCase(value) || "√".equals(value)) {
            return "√";
        }
        if ("NO".equalsIgnoreCase(value) || "N".equalsIgnoreCase(value) || "0".equals(value)
            || "false".equalsIgnoreCase(value)) {
            return "";
        }
        return value;
    }

    /**
     * 写入整型序号（对齐用户模版 <v>1</v>）
     *
     * <p>POI 的 {@link Cell#setCellValue(double)} 无 int 重载，直接传 int 会以 double
     * 存储并序列化为 <v>1.0</v>；模板（SmartSQL/EPPlus）中为 <v>1</v>，
     * 故写入后回写 CT_Cell 的整数字面量，保证序号列与模版完全一致。</p>
     *
     * @param cell  目标单元格
     * @param order 序号（从 1 开始）
     */
    private void setOrderNum(Cell cell, int order) {
        cell.setCellValue(order);
        if (cell instanceof XSSFCell) {
            ((XSSFCell) cell).getCTCell().setV(Integer.toString(order));
        }
    }
}

<#--
    screw-core - 简洁好用的数据库表结构文档生成工具
    Copyright © 2020 SanLi (qinggang.zuo@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    DDL 模板：从 DataModel 生成 CREATE TABLE / DROP / COMMENT 语句。
    完整的 DDL（含索引、视图、函数）由 screw-web DdlService 基于 JDBC 元数据生成，
    此模板作为 Freemarker 引擎的后备输出（仅含 CREATE TABLE + COMMENT）。
-->
-- ============================================================
-- 数据库: ${database!''}
-- 生成工具: screw-core
-- <#if (version)??>版本: ${version!''}</#if>
-- <#if (description)??>描述: ${description!''}</#if>
-- ============================================================

<#list tables>
<#items as t>
-- ------------------------------------------------------------
-- 表: ${t.tableName!''}
<#if (t.remarks)??>-- 备注: ${t.remarks!''}</#if>
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ${t.tableName!''};

CREATE TABLE ${t.tableName!''} (
<#list t.columns>
<#items as c>
    ${c.columnName!''} ${c.columnType!''}<#if (c.nullable)?? && c.nullable == 'NO'> NOT NULL</#if><#if (c.columnDef)?? && c.columnDef != ''> DEFAULT ${c.columnDef!''}</#if><#if c?has_next>,</#if>
</#items>
</#list>
);

<#list t.columns>
<#items as c>
<#if (c.remarks)?? && c.remarks != ''>
COMMENT ON COLUMN ${t.tableName!''}.${c.columnName!''} IS '${c.remarks!''}';
</#if>
</#items>
</#list>
<#if (t.remarks)?? && t.remarks != ''>
COMMENT ON TABLE ${t.tableName!''} IS '${t.remarks!''}';
</#if>

</#items>
</#list>
-- ======================= End of DDL =======================

<#--
    screw-core - 简洁好用的数据库表结构文档生成工具
    Copyright © 2020 SanLi (qinggang.zuo@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
-->
<?xml version="1.0" encoding="UTF-8"?>
<database>
  <title>${title!'数据库设计文档'}</title>
  <name>${database!''}</name>
<#if (version)??><version>${version!''}</version></#if>
<#if (description)??><description>${description!''}</description></#if>
<#list tables>
<#items as t>
  <table>
    <tableName>${t.tableName!''}</tableName>
    <remarks>${t.remarks!''}</remarks>
<#if (t.tableType)??><tableType>${t.tableType!''}</tableType></#if>
<#list t.columns>
<#items as c>
    <column>
      <ordinalPosition>${c.ordinalPosition!''}</ordinalPosition>
      <columnName>${c.columnName!''}</columnName>
      <columnType>${c.columnType!''}</columnType>
      <typeName>${c.typeName!''}</typeName>
      <columnSize>${c.columnSize!''}</columnSize>
      <decimalDigits>${c.decimalDigits!'0'}</decimalDigits>
      <nullable>${c.nullable!''}</nullable>
      <primaryKey>${c.primaryKey!''}</primaryKey>
<#if (c.autoIncrement)??><autoIncrement>${c.autoIncrement!''}</autoIncrement></#if>
      <columnDef>${c.columnDef!''}</columnDef>
      <remarks>${c.remarks!''}</remarks>
    </column>
</#items>
</#list>
  </table>
</#items>
</#list>
</database>

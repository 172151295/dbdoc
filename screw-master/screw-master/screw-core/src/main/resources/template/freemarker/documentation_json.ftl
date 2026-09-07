<#--
    screw-core - 简洁好用的数据库表结构文档生成工具
    Copyright © 2020 SanLi (qinggang.zuo@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
-->
{
  "title": "${title!'数据库设计文档'}",
  "database": "${database!''}",
  <#if (version)??>"version": "${version!''}",</#if>
  <#if (description)??>"description": "${description!''}",</#if>
  "tables": [
<#list tables>
<#items as t>
    {
      "tableName": "${t.tableName!''}",
      "remarks": "${t.remarks!''}",
      <#if (t.tableType)??>"tableType": "${t.tableType!''}",</#if>
      "columns": [
<#list t.columns>
<#items as c>
        {
          "ordinalPosition": "${c.ordinalPosition!''}",
          "columnName": "${c.columnName!''}",
          "columnType": "${c.columnType!''}",
          "typeName": "${c.typeName!''}",
          "columnSize": "${c.columnSize!''}",
          "decimalDigits": "${c.decimalDigits!'0'}",
          "nullable": "${c.nullable!''}",
          "primaryKey": "${c.primaryKey!''}",
          <#if (c.autoIncrement)??>"autoIncrement": "${c.autoIncrement!''}",</#if>
          "columnDef": "${c.columnDef!''}",
          "remarks": "${c.remarks!''}"
        }<#if c?has_next>,</#if>
</#items>
</#list>
      ]
    }<#if t?has_next>,</#if>
</#items>
</#list>
  ]
}

/*
 * screw-web - 数据库表结构文档生成平台
 * Copyright © 2026
 */
package cn.smallbun.screw.web.service;

import cn.smallbun.screw.core.metadata.model.ColumnModel;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.TableModel;
import cn.smallbun.screw.web.common.BizException;
import cn.smallbun.screw.web.dto.CodeGenerateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 代码生成服务：基于数据库元数据生成 Java/C# 实体类
 */
@Service
@RequiredArgsConstructor
public class CodeGenerateService {

    private final MetadataService metadataService;

    /**
     * 生成实体代码
     *
     * @return Map&lt;表名, 代码内容&gt;
     */
    public Map<String, String> generate(CodeGenerateRequest req) {
        DataModel model = metadataService.metadata(req.getConnectionId());
        Set<String> tables = req.getTables() == null ? Set.of()
            : req.getTables().stream().filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toSet());
        List<TableModel> list = model.getTables() == null ? List.of() : model.getTables();
        if (!tables.isEmpty()) {
            list = list.stream().filter(t -> tables.contains(t.getTableName()))
                .collect(Collectors.toList());
        }
        if (list.isEmpty()) {
            throw new BizException("没有可生成代码的表");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (TableModel table : list) {
            String code;
            if ("CSHARP".equalsIgnoreCase(req.getLanguage())) {
                code = generateCSharp(table, req);
            } else {
                code = generateJava(table, req);
            }
            result.put(table.getTableName(), code);
        }
        return result;
    }

    // ==================== Java ====================

    private String generateJava(TableModel table, CodeGenerateRequest req) {
        String packageName = nvl(req.getPackageName(), "com.example.entity");
        boolean lombok = req.getLombok() == null || req.getLombok();
        boolean swagger = req.getSwagger() != null && req.getSwagger();
        String className = toUpperCamel(table.getTableName());
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        if (lombok) {
            sb.append("import lombok.Data;\n");
        }
        if (swagger) {
            sb.append("import io.swagger.annotations.ApiModel;\n");
            sb.append("import io.swagger.annotations.ApiModelProperty;\n");
        }
        sb.append("\n");
        if (lombok) {
            sb.append("@Data\n");
        }
        if (swagger) {
            String remark = nvl(table.getRemarks(), className);
            sb.append("@ApiModel(value = \"").append(remark).append("\")\n");
        }
        sb.append("public class ").append(className).append(" {\n\n");
        // 字段
        List<ColumnModel> columns = table.getColumns() == null ? List.of()
            : table.getColumns();
        for (ColumnModel col : columns) {
            String fieldName = toLowerCamel(col.getColumnName());
            String javaType = toJavaType(col);
            String remark = nvl(col.getRemarks(), col.getColumnName());
            if (swagger) {
                sb.append("    @ApiModelProperty(value = \"").append(remark).append("\")\n");
            } else {
                sb.append("    /** ").append(remark).append(" */\n");
            }
            sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }
        // getter/setter（非 Lombok 时生成）
        if (!lombok) {
            for (ColumnModel col : columns) {
                String fieldName = toLowerCamel(col.getColumnName());
                String javaType = toJavaType(col);
                sb.append("    public ").append(javaType).append(" get")
                    .append(toUpperCamel(fieldName)).append("() {\n");
                sb.append("        return ").append(fieldName).append(";\n    }\n\n");
                sb.append("    public void set").append(toUpperCamel(fieldName)).append("(")
                    .append(javaType).append(" ").append(fieldName).append(") {\n");
                sb.append("        this.").append(fieldName).append(" = ").append(fieldName)
                    .append(";\n    }\n\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ==================== C# ====================

    private String generateCSharp(TableModel table, CodeGenerateRequest req) {
        String namespace = nvl(req.getNamespace(), "Entities");
        String className = toUpperCamel(table.getTableName());
        StringBuilder sb = new StringBuilder();
        sb.append("using System;\nusing System.ComponentModel.DataAnnotations;\n\n");
        sb.append("namespace ").append(namespace).append("\n{\n");
        sb.append("    /// <summary>\n");
        sb.append("    /// ").append(nvl(table.getRemarks(), className)).append("\n");
        sb.append("    /// </summary>\n");
        sb.append("    public class ").append(className).append("\n    {\n");
        List<ColumnModel> columns = table.getColumns() == null ? List.of()
            : table.getColumns();
        for (ColumnModel col : columns) {
            String propName = toUpperCamel(col.getColumnName());
            String csType = toCSharpType(col);
            String remark = nvl(col.getRemarks(), col.getColumnName());
            sb.append("        /// <summary>\n");
            sb.append("        /// ").append(remark).append("\n");
            sb.append("        /// </summary>\n");
            sb.append("        [Display(Name = \"").append(remark).append("\")]\n");
            sb.append("        public ").append(csType).append(" ").append(propName)
                .append(" { get; set; }\n\n");
        }
        sb.append("    }\n}\n");
        return sb.toString();
    }

    // ==================== 类型映射 ====================

    private String toJavaType(ColumnModel col) {
        String type = col.getTypeName() == null ? "" : col.getTypeName().toUpperCase();
        String len = nvl(col.getColumnLength(), "");
        if (type.contains("INT") || type.contains("SERIAL") || type.contains("YEAR")) {
            return "Integer";
        }
        if (type.contains("BIGINT") || type.contains("LONG")) {
            return "Long";
        }
        if (type.contains("DECIMAL") || type.contains("NUMERIC") || type.contains("MONEY")) {
            return "java.math.BigDecimal";
        }
        if (type.contains("FLOAT")) {
            return "Float";
        }
        if (type.contains("DOUBLE") || type.contains("REAL")) {
            return "Double";
        }
        if (type.contains("BOOL")) {
            return "Boolean";
        }
        if (type.contains("DATE") || type.contains("TIME") || type.contains("TIMESTAMP")
            || type.contains("DATETIME")) {
            return "java.util.Date";
        }
        if (type.contains("BLOB") || type.contains("BINARY") || type.contains("BYTEA")
            || type.contains("VARBINARY")) {
            return "byte[]";
        }
        if (type.contains("TEXT") || type.contains("CHAR") || type.contains("CLOB")
            || type.contains("JSON") || type.contains("ENUM") || type.contains("VARCHAR")) {
            return "String";
        }
        // 未知类型按长度判断
        try {
            if (!len.isEmpty() && Integer.parseInt(len) > 255) {
                return "String";
            }
        } catch (NumberFormatException ignored) {
            // ignore
        }
        return "Object";
    }

    private String toCSharpType(ColumnModel col) {
        String type = col.getTypeName() == null ? "" : col.getTypeName().toUpperCase();
        if (type.contains("INT") || type.contains("SERIAL") || type.contains("YEAR")) {
            return "int";
        }
        if (type.contains("BIGINT") || type.contains("LONG")) {
            return "long";
        }
        if (type.contains("DECIMAL") || type.contains("NUMERIC") || type.contains("MONEY")) {
            return "decimal";
        }
        if (type.contains("FLOAT")) {
            return "float";
        }
        if (type.contains("DOUBLE") || type.contains("REAL")) {
            return "double";
        }
        if (type.contains("BOOL")) {
            return "bool";
        }
        if (type.contains("DATE") || type.contains("TIME") || type.contains("TIMESTAMP")
            || type.contains("DATETIME")) {
            return "DateTime";
        }
        if (type.contains("BLOB") || type.contains("BINARY") || type.contains("BYTEA")
            || type.contains("VARBINARY")) {
            return "byte[]";
        }
        return "string";
    }

    // ==================== 工具 ====================

    private String toUpperCamel(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.length() == 0 ? name : sb.toString();
    }

    private String toLowerCamel(String name) {
        String camel = toUpperCamel(name);
        if (camel.isEmpty()) {
            return camel;
        }
        return Character.toLowerCase(camel.charAt(0)) + camel.substring(1);
    }

    private String nvl(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }
}

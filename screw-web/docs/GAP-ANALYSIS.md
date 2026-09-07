# SmartSQL 功能差距分析与实施计划

> 目标：全面替代 SmartSQL 桌面工具，实现 Web 版并**超越**其功能。
> 技术栈：screw-core（后端核心）+ screw-web（Spring Boot 服务）+ screw-web-ui（Vue3+NaiveUI）。

## 一、SmartSQL 功能清单 vs 现状

| # | SmartSQL 功能 | screw-web 现状 | 差距 | 优先级 | 实现方案 |
|---|---|---|---|---|---|
| 1 | 多数据库连接管理（MySQL/Oracle/SqlServer/PG/SQLite/Redis/DM） | ✅ MySQL/MariaDB/Oracle/SqlServer/PG/HighGo/DM/DB2/HSQL/H2/SQLite | Redis 缺失 | P1 | 新增 Redis 连接（screw-core 无元数据，仅作测试/浏览 Key） |
| 1a | 国产化数据库：Doris/TiDB/OceanBase/人大金仓/虚谷/达梦/瀚高 | ✅ Doris/TiDB/OceanBase（MySQL协议）+ 金仓/虚谷/达梦/瀚高 | — | — | Doris/TiDB/OceanBase 复用 MySQL 驱动与元数据查询；金仓/虚谷已引入官方驱动 |
| 2 | 对象浏览：表+视图+索引+触发器 | ⚠️ 仅表 | 视图/索引缺失 | P1 | screw-core 只取表；新增视图/索引/触发器元数据 API（自研） |
| 3 | 对象名模糊搜索 | ✅ 前端已做 | — | — | — |
| 4 | 备注说明在线编辑（表/字段） | ❌ | **缺** | P1 | 新增注释编辑 API（ALTER TABLE COMMENT） |
| 5 | 注释导入/导出 | ❌ | **缺** | P2 | Excel/JSON 导入导出注释 |
| 6 | 文档导出：HTML/Word/MD/Excel | ✅ 4 种 | CHM/PDF/XML/JSON 缺失 | P2 | 新增 XML/JSON（易）；PDF（自研/开源）；CHM（难，建议 JSON+MD 替代） |
| 7 | Excel 导出：单Sheet（所有表一页）+多Sheet（每表一页） | ⚠️ 仅多Sheet | **单Sheet 缺** | **P0** | 扩展 screw-core EngineConfig 加 sheet 模式 |
| 8 | 代码生成：C#/Java 实体 | ❌ | **缺** | P1 | 自研代码生成服务（freemarker 模板） |
| 9 | DB 对比（源/目标库表结构对比） | ❌ | **缺** | P2 | 自研对比服务（复用元数据） |
| 10 | 对象分组管理 | ⚠️ 连接分组已有 | 对象级分组缺 | P3 | 表收藏/标签 |
| 11 | SQL 脚本窗口 | ❌ | **缺** | P2 | SQL 执行 API + 前端终端 |
| 12 | Redis 客户端 | ❌ | **缺** | P3 | Redis 连接浏览 Key/Value |
| 13 | 设置项（多标签/模糊搜索/保存提示） | ❌ | 部分 | P3 | 前端设置面板 |
| 14 | 工具箱 | ❌ | **缺** | P3 | 常用工具聚合（SQL格式化/时间戳等） |

## 二、screw-core 能力边界（已核实）

- 支持库：MySQL、MariaDB、Oracle、SqlServer、PostgreSQL、HighGo、DM、DB2、HSQL、H2、SQLite
- 导出引擎：HTML/WORD/MD（freemarker）、EXCEL（EasyExcel，固定每表一 sheet）
- **限制**：仅表元数据（无视图/索引/触发器）；H2 查询显式抛 "Not supported yet!"
- 依赖：easyexcel、freemarker、velocity、HikariCP、fastjson（已含）

## 三、实施批次

- **批次 0（P0）**：Excel 单/多 Sheet 双模式（screw-core + screw-web + 前端选择）
- **批次 1（P1）**：注释在线编辑、代码生成（Java/C#）、视图/索引元数据 API
- **批次 2（P2）**：XML/JSON 导出、SQL 脚本窗口、DB 对比
- **批次 3（P3）**：注释导入导出、Redis、对象分组、工具箱、设置

## 四、关键设计决策

1. **Excel 双模式**：在 `EngineConfig` 增加 `excelSheetMode`（枚举 `SINGLE`/`PER_TABLE`，默认 PER_TABLE 保持兼容）；单 sheet 模式在列头增加「表名」列，表目录 + 全部列数据合并。
2. **代码生成**：新增 `CodeGenerateService`，用 freemarker 模板生成 Java/C# 实体（参考 SmartSQL 的 CsharpLang/JavaLang），支持 Lombok 注解选项。
3. **DB 对比**：复用 `MetadataService` 取两库表/列集合，输出 新增/删除/修改（表级+列级）。
4. **注释编辑**：MySQL 用 `ALTER TABLE ... MODIFY COLUMN ... COMMENT`；其他库按方言实现；不可编辑的库降级为前端本地保存。
5. **单 jar**：所有新增能力打包进 screw-web JAR，前端继续 hash 路由 + static 直出。

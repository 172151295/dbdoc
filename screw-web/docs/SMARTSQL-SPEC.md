# SmartSQL 一比一还原规格蓝本

> 目标：把 `../SmartSQL-master`（C# WPF 桌面工具）的功能**一比一还原**到 screw-web（Spring Boot + Vue3）。
> 本文件是从 SmartSQL 源码逐项核实的**功能规格**与**批次执行计划**，每个批次含验收标准。
> 更新日期：2026-09-02

## 一、SmartSQL 功能全景（源码核实，非猜测）

### 1. 数据库支持（Exporter 层）
SmartSQL 自带导出器：**MySQL / Oracle / SQL Server / PostgreSQL / SQLite / Dm(达梦) / Redis**（`Framework/Exporter/`）。
screw-web 现状：MySQL/MariaDB/Oracle/SqlServer/PG/HighGo/DM/DB2/HSQL/H2/SQLite/Doris/TiDB/OceanBase/金仓/虚谷 → **已超集**，**仅缺 Redis**。

### 2. 主窗口布局与菜单（MainWindow.xaml）
菜单栏：
- 文件（选择数据源 / 新建 / 打开…）
- 数据源
- **导入备注**（ImportMark）
- **导出文档**（ExportDoc）
- **生成代码**（GenCode）
- **工具**：分组管理 / 标签管理 / 选项（SettingWindow）
- 帮助

左侧对象树（按数据源分组），节点图标类型含：**表 Table / 视图 View / 存储过程 Proc**（`SysConst.cs: Sys_TABLEICON/VIEWICON/PROCICON`）。
左侧菜单支持三种视图：**全部(All) / 分组(Group) / 标签(Tag)**（`SysEnum.LeftMenuType`）。

### 3. 对象浏览 + 元数据能力（Exporter.cs 抽象接口 = 每个库都必须实现）
| 能力 | 方法 | 说明 |
|---|---|---|
| 库列表 | `GetDatabases()` | |
| 服务器信息 | `GetInfo()` | Redis 专用 |
| 列信息 | `GetColumnInfoById(objectId)` | |
| 对象脚本 | `GetScriptInfoById(objectId, objectType)` | 建表/建视图等 DDL |
| **改对象备注** | `UpdateObjectRemark(name, remark, objectType)` | 在线编辑表/视图注释 |
| **改列备注** | `UpdateColumnRemark(column, remark)` | 在线编辑列注释 |
| 数据预览 | `GetDataTable(sql, orderBy, page, size)` | SQL 脚本窗口用 |
| **执行 SQL** | `ExecuteSQL(sql)` | SQL 脚本窗口用 |
| 生成 SQL | `CreateTableSql/SelectSql/InsertSql/UpdateSql/DeleteSql/AddColumnSql/AlterColumnSql/DropColumnSql` | 对象详情里展示/复制 |

### 4. 文档导出（DocType 枚举：8 种格式）
**chm / html / word / excel / pdf / md / xml / json**
- Excel 支持 **单Sheet(所有表一个页) / 多Sheet(每表一页)** 双模式（ExportDoc.xaml 文案已核实）
- 提示："Excel、Word、Html 文档仅支持导出表，其他文档类型都支持导出" → 即 **CHM/PDF/XML/JSON/MD 支持导视图**

screw-web 现状：html/word/md/excel 4 种 → **缺 chm/pdf/xml/json**。

### 5. 代码生成（GenCode）
- 支持 **C# 与 Java** 两种语言（`CsharpLang.cs` / `JavaLang.cs`）
- C# 模板（Csharp.tmpl）：含 SqlSugar 特性（`[SugarTable]` / `[SugarColumn]`，主键/自增标识），命名空间 + 类注释
- Java 模板（Java.tmpl）：**bejson 风格纯 POJO**，字段 + getter/setter + 类注释（非 Lombok）
- 基于 JNTemplate 引擎 + `.tmpl` 模板文件

screw-web 现状：手写 Java/C# + Lombok 开关 → 模板引擎化、与 SmartSQL 输出结构对齐是差距。

### 6. 设置项（SettingWindow.xaml）
- 主窗口**多标签展示**
- 对象名**模糊搜索**
- 左侧表、视图菜单**包含表备注说明**
- 修改备注时**是否弹出保存提示对话框**

### 7. 工具箱（ToolBox，27 个工具，源码 case 全量）
SQL格式化 / Json格式化 / MD5文本加密 / 密码生成器 / UUID生成器 / Unix时间戳转换 / Base64编码解码 / 汉字转拼音 / 文本两端插入字符 / 字数统计 / JWT解码器 / 时间差计算 / 二维码生成 / 图片转base64 / RGB颜色转换 / 文字物语 / Url编码解码 / MimeType对照表 / Unicode中文互转 / Hex编码解码 / Linux命令大全 / 繁简转换 / 人民币大写转换 / Ico图标生成 / Icon图标 / Json转C#实体 / 条形码生成 / Json转换Excel

### 8. 脚本窗口（ScriptWindow）
执行任意 SQL、分页取数据、查看执行结果。

### 9. 其它
- 分组管理 / 标签管理（对象收藏标签）
- 导入备注（ImportMark）：批量导入表/列注释
- 关于 / 更新 / 联系（桌面特有，不还原）

---

## 二、逐项对照：现状 → 差距 → 批次

| # | SmartSQL 功能 | screw-web 现状 | 差距 | 批次 | 验收标准 |
|---|---|---|---|---|---|
| 1 | 数据库支持(7类) | 16 类(超集) | 缺 **Redis** | G | 可连 Redis、浏览 Key |
| 2 | 对象树(表/视图/存储过程) | 仅表 | **缺视图/存储过程浏览** | B | 树含表/视图/过程，可看列与 DDL |
| 3 | 改表/视图/列备注 | ❌ | **缺在线编辑注释** | C | 双击备注可改，MySQL 落库 |
| 4 | 对象 DDL 脚本 | ❌ | 缺 | B | 详情显示建表/建视图 SQL |
| 5 | 数据预览 + 执行SQL | ❌ | 缺脚本窗口 | F | 可跑 SELECT/DDL，分页看结果 |
| 6 | 导出 8 格式 | html/word/md/excel | **缺 chm/pdf/xml/json** | D | 可选 8 格式；md/xml/json 支持视图 |
| 7 | Excel 单/多Sheet | 已实现(未提交) | — | A | 收尾提交 |
| 8 | 代码生成 C#/Java | Java/C# 手写 | 模板化对齐 | A/B 后评估 | 输出同 SmartSQL 结构 |
| 9 | 设置4项 | ❌ | 缺设置页 | H | 多标签/模糊搜/备注显示/保存提示 |
| 10 | 工具箱 27 工具 | ❌ | 缺 | H | 核心工具可用 |
| 11 | 分组/标签 | 连接分组有 | 缺对象级标签 | H | 对象可收藏/打标签 |
| 12 | 导入备注 | ❌ | 缺 | E | Excel/JSON 导入表列注释 |
| 13 | 在线文档类(WORD null防护等) | 进行中(未提交) | — | A | 收尾提交 |

## 三、批次执行顺序（依赖 + 价值排序）

- **批次 A（收尾，先做）**：screw-core 在途改动（`lengthName`/`databaseType`/Word 模板 null 防护/Excel 单多Sheet）→ `mvn install` → 提交。
  理由：所有后续批次基于干净主干，且这批已实质完成。
- **批次 B（对象浏览扩展）**：core 层 `includeView` 已埋点但未通 Web → 补视图/存储过程查询 + DDL 脚本 API + 前端对象树分组。
- **批次 C（注释在线编辑）**：新增 `remark` 编辑 API（MySQL `ALTER TABLE/MODIFY COLUMN` 起步，按方言扩展）+ 前端双击编辑。
- **批次 D（导出补齐）**：新增 **XML / JSON** 导出器（DataModel → xml/json，含视图）；评估 PDF（itext/openhtmltopdf）、CHM（放弃，用 JSON+MD 组合替代，在 UI 注明）。
- **批次 E（注释导入导出）**：Excel/JSON 模板导入 → 批量写回。
- **批次 F（脚本窗口）**：`/api/sql/execute` + 分页查询；前端 SQL 编辑器 + 结果表格。
- **批次 G（Redis 客户端）**：jedis/lettuce 连 Redis，Key 浏览 + 类型/值查看。
- **批次 H（工具箱+设置+分组标签）**：前端工具集 + 设置持久化 + 对象标签收藏。

## 四、关键设计约束（沿用 GAP-ANALYSIS）
- 所有能力打包进单 jar（screw-web），前端 dist 构建后拷入 static。
- 注释编辑不可用方言降级为前端本地保存提示。
- Java/C# 生成模板放 resources，便于对 SmartSQL `.tmpl` 逐字对齐。

# F1c — views/screw/* 前端页面设计文档

> 阶段：F1c（设计）→ F1d（实现）
> 日期：2026-09-03
> 范围：将 `screw-web-ui`（Naive UI + axios + 独立路由）功能迁移进 `jimuqu-admin-ui`（Soybean/Vben + alova + 后端驱动菜单）的 `src/views/screw/*` 页面。
> 关联：`screw-web/docs/GAP-ANALYSIS.md`（能力差距）、`DataSeeder.java`（已建 5 个 screw 菜单）、`jimuqu-admin-ui/src/router/access.ts`（动态路由映射）。

---

## 0. 结论速览（TL;DR）

| 项 | 结论 |
|---|---|
| 后端 | `screw-web` 已实现全部所需 API（分组/连接/元数据/文档/代码/对比），**零后端改动**（文档生成后缀异常除外，见 §7 风险） |
| 前端基座 | `jimuqu-admin-ui`（Soybean/Vben，Vue3.5 + TS + antdv-next + VxeGrid） |
| HTTP | `alovaInstance`（`@/utils/http`），`baseURL=/dev-api`（来自 `useAppConfig`）；vite 代理去掉 `/dev-api` 前缀 → 后端收 `/api/...` |
| API 路径 | **前端统一写 `/api/...` 全路径**（`/api/group`、`/api/connection/...` 等） |
| 页面落位 | `src/views/screw/{connection,document,metadata,code,compare}/index.vue`（已由 DataSeeder component 字段锁定） |
| 组件范式 | 参照 `system/post`（列表+抽屉+搜索表单）、`system/user`（树+表格） |
| 下载 | 文档生成走 `responseType:'blob'`（与 jimuqu `commonExport` 同机制）；文件名从响应头 `Content-Disposition` 解析 |
| 兼容 | screw-web `ApiResponse{code,msg,data}` 与 jimuqu 拦截器契约**逐字段兼容**（`code===200` 成功，拦截器自动解包返回 `data`），错误 `msg` 自动弹提示 |

---

## 1. 背景与目标

### 1.1 背景

- `screw-web`（Spring Boot 3.5，端口 8760）是一个数据库表结构文档生成平台，其前端 `screw-web-ui`（Vue3 + Naive UI + axios，独立 Vite 应用）即将被 `jimuqu-admin-ui` 取代——后者是一个 Soybean/Vben5 风格的管理后台，已具备登录鉴权、动态菜单、VxeGrid 表格、表单抽屉等完整体系。
- `DataSeeder` 已向 `screw-web` H2 库写入 5 个菜单（父菜单 `Screw` + 5 子项），其 `component` 字段指向 `screw/{module}/index`——经 `jimuqu-admin-ui` 动态路由映射到 `src/views/screw/{module}/index.vue`。

### 1.2 目标

在 `jimuqu-admin-ui/src/views/screw/*` 下新建 5 个页面，**保留 screw-web-ui 的全部业务语义**，但完全遵循 jimuqu 的组件/HTTP/路由/文件组织范式，使 screw 能力成为 jimuqu 平台内的一个普通功能域。

### 1.3 非目标

- 不改动 `screw-web` 后端 API 契约（除非必要缺陷，见 §7）。
- 不引入 Naive UI / 旧路由 / 旧 HTTP 层。
- 不实现 screw-web-ui 里超出后端能力的前端模拟功能。

---

## 2. 技术事实基线（F1b 已核实）

### 2.1 HTTP 层与代理

- `.env.development`：`VITE_GLOB_API_URL=/dev-api`，`VITE_PORT=5666`。
- `src/utils/http/index.ts`：`alovaInstance` 的 `baseURL = apiURL`（`useAppConfig` 注入，即 `/dev-api`）。
- 请求拦截器：自动附加 token、语言、clientId；`POST/PUT` 带 `encrypt:true` 时用 SM4/RSA 加密 body。
- 响应拦截器（`responded.onSuccess`）：
  - 后端返回 `{code, msg, data}`；`code===200` → 解包**直接返回 `data`**（`getWithMsg/postWithMsg/...` 额外弹成功 msg）。
  - `code!==200` → 弹 `msg` 错误并抛 `BusinessException`；`code===401` → 触发登出。
  - `isTransformResponse:false` + `responseType:'blob'` → 非 JSON 直接返回 blob（下载用）；若 blob 实为 JSON（业务错误）则解析并按业务码处理。
- vite 代理：`/dev-api` → `http://127.0.0.1:5320`，`rewrite` 移除 `/dev-api` 前缀（`build/vite/config/api-proxy.ts`）。

> ⚠️ **开发代理目标注意**：当前 vite 代理 target 是 `5320`（jimuqu 后端），而 screw-web 在 `8760`。F1d/联调阶段需将 target 指向 `8760`（B3 阶段处理），或让 screw-web 与 jimuqu 后端同端口聚合。**前端 API 路径书写不受此影响**（始终 `/api/...`）。

### 2.2 动态路由映射

- `src/router/access.ts`：`const pageMap = import.meta.glob('../views/**/*.vue')`。
- 后端菜单 `component: 'screw/connection/index'` → `backMenuToVbenMenu` 加前缀 `/` → 解析为 `src/views/screw/connection/index.vue`。
- **因此新建页面文件必须精确落位于上述路径**，否则菜单点击白屏。

### 2.3 screw-web 后端 API 响应结构

`ApiResponse<T>{ code:int, msg:string, data:T }`；`ok()` → `code=200, msg="success"`；错误 `error(code,msg)`。与 jimuqu 拦截器契约一致（见 §2.1）。

---

## 3. API 契约（前端模块定义）

**统一出口**：新建 `src/api/screw/index.ts`（或按模块拆分后聚合，遵循 jimuqu `src/api/index.ts` 模式）。

> 所有请求路径带 `/api` 前缀；无分页；除 blob 下载外全部走 `isTransformResponse` 默认（true），由拦截器解包 `data`。
> 文档生成/代码生成/元数据加载是**重量级同步操作**，UI 必须给 loading；数据量可能大（元数据整库表+列）。

### 3.1 类型定义（`src/api/screw/model.d.ts`）

以 `screw-web-ui/src/types/index.ts` 与后端 Java record/entity/DTO 为准：

```ts
/** 统一后端响应（jimuqu 拦截器已解包，仅类型参考；blob/原始响应场景可能需要） */
export interface ApiResponse<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

/** 数据库类型选项（GET /api/connection/db-types） */
export interface DbTypeInfo {
  code: string;
  name: string;
  driver: string;
  defaultPort: number;
  schemaSupported: boolean;
}

/** 连接分组（ConnectionGroup） */
export interface ConnectionGroup {
  id?: number;
  name: string;
  sort?: number;
  remark?: string;
  createTime?: string;
}

/** 数据库连接实体（DbConnection）——注意：密码不下发，password 恒为空 */
export interface DbConnection {
  id: number;
  groupId: number;
  name: string;
  dbType: string;
  host: string;
  port?: number | null;
  database: string;
  schemaName?: string;
  username?: string;
  passwordEnc?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 保存连接请求（ConnectionSaveRequest）——password 明文；更新时留空=不改密码 */
export interface ConnectionSaveRequest {
  id?: number;
  groupId: number;
  name: string;
  dbType: string;
  host: string;
  port?: number | null;
  database: string;
  schemaName?: string;
  username?: string;
  password?: string;
  remark?: string;
}

/** 列模型（ColumnModel） */
export interface ColumnModel {
  columnName: string;
  columnType?: string;
  typeName?: string;
  columnLength?: string;
  decimalDigits?: string;
  nullable?: string;
  columnDef?: string;
  primaryKey?: string;
  remarks?: string;
  ordinalPosition?: number;
}

/** 表模型（TableModel） */
export interface TableModel {
  tableName: string;
  tableType?: string;
  remarks?: string;
  columns?: ColumnModel[];
}

/** 元数据模型（DataModel） */
export interface DataModel {
  dbType?: string;
  database?: string;
  tables?: TableModel[];
}

/** 文档格式 */
export type DocFormat = 'HTML' | 'MD' | 'WORD' | 'EXCEL';

/** 文档生成请求（DocumentGenerateRequest） */
export interface DocumentGenerateRequest {
  connectionId: number;
  format: DocFormat;
  title?: string;
  version?: string;
  description?: string;
  tables: string[]; // 空数组 = 全部表
  excelSheetMode?: 'SINGLE' | 'PER_TABLE'; // 仅 EXCEL 有效；缺省 PER_TABLE
}

/** 代码生成请求（CodeGenerateRequest） */
export interface CodeGenerateRequest {
  connectionId: number;
  language: 'JAVA' | 'CSHARP';
  packageName?: string; // JAVA
  namespace?: string;   // CSHARP
  lombok?: boolean;
  swagger?: boolean;
  tables: string[];
}

/** DB 对比请求（DbCompareRequest） */
export interface DbCompareRequest {
  sourceId: number;
  targetId: number;
  diffOnly?: boolean;
}

/** 列差异（DbCompareService.ColumnDiff） */
export interface ColumnDiff {
  table: string;
  column: string;
  source: string; // 描述串；缺失侧为 "∅"
  target: string;
  type:
    | 'COLUMN_MISSING_IN_SOURCE'
    | 'COLUMN_MISSING_IN_TARGET'
    | 'COLUMN_DIFF'
    | 'PK_DIFF';
}

/** 表差异（DbCompareService.TableDiff） */
export interface TableDiff {
  table: string;
  type: string; // "REMARK"（表注释差异）
  detail: string; // "源注释 → 目标注释"
}

/** 对比结果（DbCompareService.CompareResult） */
export interface CompareResult {
  onlySourceTables: string[];
  onlyTargetTables: string[];
  tableDiffs: TableDiff[];
  columnDiffs: ColumnDiff[];
  summary: {
    onlySourceTables: number;
    onlyTargetTables: number;
    tableDiffs: number;
    columnDiffs: number;
  };
}

/** 代码生成结果：表名 → 代码内容（CodeGenerateController） */
export type CodeGenerateResult = Record<string, string>;
```

### 3.2 API 函数（`src/api/screw/index.ts`）

```ts
import type {
  CodeGenerateRequest, CodeGenerateResult, CompareResult, ConnectionGroup,
  ConnectionSaveRequest, DataModel, DbCompareRequest, DbConnection, DbTypeInfo,
  DocFormat, DocumentGenerateRequest,
} from './model';

import { alovaInstance } from '@/utils/http';

/** 分组 */
export const groupApi = {
  list: () => alovaInstance.get<ConnectionGroup[]>('/api/group'),
  create: (g: Partial<ConnectionGroup>) =>
    alovaInstance.post<ConnectionGroup>('/api/group', g),
  update: (id: number, g: Partial<ConnectionGroup>) =>
    alovaInstance.put<ConnectionGroup>(`/api/group/${id}`, g),
  remove: (id: number) => alovaInstance.delete<void>(`/api/group/${id}`),
};

/** 连接 */
export const connectionApi = {
  dbTypes: () => alovaInstance.get<DbTypeInfo[]>('/api/connection/db-types'),
  list: (groupId?: number) =>
    alovaInstance.get<DbConnection[]>('/api/connection', {
      params: groupId != null ? { groupId } : {},
    }),
  save: (req: ConnectionSaveRequest) =>
    alovaInstance.post<DbConnection>('/api/connection', req),
  update: (id: number, req: ConnectionSaveRequest) =>
    alovaInstance.put<DbConnection>(`/api/connection/${id}`, req),
  remove: (id: number) => alovaInstance.delete<void>(`/api/connection/${id}`),
  test: (req: ConnectionSaveRequest) =>
    alovaInstance.post<string>('/api/connection/test', req),
};

/** 元数据 */
export const metadataApi = {
  get: (connectionId: number) =>
    alovaInstance.get<DataModel>(`/api/metadata/${connectionId}`),
};

/** 文档生成（blob 下载） */
export const documentApi = {
  formats: () => alovaInstance.get<DocFormat[]>('/api/document/formats'),
  /** 返回 { blob, filename }——filename 取自响应头 Content-Disposition */
  generate: (req: DocumentGenerateRequest) =>
    alovaInstance.post<Blob>('/api/document/generate', req, {
      isTransformResponse: false,
      responseType: 'blob',
    }),
};

/** 代码生成（返回 表名→代码 map，非文件下载） */
export const codeApi = {
  generate: (req: CodeGenerateRequest) =>
    alovaInstance.post<CodeGenerateResult>('/api/code/generate', req),
};

/** DB 对比 */
export const compareApi = {
  compare: (req: DbCompareRequest) =>
    alovaInstance.post<CompareResult>('/api/compare', req),
};
```

> **要点**：
> - screw-web 全部控制器 `@RequestMapping("/api/...")`，故前端写全 `/api/...`；vite 只剥 `/dev-api`。
> - 这些请求**不带 `encrypt:true`**（与 `system/post` 等 CRUD 一致——加密仅在显式开启的登录等接口）。若 jimuqu 代理或网关要求统一加密，F1d 需按 `src/api/core/auth.ts` 的 `encrypt:true` 补齐并同步后端 RSA/AES（screw-web `application.yml` 已配 AES/RSA 且 RSA 与 jimuqu `.env` 配对——**说明后端已具备解密能力，前端按需开启即可**）。
> - 代码生成（`/api/code/generate`）返回 JSON map（表名→代码文本），**非 blob**——与文档生成不同，不要误用 blob。
> - `test` 返回 `string`（成功 `"连接成功，数据库产品: X Y"`），失败抛 `BizException`（HTTP 200 + `code!=200`，msg=失败原因）→ jimuqu 拦截器自动弹错误。**前端调用后仅需在成功时 message.success(data)**（旧 UI 弹窗展示产品信息）。

### 3.3 文档下载文件名解析

`screw-web` DocumentController 响应头：

```
Content-Disposition: attachment; filename*=UTF-8''<urlencoded 文件名>
```

前端解析（jimuqu 内可放 `src/utils/file/export.ts` 扩展，或页面内工具函数）：

```ts
function parseFilenameFromDisposition(disposition?: string): string {
  if (!disposition) return `database_${Date.now()}`;
  const star = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (star) return decodeURIComponent(star[1]);
  const plain = disposition.match(/filename="?([^";]+)"?/i);
  return plain ? plain[1] : `database_${Date.now()}`;
}
```

> 后端生成文件名形如 `<title 或 database>_<format>_<timestamp>_<uuid>.<ext>`（DocumentService）；前端也可在无头时自行拼名兜底。

---

## 4. 页面设计（5 个 + 共享组件）

### 4.0 共享：连接选择器（`src/components/screw/connection-select.vue`）

多个页面（元数据/文档/代码/对比）都需要"选连接"。抽象为共享组件：

- **Props**：`modelValue?: number`、`groupId?: number`、`allowEmpty?: boolean`、`disabled?: boolean`
- **行为**：挂载时拉 `groupApi.list()` + `connectionApi.list()`（按需 groupId 过滤）；用 `FormSelect`/级联（分组 → 连接）；展示 `name (dbType@host:port/database)`。
- **空态**：无连接时展示"请先到连接管理创建连接"，并提供跳转链接。
- **依赖**：无需依赖页面，独立目录 `src/components/screw/`。

> 由 F1d 决定是抽共享组件还是每页内联下拉（若仅 2 页用则内联更省）。设计上建议共享，因 4 个页面语义完全一致。

### 4.1 连接管理 `screw/connection/index.vue`（对应旧 ConnectionManage.vue）

**布局**：左右分栏（仿 `system/user` 的部门树 + 用户表）。
- 左：分组列表（树/列表）：分组名 + 连接数；顶部"新增分组"；行内"重命名/删除"（Popconfirm）。
- 右：当前分组下的连接 VxeGrid。

**左栏（分组）**：
- 加载：`groupApi.list()`。
- 新建分组：小弹窗/抽屉（名称必填、sort 可选）。
- 重命名：行内或弹窗。
- 删除：Popconfirm（后端会级联删连接？——**F1d 需确认 GroupController.delete 行为**；若后端不级联，前端先提示"该组下 N 条连接将无法通过分组访问，仍删除？"）。
- 选中分组 → 右侧 `connectionApi.list(groupId)`。
- 提供"全部连接"虚拟分组（groupId=undefined）与"未分组"（如后端支持 groupId 过滤为 null）。

**右栏（连接 VxeGrid 列）**：

| field | 列标题 | 渲染 |
|---|---|---|
| name | 连接名称 | — |
| dbType | 数据库类型 | Tag/文字映射 name |
| host | 主机 | — |
| port | 端口 | — |
| database | 库名 | — |
| schemaName | Schema | 空显示 `-` |
| username | 用户名 | 空显示 `-` |
| remark | 备注 | ellipsis |
| createTime | 创建时间 | — |
| （操作） | — | 编辑 / 测试 / 删除 |

- toolbar 按钮：`新增连接`、`批量删除`（checkbox 选中后）。
- **测试**：不弹表单，直接 `connectionApi.test({ ...row 转 ConnectionSaveRequest, password:'' })` → 成功 message.success(返回串)；失败已由拦截器弹错。测试需 loading（行级或全局）。
- **新增/编辑（抽屉，仿 post-drawer.vue）**：见 §4.1.1。

**4.1.1 连接抽屉（同目录 `connection-drawer.vue`）**
- 字段：所属分组（FormTreeSelect 或下拉，来自 groupApi.list）、名称*、数据库类型*（下拉来自 `connectionApi.dbTypes()`，默认 MySQL）、主机*、端口（数字，选类型后按 `defaultPort` 预填，用户可改）、数据库*、Schema（仅当所选 dbType `schemaSupported` 时显示）、用户名、密码、备注。
- 密码交互语义（**关键**，与后端一致）：
  - 新增：密码必填（连接测试需要）。
  - 编辑：密码框**留空 = 不修改**（后端 `ConnectionService.save` 仅非空才更新 passwordEnc）。放 placeholder "留空则不修改密码"。
  - 列表数据**永不下发密码**（entity 只有 passwordEnc），编辑回显时密码框恒空。
- 提交：新增 `connectionApi.save`，编辑 `connectionApi.update(id, req)`；成功后 emit reload。
- 抽屉内可提供"保存并测试"（先 save 拿 id，再 test）。
- 校验：`FormSelect`/`FormInput` 必填规则 + `dbType/host/port/database` 联动（如 MySQL port 默认 3306）。

### 4.2 表结构浏览 `screw/metadata/index.vue`（对应 ObjectBrowse/WorkbenchHome/TableStructure 合并）

**语义合并说明**：旧 UI 中 ObjectBrowse（树）、WorkbenchHome（引导页）、TableStructure（表格 Tab）是同一功能的碎片。新页面整合为单页三态。

**布局**：
- 顶部工具条：连接选择器（`connection-select`）+ 加载元数据按钮 + 搜索框（表名/注释过滤）+ 全选/清空（供文档/代码联用）。
- 左侧：表树（`a-tree` / 简单列表，分组：表名 + 注释副行）；选中表 → 右侧列结构。
- 右侧：选中表的列 VxeGrid（见下）；支持搜索列名。

**列结构表格（ColumnModel）**：

| field | 列标题 |
|---|---|
| columnName | 列名 |
| columnType/typeName + columnLength(decimalDigits) | 类型(长度) |
| primaryKey | 主键（PRI/YES → Tag） |
| nullable | 可空（NO → Tag"NOT NULL"） |
| columnDef | 默认值 |
| remarks | 注释 |

**加载语义**：
- `metadataApi.get(connectionId)` 一次拉全 `DataModel`（表+列），前端内存过滤——后端无分页。
- 大库性能：loading 遮罩 + 提示；可选加"仅表名"模式（后端不支持，跳过）。
- 切换连接 → 清空并重新加载。

**操作**：
- 表选中集合（多选，右上角统计）→ 可触发"导出文档 / 生成代码"（跳转对应页面并携带表集合，或直接弹 §4.4/§4.5 对话框）。
- 表行内快捷：`导出该表文档`、`生成该表代码`、`加入对比`。
- 空态引导：未选连接 → 中央引导（仿 WorkbenchHome："请选择连接，双击表查看结构"）。

### 4.3 文档生成 `screw/document/index.vue`（对应 ExportDocDialog）

**语义**：独立页面（不做成模态 Dialog——菜单直接指向），但内部步骤沿用旧对话框：
1. **选库与表**：连接选择器 → 加载元数据 → 表多选（可全选；带搜索）。
2. **格式**：`RadioGroup` HTML / MD / WORD / EXCEL（来自 `documentApi.formats()`，与后端 `supportedFormats` 对齐）。
3. **文档信息**：标题（默认取库名/连接名）、版本（默认 `1.0.0`）、描述（多行）。
4. **Excel 专属**：Sheet 模式单选 `PER_TABLE`（每表一页）/ `SINGLE`（单页，表目录+全列），默认 PER_TABLE。仅当 format=EXCEL 显示。
5. **生成**：`documentApi.generate(req)` → blob → 解析 Content-Disposition 文件名 → `<a download>` 触发保存。

**细节**：
- 表选择 UI 用表格（checkbox）而非树（多选更直观），列：表名、注释。
- `tables` 传**所选表名数组**；不选 = 空数组 = 全表（与后端约定一致，但 UI 上引导显式"全选"以免歧义）。
- 生成按钮 loading；下载完成后 message.success。
- 若 jimuqu 有 `useBlobExport`/`commonExport`（`@/utils/file/export`），复用其保存逻辑（该文件已见 `useBlobExport` 引用自 `system/post/index.vue`，F1d 读其签名适配——但注意 commonExport 是 FORM_URLENCODED POST 且剥 JSON，此处需 JSON body + blob；**以 documentApi.generate 的 alova blob 调用为准，导出仅复用"保存文件"部分**）。

### 4.4 代码生成 `screw/code/index.vue`（对应 GenCodeDialog）

**语义**：
1. **选库与表**：连接选择器 + 表多选（同 §4.3）。
2. **语言**：`RadioGroup` Java / C#。
3. **选项**（语言联动）：
   - Java：包名 `packageName`（默认如 `com.example.entity`）、`Lombok` Checkbox、`Swagger` Checkbox。
   - C#：命名空间 `namespace`（默认如 `YourNamespace.Entities`）。
4. **生成**：`codeApi.generate(req)` → 返回 `Record<tableName, codeText>`。
5. **结果区**：左侧表名列表（结果 map 的 key），右侧代码只读高亮（无编辑器依赖时用 `<pre>`+简单高亮；F1d 评估是否引入轻量高亮库，倾向不引入——纯文本 + 行号即可，或复用 antdv 的 `Typography.Paragraph code`）。
6. **下载**：
   - 下载当前表：`Blob([code], {type:'text/plain'})` + 文件名 `<表名><.java/.cs>`。
   - 下载全部：拼接所有表代码为一个大 txt（旧 UI 行为：`entities_<lang>_<ts>.txt`）。

**语言映射**：JAVA → `.java`；CSHARP → `.cs`。

### 4.5 库表比较 `screw/compare/index.vue`（对应 DbCompareTab）

**语义**：
1. **源/目标选择**：两个连接选择器（sourceId / targetId）；相同连接不可选（校验 sourceId !== targetId）；可"交换"按钮。
2. **对比**：`compareApi.compare({sourceId, targetId, diffOnly})` → CompareResult。
3. **diffOnly 开关**：默认关（全量差异）；开则只显示差异项（过滤相同表/列）。后端 diffOnly 实际过滤逻辑：**F1d 需核对 DbCompareService——目前服务实现未用 diffOnly 参数，仅对比有差异才记录；前端把 diffOnly 作为 UI 过滤开关即可**（若后端无差异，天然只显示 onlySource/onlyTarget/diff 列表）。
4. **结果展示**：
   - 顶部 summary 卡片/统计条：`仅源库表 N / 仅目标库表 N / 表级差异 N / 列级差异 N`。
   - Tabs：
     - 表级差异（TableDiff）：表名、类型（REMARK）、详情（源→目标）。
     - 列级差异（ColumnDiff）：表、列、源描述、目标描述、类型 Tag（颜色区分 MISSING_IN_SOURCE/TARGET/DIFF/PK_DIFF）。
     - 仅源库表（列表）。
     - 仅目标库表（列表）。
5. 结果可导出（选做 P2）：前端序列化 JSON 下载，不做后端导出。

---

## 5. 路由与菜单

- 菜单已由 `DataSeeder` 写入（无需新增）；若 H2 已重置需重新种子。
- 页面文件落位（**必须与 component 字符串精确匹配**）：

| 菜单 component | 文件 |
|---|---|
| `screw/connection/index` | `src/views/screw/connection/index.vue` |
| `screw/document/index` | `src/views/screw/document/index.vue` |
| `screw/metadata/index` | `src/views/screw/metadata/index.vue` |
| `screw/code/index` | `src/views/screw/code/index.vue` |
| `screw/compare/index` | `src/views/screw/compare/index.vue` |

- 父子：Screw 父菜单（可能为目录，无 component）→ 5 子菜单。
- 路径（DataSeeder 里定义）：`/screw/connection` 等；jimuqu 按 `path` 生成前端路由。
- 权限：jimuqu 菜单默认按登录用户可见（admin 全量）；screw-web 无独立权限模型，**跟随 jimuqu 登录态即可**（页面不额外做按钮级权限）。

---

## 6. 认证与数据流

1. 用户经 jimuqu 登录（`/auth/login`，admin/admin123 由 DataSeeder 建）拿到 token。
2. 拉菜单 `/system/menu/getRouters`（含 screw 5 项）→ 动态路由注册。
3. 页面内所有 screw 请求走 `alovaInstance`，拦截器自动带 token；screw-web 后端需校验 token 还是裸放行——**B2b（后端安全）阶段定**；设计上：若 B2b 让 screw-web 校验 jimuqu 签发的 token（共享密钥/JWT），前端无感；若仍裸放行，前端也不受影响（token 头多余无害）。
4. 401 → 拦截器登出回登录页。

---

## 7. 风险与待办（F1d/后续阶段）

| # | 风险/待确认 | 影响 | 处置 |
|---|---|---|---|
| 1 | vite 代理 target=5320 非 screw-web 8760 | 本地联调不通 | B3 改代理 target 指向 8760（或聚合端口），F1d 用 5320→8760 假定 |
| 2 | `GroupController.delete` 是否级联删连接 | 分组删除后连接成孤儿 | F1d 读 GroupController/GroupService 确认，前端提示文案适配 |
| 3 | `diffOnly` 后端是否真过滤 | 对比结果量 | F1d 读 DbCompareService（当前实现似未用），前端做过滤开关 |
| 4 | screw-web 无登录鉴权 | screw API 裸奔 | B2b 处理（jimuqu token 校验/白名单） |
| 5 | 文档后缀异常 | EXCEL 文件名后缀 | 后端 DocumentService（`.xlsx` 判定）已按后缀给 contentType；前端以响应头为准；如遇 `.html` 双写 bug 归 B2 |
| 6 | screw-core 仅表元数据（无视图/索引/触发器） | 表树无视图索引 | 接受现状，不设计视图/索引节点（GAP-ANALYSIS 批次 1 才补） |
| 7 | 大库元数据全量加载慢 | 体验 | loading + 异步加载表树（逐表展开拉列是后端不支持的前端优化，暂不做） |
| 8 | jimuqu 是否需要 encrypt | 请求体加密 | 默认不带；如网关强制，参考 auth.ts 加 `encrypt:true`（后端已配 AES/RSA） |

---

## 8. 验收标准（F1d 完成判定）

1. `pnpm dev` 起 jimuqu-admin-ui，代理指向 8760；admin/admin123 登录。
2. 菜单出现 Screw → 5 子项，均能打开对应页面（无白屏、无控制台路由错误）。
3. **连接管理**：分组增删改；连接增删改查；dbType 联动默认端口与 schema 显隐；新增必填校验；测试成功弹"连接成功，数据库产品…"、失败弹后端 msg；编辑留空密码不覆盖；列表无密码明文。
4. **表结构浏览**：选连接加载表树；点表看列（类型/主键/可空/默认/注释）；搜索表/列；空态引导。
5. **文档生成**：4 格式下拉来自后端；选表+元信息+Excel sheet 模式；生成下载成功，文件名来自响应头且中文不乱码；MD/HTML 内容正确。
6. **代码生成**：Java/C# 切换联动包名/命名空间与 lombok/swagger；生成后左表右码；下载当前/全部。
7. **库表比较**：源≠目标校验；交换；summary 统计与 4 个明细 Tab 正确；空结果态。
8. 全流程无 Naive UI 残留、无 `/api` 之外裸路径请求（Network 面板所有 screw 请求路径为 `/dev-api/api/...`）。
9. TypeScript 通过 `vue-tsc`/lint（jimuqu 现有脚本）。

---

## 9. F1d 实施顺序建议

1. `src/api/screw/model.d.ts` + `index.ts`（契约先行，可先用 curl 桩测通）。
2. 连接管理页（分组+连接+抽屉）——被其他 4 页依赖（连接数据源）。
3. 连接选择器共享组件。
4. 表结构浏览（元数据）。
5. 文档生成、代码生成（共用表选择模式，可并行）。
6. 库表比较。
7. 联调：代理 target、token、全流程走查。

> 每完成一页即按 §8 对应条目自测，不积压到最后。

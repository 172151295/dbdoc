/**
 * @description: screw 数据库文档生成平台相关接口 model
 * 对应 screw-web 后端 entity / DTO / Java record
 */

/** 统一后端响应（拦截器已解包 data，此处仅供类型参考） */
export interface ApiResponse<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

/** 数据库类型信息（GET /api/connection/db-types） */
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

/** 数据库连接实体（DbConnection）——密码不下发，passwordEnc 恒有值但前端不回显 */
export interface DbConnection {
  id: number;
  groupId: number;
  name: string;
  dbType: string;
  host: string;
  port?: null | number;
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
  port?: null | number;
  database: string;
  schemaName?: string;
  username?: string;
  password?: string;
  remark?: string;
}

/** 列模型（ColumnModel）——对应 screw-core ColumnModel */
export interface ColumnModel {
  /** 列索引（String，从 1 开始） */
  ordinalPosition?: string;
  columnName: string;
  /** SQL 数据类型带长度 */
  columnType?: string;
  /** SQL 数据类型名称 */
  typeName?: string;
  columnLength?: string;
  /** 列大小 */
  columnSize?: string;
  /** 长度名（形如 (10)、(10,2)） */
  lengthName?: string;
  decimalDigits?: string;
  /** 可为空（YES/NO） */
  nullable?: string;
  /** 是否主键（PRI/YES/...） */
  primaryKey?: string;
  /** 是否自增 */
  autoIncrement?: string;
  columnDef?: string;
  remarks?: string;
}

/** 表模型（TableModel）——对应 screw-core TableModel */
export interface TableModel {
  tableName: string;
  remarks?: string;
  columns?: ColumnModel[];
  /** 表类型（TABLE / VIEW / SYSTEM TABLE 等），用于区分普通表和视图 */
  tableType?: string;
  deprecated?: boolean;
}

/** 元数据模型（DataModel）——继承 DatabaseModel：database/tables，另有 databaseType 等 */
export interface DataModel {
  database?: string;
  tables?: TableModel[];
  /** 数据库类型（如 MySQL、Oracle、PostgreSql），模板条件渲染用 */
  databaseType?: string;
  title?: string;
  organization?: string;
  organizationUrl?: string;
  version?: string;
  description?: string;
}

/** 轻量级表清单（GET /api/metadata/{id}/tables）——先清单、后按需拉列 */
export interface TableBriefList {
  /** 数据库类型名（如 MySQL、Oracle），供页头展示 */
  databaseType?: string;
  /** 表/视图清单（仅表名/说明/类型，不含列） */
  tables?: TableModel[];
}

/** 文档格式 */
export type DocFormat =
  'DDL' | 'EXCEL' | 'HTML' | 'JSON' | 'MD' | 'PDF' | 'WORD' | 'XML';

/** Excel Sheet 模式 */
export type ExcelSheetMode = 'PER_TABLE' | 'SINGLE';

/** 文档生成请求（DocumentGenerateRequest） */
export interface DocumentGenerateRequest {
  connectionId: number;
  format: DocFormat;
  title?: string;
  version?: string;
  description?: string;
  /** 空数组 = 全部表 */
  tables: string[];
  /** 仅 EXCEL 有效；缺省 PER_TABLE */
  excelSheetMode?: ExcelSheetMode;
}

/** 文档生成任务状态（DocumentTask） */
export interface DocumentTask {
  taskId: string;
  /** RUNNING / SUCCESS / FAILED */
  status: 'FAILED' | 'RUNNING' | 'SUCCESS';
  /** 当前进度序号（从 1 开始） */
  current: number;
  /** 总对象数 */
  total: number;
  /** 当前正在导出的对象名（表/视图） */
  currentTable?: string;
  /** 失败原因 */
  error?: string;
}

/** 代码语言 */
export type CodeLanguage = 'CSHARP' | 'JAVA';

/** 代码生成请求（CodeGenerateRequest） */
export interface CodeGenerateRequest {
  connectionId: number;
  language: CodeLanguage;
  packageName?: string; // JAVA
  namespace?: string; // CSHARP
  lombok?: boolean;
  swagger?: boolean;
  tables: string[];
}

/** 代码生成结果：表名 → 代码内容 */
export type CodeGenerateResult = Record<string, string>;

/** 表外键关系（子表 → 父表）——对应后端 TableRelation，复合外键每列一行 */
export interface TableRelation {
  /** 外键约束名 */
  fkName?: string;
  /** 子表（外键所在表） */
  childTable?: string;
  /** 子表列 */
  childColumn?: string;
  /** 父表（被引用表） */
  parentTable?: string;
  /** 父表列 */
  parentColumn?: string;
}

/** DB 对比请求（DbCompareRequest） */
export interface DbCompareRequest {
  sourceId: number;
  targetId: number;
  diffOnly?: boolean;
}

/** 列差异（ColumnDiff） */
export interface ColumnDiff {
  table: string;
  column: string;
  source: string; // 描述串；缺失侧为 "∅"
  target: string;
  type:
    | 'COLUMN_DIFF'
    | 'COLUMN_MISSING_IN_SOURCE'
    | 'COLUMN_MISSING_IN_TARGET'
    | 'PK_DIFF';
}

/** 表差异（TableDiff） */
export interface TableDiff {
  table: string;
  /** "REMARK"（表注释差异） */
  type: string;
  /** "源注释 → 目标注释" */
  detail: string;
}

/** 对比汇总 */
export interface CompareSummary {
  onlySourceTables: number;
  onlyTargetTables: number;
  tableDiffs: number;
  columnDiffs: number;
  // ---- 同步脚本统计（后端 CompareSyncService） ----
  syncCreateTables?: number;
  syncAddColumns?: number;
  syncModifyColumns?: number;
  syncTableComments?: number;
  syncColumnComments?: number;
  syncSkippedTargetOnlyTables?: number;
  syncSkippedTargetOnlyColumns?: number;
}

/** 对比结果（CompareResult） */
export interface CompareResult {
  onlySourceTables: string[];
  onlyTargetTables: string[];
  tableDiffs: TableDiff[];
  columnDiffs: ColumnDiff[];
  summary: CompareSummary;
  /** 以源库为准的同步 DDL（在目标库执行）；目标库类型不支持时为 null */
  syncScript?: null | string;
  /** 同步脚本目���方言名（如 "MySQL 系"） */
  syncDialect?: null | string;
  /** 同步脚本生成警告 */
  syncWarnings?: null | string[];
}

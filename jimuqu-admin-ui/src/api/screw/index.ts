import type {
  CodeGenerateRequest,
  CodeGenerateResult,
  CompareResult,
  ConnectionGroup,
  ConnectionSaveRequest,
  DataModel,
  DbCompareRequest,
  DbConnection,
  DbTypeInfo,
  DocFormat,
  DocumentGenerateRequest,
  DocumentTask,
  TableBriefList,
  TableRelation,
} from './model';

import { alovaInstance } from '@/utils/http';

/**
 * @description: screw 数据库文档生成平台接口
 * 注意：
 * 1. screw-web 后端 Controller 统一前缀 /api，故路径必须带 /api（jimuqu 其他模块无此前缀）。
 * 2. 写操作不使用 postWithMsg 等变体：screw 后端成功 msg 恒为 "success"，会弹出英文提示。
 *    页面内按需自行 message.success。
 * 3. 文档下载走 isTransformResponse:false + responseType:'blob'（错误响应会被拦截器解析并按业务码抛错）。
 */

enum Api {
  codeGenerate = '/api/code/generate',
  compare = '/api/compare',
  connectionDbTypes = '/api/connection/db-types',
  connectionRoot = '/api/connection',
  connectionTest = '/api/connection/test',
  documentDownload = '/api/document/download',
  documentFormats = '/api/document/formats',
  documentGenerate = '/api/document/generate',
  documentProgress = '/api/document/progress',
  groupRoot = '/api/group',
  metadataGet = '/api/metadata',
}

/** 分组列表 */
export function groupList() {
  return alovaInstance.get<ConnectionGroup[]>(Api.groupRoot);
}

/** 新建分组 */
export function groupAdd(data: Partial<ConnectionGroup>) {
  return alovaInstance.post<ConnectionGroup>(Api.groupRoot, data);
}

/** 更新分组 */
export function groupUpdate(id: number, data: Partial<ConnectionGroup>) {
  return alovaInstance.put<ConnectionGroup>(`${Api.groupRoot}/${id}`, data);
}

/** 删除分组（后端不级联删除组下连接，仅解除分组归属） */
export function groupRemove(id: number) {
  return alovaInstance.delete<void>(`${Api.groupRoot}/${id}`);
}

/** 支持的数据库类型 */
export function connectionDbTypes() {
  return alovaInstance.get<DbTypeInfo[]>(Api.connectionDbTypes);
}

/** 连接列表（可按分组过滤） */
export function connectionList(groupId?: number) {
  return alovaInstance.get<DbConnection[]>(Api.connectionRoot, {
    params: groupId != null ? { groupId } : {},
  });
}

/** 新增连接 */
export function connectionAdd(data: Partial<ConnectionSaveRequest>) {
  return alovaInstance.post<DbConnection>(Api.connectionRoot, data);
}

/** 更新连接（password 留空 = 不修改） */
export function connectionUpdate(
  id: number,
  data: Partial<ConnectionSaveRequest>,
) {
  return alovaInstance.put<DbConnection>(`${Api.connectionRoot}/${id}`, data);
}

/** 删除连接 */
export function connectionRemove(id: number) {
  return alovaInstance.delete<void>(`${Api.connectionRoot}/${id}`);
}

/** 测试连接（成功返回描述串，失败由后端抛 BizException） */
export function connectionTest(data: Partial<ConnectionSaveRequest>) {
  return alovaInstance.post<string>(Api.connectionTest, data);
}

/** 加载库元数据（表+列）
 * 说明：获取全量表/列元数据较慢（千表大库可达数分钟），默认全局超时 10s 会超时，
 * 这里单独放宽到 5 分钟，避免"选择数据库后无法获取表和视图"。
 */
export function metadataGet(connectionId: number) {
  return alovaInstance.get<DataModel>(`${Api.metadataGet}/${connectionId}`, {
    // 千表大库：每表多次 JDBC 元数据查询，可达数分钟
    timeout: 300_000,
  });
}

/** 外键关系（关系图数据源，单条 SQL 采集；仅 JDBC 回退路径较慢，故同样放宽超时） */
export function metadataRelations(connectionId: number) {
  return alovaInstance.get<TableRelation[]>(
    `${Api.metadataGet}/${connectionId}/relations`,
    {
      timeout: 180_000,
    },
  );
}

/** 轻量级表清单（仅表名/说明/类型，不读列；千表大库秒级返回）
 * 配合 metadataTableColumns 实现"先清单、后按需拉列"，
 * 避免对象导出/代码生成页为一张表清单而全库遍历列信息。
 */
export function metadataTables(connectionId: number) {
  return alovaInstance.get<TableBriefList>(
    `${Api.metadataGet}/${connectionId}/tables`,
    {
      // 仅一次 JDBC getTables，通常秒级；放宽到 2 分钟兜底超大库
      timeout: 120_000,
    },
  );
}

/** 单表列元数据（列+主键标记，按需加载）
 * table 参数须使用表清单下发的精确名称（部分库对大小写敏感）。
 */
export function metadataTableColumns(connectionId: number, table: string) {
  return alovaInstance.get<TableModel>(
    `${Api.metadataGet}/${connectionId}/columns`,
    {
      params: { table },
      // 单表 JDBC 查询，毫秒~秒级
      timeout: 60_000,
    },
  );
}

/** 支持的文档格式 */
export function documentFormats() {
  return alovaInstance.get<DocFormat[]>(Api.documentFormats);
}

/**
 * 提交异步生成任务（返回 taskId 字符串）
 * 说明：screw 后端改为异步生成，避免大海量表时 HTTP 超时；
 * 前端拿到 taskId 后轮询 progress，进度 100% 后调用 download 下载。
 */
export function documentGenerate(data: DocumentGenerateRequest) {
  return alovaInstance.post<string>(Api.documentGenerate, data, {
    timeout: 30_000,
  });
}

/** 查询生成任务进度 */
export function documentProgress(taskId: string) {
  return alovaInstance.get<DocumentTask>(`${Api.documentProgress}/${taskId}`);
}

/** 下载生成结果（返回 Blob；千表大库文件可达数十 MB，放宽超时） */
export function documentDownload(taskId: string) {
  return alovaInstance.get<Blob>(`${Api.documentDownload}/${taskId}`, {
    isTransformResponse: false,
    responseType: 'blob',
    timeout: 300_000,
  });
}

/** 代码生成（返回 表名→代码 map，非文件） */
export function codeGenerate(data: CodeGenerateRequest) {
  return alovaInstance.post<CodeGenerateResult>(Api.codeGenerate, data);
}

/** DB 对比 */
export function compareDo(data: DbCompareRequest) {
  return alovaInstance.post<CompareResult>(Api.compare, data);
}

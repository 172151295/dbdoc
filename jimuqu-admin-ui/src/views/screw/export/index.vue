<script setup lang="ts">
import type {
  ColumnModel,
  DocFormat,
  DocumentGenerateRequest,
  DocumentTask,
  ExcelSheetMode,
  TableBriefList,
  TableModel,
} from '@/api/screw/model';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import {
  documentDownload,
  documentGenerate,
  documentProgress,
  metadataTableColumns,
  metadataTables,
} from '@/api/screw';
import { Page } from '@/components';
import { downloadByData } from '@/utils/file/download';
import {
  Button,
  Checkbox,
  Empty,
  Form,
  FormItem,
  Input,
  InputSearch,
  Modal,
  Pagination,
  Progress,
  Radio,
  RadioGroup,
  Segmented,
  Spin,
  Tag,
} from 'antdv-next';
import dayjs from 'dayjs';

import ConnectionSelect from '../components/connection-select.vue';

// ── 格式选项 ──
const formatOptions: { label: string; value: DocFormat }[] = [
  { label: 'Word', value: 'WORD' },
  { label: 'HTML', value: 'HTML' },
  { label: 'Markdown', value: 'MD' },
  { label: 'Excel', value: 'EXCEL' },
  { label: 'JSON', value: 'JSON' },
  { label: 'XML', value: 'XML' },
  { label: 'PDF', value: 'PDF' },
  { label: 'DDL', value: 'DDL' },
];
const fileTypeToExt: Record<DocFormat, string> = {
  WORD: 'docx',
  HTML: 'html',
  MD: 'md',
  EXCEL: 'xlsx',
  JSON: 'json',
  XML: 'xml',
  PDF: 'pdf',
  DDL: 'sql',
};

// ── 连接 & 元数据 ──
const connectionId = ref<number>();
const loading = ref(false);
/** 轻量表清单（仅表名/说明/类型，千表大库秒级返回） */
const tableBrief = ref<TableBriefList>();
const currentTable = ref<TableModel>();
/** 列信息按需加载缓存（表名 → 列列表）：点中对象才拉取，避免全库列遍历 */
const columnsCache = ref<Record<string, ColumnModel[]>>({});
const columnsLoading = ref(false);
/** 在途列请求序号：快速连点/切换连接时仅最新一次请求生效 */
let columnsRequestSeq = 0;

const tables = computed(() => tableBrief.value?.tables ?? []);
const currentColumns = computed<ColumnModel[]>(
  () => columnsCache.value[currentTable.value?.tableName ?? ''] ?? [],
);

function isView(t: TableModel) {
  return (t.tableType ?? 'TABLE').toUpperCase() === 'VIEW';
}

// 类型筛选 + 搜索
type TypeFilter = 'ALL' | 'TABLE' | 'VIEW';
const typeFilter = ref<TypeFilter>('ALL');
const searchKeyword = ref('');
const tableList = computed(() => tables.value.filter((t) => !isView(t)));
const viewList = computed(() => tables.value.filter((t) => isView(t)));
const tableCount = computed(() => tableList.value.length);
const viewCount = computed(() => viewList.value.length);
const tableNames = computed(() => tableList.value.map((t) => t.tableName));
const viewNames = computed(() => viewList.value.map((t) => t.tableName));
const hasViews = computed(() => viewList.value.length > 0);

const visibleTables = computed<TableModel[]>(() => {
  let list = tables.value;
  if (typeFilter.value !== 'ALL') {
    list = list.filter(
      (t) => (isView(t) ? 'VIEW' : 'TABLE') === typeFilter.value,
    );
  }
  const kw = searchKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter(
      (t) =>
        (t.tableName ?? '').toLowerCase().includes(kw) ||
        (t.remarks ?? '').toLowerCase().includes(kw),
    );
  }
  return list;
});

// ── 分页 ──
const currentPage = ref(1);
const pageSize = ref(50);
const pagedTables = computed<TableModel[]>(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return visibleTables.value.slice(start, start + pageSize.value);
});
watch([typeFilter, searchKeyword, pageSize], () => {
  currentPage.value = 1;
});

// ── 导出表单 ──
const form = ref<{
  description: string;
  excelSheetMode: ExcelSheetMode;
  format: DocFormat;
  tables: string[];
  title: string;
  version: string;
}>({
  format: 'EXCEL',
  title: '',
  version: '',
  description: '',
  excelSheetMode: 'PER_TABLE',
  tables: [],
});

// ── 导出进度 ──
const exporting = ref(false);
const taskId = ref<string>();
const progressTask = ref<DocumentTask>();
let pollTimer: null | ReturnType<typeof setInterval> = null;
let pollFailCount = 0; // 连续轮询失败次数
let pollStartAt = 0; // 轮询开始时间（总时长上限保护）
let lastProgressAt = 0; // 最近一次进度推进时间（无推进超时保护）
let lastProgressCurrent = -1; // 最近一次进度值（用于检测推进）
const POLL_FAIL_LIMIT = 15; // 连续失败上限：后端不可达约 15 秒后停止
const POLL_STALL_MS = 3 * 60 * 1000; // 连续 3 分钟进度无推进判定卡死（千表任务每表可能较慢）
const POLL_MAX_MS = 60 * 60 * 1000; // 任务总时长上限 1 小时（兜底）

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}
onBeforeUnmount(() => stopPoll());

// ── 连接变化：加载轻量表清单（列信息点中时按需拉取） ──
watch(connectionId, async (value) => {
  loading.value = true;
  //作废在途列请求，避免旧连接结果写入新缓存
  columnsRequestSeq += 1;
  columnsLoading.value = false;
  tableBrief.value = undefined;
  columnsCache.value = {};
  currentTable.value = undefined;
  form.value.tables = [];
  progressTask.value = undefined;
  stopPoll();
  // 若上一轮导出弹窗还开着，切换连接时一并关闭，避免弹窗永久卡住
  exporting.value = false;
  try {
    if (value != null) {
      const brief = await metadataTables(value);
      tableBrief.value = brief;
      // 默认不选中任何对象：只导出用户显式勾选的表/视图，避免每次都全量遍历导出
      form.value.tables = [];
      // 自动浏览第一个对象（列信息由 currentTable 侦听器按需拉取）
      if (brief.tables?.length) {
        currentTable.value = brief.tables[0];
      }
    }
  } finally {
    loading.value = false;
  }
});

// 类型筛选变化：当前浏览对象不在可见列表时回落到第一项
watch(typeFilter, () => {
  if (!currentTable.value) return;
  const stillVisible = visibleTables.value.some(
    (t) => t.tableName === currentTable.value?.tableName,
  );
  if (!stillVisible) {
    currentTable.value = visibleTables.value[0];
  }
});

// ── 列信息按需加载（缓存 + 竞态保护）：点中对象才拉取该表列 ──
async function ensureColumns(tableName?: string) {
  if (!tableName || connectionId.value == null) return;
  if (columnsCache.value[tableName]) return;
  const seq = ++columnsRequestSeq;
  columnsLoading.value = true;
  try {
    const model = await metadataTableColumns(connectionId.value, tableName);
    //仅最新一次请求生效：快速连点时旧表结果不覆盖新表
    if (seq !== columnsRequestSeq) return;
    columnsCache.value[tableName] = model.columns ?? [];
  } catch {
    //失败不缓存，可再次点击重试；错误提示由全局拦截器处理
  } finally {
    if (seq === columnsRequestSeq) {
      columnsLoading.value = false;
    }
  }
}

//浏览对象变化 → 按需拉列（含连接加载完成后自动选中首表）
watch(
  () => currentTable.value?.tableName,
  (name) => {
    ensureColumns(name);
  },
);

// ── 全选/取消（切换时同步切到对应类别列表） ──
function handleCheckAllTables(value: boolean) {
  typeFilter.value = 'TABLE';
  const rest = form.value.tables.filter((t) => viewNames.value.includes(t));
  form.value.tables = value ? [...tableNames.value, ...rest] : [...rest];
}
function handleCheckAllViews(value: boolean) {
  typeFilter.value = 'VIEW';
  const rest = form.value.tables.filter((t) => !viewNames.value.includes(t));
  form.value.tables = value ? [...rest, ...viewNames.value] : rest;
}
const checkedTables = computed(
  () => form.value.tables.filter((t) => tableNames.value.includes(t)).length,
);
const checkedViews = computed(
  () => form.value.tables.filter((t) => viewNames.value.includes(t)).length,
);

// 判断某对象是否被选中用于导出
function isSelected(tableName: string) {
  return form.value.tables.includes(tableName);
}

// 勾选/取消单个对象（仅影响导出范围，不改变当前浏览对象）
function toggleSelect(tableName: string, checked: boolean) {
  if (checked) {
    if (!form.value.tables.includes(tableName)) {
      form.value.tables.push(tableName);
    }
  } else {
    const index = form.value.tables.indexOf(tableName);
    if (index >= 0) {
      form.value.tables.splice(index, 1);
    }
  }
}

// 点击对象行时：仅切换当前浏览对象（导出范围由勾选框决定）
function handleRowClick(record: TableModel) {
  currentTable.value = record;
}

// ── 导出 ──
async function handleExport() {
  if (connectionId.value == null) {
    window.message.warning('请先选择连接');
    return;
  }
  if (form.value.tables.length === 0) {
    window.message.warning('请至少选择一张要导出的对象');
    return;
  }
  exporting.value = true;
  progressTask.value = undefined;
  try {
    const payload: DocumentGenerateRequest = {
      connectionId: connectionId.value,
      format: form.value.format,
      title: form.value.title || undefined,
      version: form.value.version || undefined,
      description: form.value.description || undefined,
      tables: form.value.tables,
      excelSheetMode:
        form.value.format === 'EXCEL' ? form.value.excelSheetMode : undefined,
    };
    const tid = await documentGenerate(payload);
    taskId.value = tid;
    pollProgress(tid);
  } catch (e: any) {
    exporting.value = false;
    window.message.error(e?.message || '导出任务提交失败');
  }
}

function pollProgress(tid: string) {
  stopPoll();
  pollFailCount = 0;
  pollStartAt = Date.now();
  lastProgressAt = Date.now();
  lastProgressCurrent = -1;
  pollTimer = setInterval(async () => {
    // 总时长兜底：超过 1 小时仍无终态则停止跟踪（后端任务继续跑，前端不再无限等待）
    if (Date.now() - pollStartAt > POLL_MAX_MS) {
      stopPoll();
      exporting.value = false;
      window.message.error('导出耗时过长已停止跟踪，请减少导出对象后重试');
      return;
    }
    try {
      const task = await documentProgress(tid);
      pollFailCount = 0;
      progressTask.value = task;
      // 进度推进检测：current 变化即视为活跃（千表任务单表可能较慢，不能按总时长判死）
      if (task.current !== lastProgressCurrent) {
        lastProgressCurrent = task.current;
        lastProgressAt = Date.now();
      } else if (Date.now() - lastProgressAt > POLL_STALL_MS) {
        // 连续 3 分钟进度无推进判定卡死（如 JDBC 连接挂起）
        stopPoll();
        exporting.value = false;
        window.message.error('导出进度长时间无变化已停止跟踪，请稍后重试');
        return;
      }
      if (task.status === 'SUCCESS') {
        stopPoll();
        await downloadResult(tid);
      } else if (task.status === 'FAILED') {
        stopPoll();
        exporting.value = false;
        window.message.error(task.error || '文档生成失败');
      }
    } catch {
      // 连续失败达上限视为后端不可达，避免无限空转
      pollFailCount += 1;
      if (pollFailCount >= POLL_FAIL_LIMIT) {
        stopPoll();
        exporting.value = false;
        window.message.error('导出进度查询失败，请稍后重试');
      }
    }
  }, 1000);
}

async function downloadResult(tid: string) {
  try {
    const blob = await documentDownload(tid);
    const ext = fileTypeToExt[form.value.format];
    const stamp = dayjs().format('YYYYMMDD_HHmmss');
    // 清洗文件名非法字符（Windows: \\ / : * ? " < > |）
    const safeTitle = (form.value.title || '数据库文档').replace(
      /[\\/:*?"<>|]/g,
      '_',
    );
    const fileName = `${safeTitle}_${stamp}.${ext}`;
    downloadByData(blob, fileName);
    window.message.success('文档已生成并开始下载');
  } catch (e: any) {
    // 下载失败必须提示（此前异常被轮询 catch 吞掉导致静默失败）
    window.message.error(e?.message || '文档下载失败，请稍后重试');
  } finally {
    exporting.value = false;
  }
}

const exportDisabled = computed(
  () => connectionId.value == null || form.value.tables.length === 0,
);

const progressPercent = computed(() => {
  const t = progressTask.value;
  if (!t) return 0;
  if (t.total > 0)
    return Math.min(100, Math.round((t.current / t.total) * 100));
  return t.status === 'SUCCESS' ? 100 : 0;
});

const emptyDescription = computed(() => {
  if (loading.value) return '加载中…';
  if (connectionId.value == null) return '请先选择连接';
  if (searchKeyword.value.trim()) return '没有匹配的对象';
  return '暂无数据';
});
</script>

<template>
  <Page :auto-content-height="true">
    <div class="flex h-full flex-col gap-3">
      <!-- 顶部：连接选择 -->
      <div class="bg-card rounded-lg px-4 py-3">
        <div class="flex items-center gap-4">
          <span class="text-[14px] text-[var(--ant-color-text-secondary)]">
            选择连接
          </span>
          <ConnectionSelect
            v-model:value="connectionId"
            class="max-w-[360px]"
            placeholder="请选择要导出的数据库连接"
          />
          <span
            v-if="tableBrief?.databaseType"
            class="text-[12px] text-[var(--ant-color-text-description)]"
          >
            {{ tableBrief.databaseType }}
          </span>
        </div>
      </div>

      <Spin
        :spinning="loading"
        size="large"
        tip="正在加载表清单…"
        class="min-h-0 flex-1"
      >
        <div class="flex h-full gap-3">
          <!-- 左：对象清单 -->
          <div
            class="bg-card flex w-[380px] shrink-0 flex-col overflow-hidden rounded-lg"
          >
            <div
              class="flex items-center justify-between border-b border-[var(--ant-color-split)] px-4 py-2.5"
            >
              <span class="text-[15px] font-medium">
                对象清单
                <span
                  class="ml-1 text-[12px] text-[var(--ant-color-text-description)]"
                >
                  表 {{ tableCount }} · 视图 {{ viewCount }}
                </span>
              </span>
              <Segmented
                size="small"
                :value="typeFilter"
                :options="[
                  { label: '全部', value: 'ALL' },
                  { label: '表', value: 'TABLE' },
                  {
                    label: `视图${viewCount > 0 ? ` (${viewCount})` : ''}`,
                    value: 'VIEW',
                    disabled: viewCount === 0,
                  },
                ]"
                @change="(v: any) => (typeFilter = v)"
              />
            </div>

            <!-- 搜索框 -->
            <div class="border-b border-[var(--ant-color-split)] px-4 py-2">
              <InputSearch
                v-model:value="searchKeyword"
                allow-clear
                placeholder="搜索表/视图名称或备注"
                size="small"
              />
            </div>

            <!-- 导出选择快捷区 -->
            <div
              class="flex items-center gap-3 border-b border-[var(--ant-color-split)] px-4 py-1.5 text-[12px]"
            >
              <Checkbox
                :checked="
                  checkedTables === tableNames.length && tableNames.length > 0
                "
                :indeterminate="
                  checkedTables > 0 && checkedTables < tableNames.length
                "
                :disabled="tableNames.length === 0"
                @change="(e: any) => handleCheckAllTables(e.target.checked)"
              >
                全选表（{{ checkedTables }}/{{ tableNames.length }}）
              </Checkbox>
              <Checkbox
                v-if="hasViews"
                :checked="
                  checkedViews === viewNames.length && viewNames.length > 0
                "
                :indeterminate="
                  checkedViews > 0 && checkedViews < viewNames.length
                "
                :disabled="viewNames.length === 0"
                @change="(e: any) => handleCheckAllViews(e.target.checked)"
              >
                全选视图（{{ checkedViews }}/{{ viewNames.length }}）
              </Checkbox>
            </div>

            <!-- 对象列表（分页） -->
            <div class="obj-list min-h-0 flex-1 overflow-y-auto py-1">
              <div
                v-if="pagedTables.length === 0"
                class="flex h-full items-center justify-center"
              >
                <Empty :description="emptyDescription" />
              </div>
              <template v-else>
                <div
                  v-for="t in pagedTables"
                  :key="t.tableName"
                  class="obj-item"
                  :class="{
                    'obj-item-selected': isSelected(t.tableName),
                    'obj-item-active': currentTable?.tableName === t.tableName,
                  }"
                  role="button"
                  tabindex="0"
                  @click="handleRowClick(t)"
                  @keydown.enter.prevent="handleRowClick(t)"
                  @keydown.space.prevent="handleRowClick(t)"
                >
                  <span class="obj-check-box" @click.stop>
                    <Checkbox
                      :checked="isSelected(t.tableName)"
                      @change="
                        (e: any) => toggleSelect(t.tableName, e.target.checked)
                      "
                    />
                  </span>
                  <Tag
                    :color="isView(t) ? 'cyan' : 'blue'"
                    class="mr-0 shrink-0"
                  >
                    {{ isView(t) ? '视图' : '表' }}
                  </Tag>
                  <span class="obj-name">{{ t.tableName }}</span>
                  <span class="obj-remarks">{{ t.remarks || '' }}</span>
                </div>
              </template>
            </div>

            <!-- 分页 -->
            <div
              class="flex items-center justify-between border-t border-[var(--ant-color-split)] px-3 py-1"
            >
              <span
                class="text-[12px] text-[var(--ant-color-text-description)]"
              >
                共 {{ visibleTables.length }} 个
              </span>
              <Pagination
                v-model:current="currentPage"
                size="small"
                simple
                :total="visibleTables.length"
                :page-size="pageSize"
              />
            </div>
          </div>

          <!-- 中：列结构浏览 -->
          <div
            class="bg-card flex min-w-0 flex-1 flex-col overflow-hidden rounded-lg"
          >
            <div
              class="flex items-center justify-between border-b border-[var(--ant-color-split)] px-4 py-3"
            >
              <span
                class="flex min-w-0 items-center gap-2 text-[15px] font-medium"
              >
                <span class="truncate">
                  {{ currentTable?.tableName || '列结构' }}
                </span>
                <Tag v-if="currentTable && isView(currentTable)" color="cyan">
                  视图
                </Tag>
              </span>
              <span
                v-if="currentTable?.remarks"
                class="text-[12px] text-[var(--ant-color-text-description)]"
              >
                {{ currentTable.remarks }}
              </span>
            </div>
            <div class="min-h-0 flex-1 overflow-hidden">
              <!-- 列结构：列表式展示 -->
              <template v-if="currentColumns.length > 0">
                <div class="col-head">
                  <span class="col-c1">#</span>
                  <span>字段</span>
                  <span>数据类型</span>
                  <span class="col-center">可空</span>
                  <span>默认值</span>
                  <span>备注</span>
                </div>
                <div class="col-body">
                  <div
                    v-for="c in currentColumns"
                    :key="`${c.columnName}-${c.ordinalPosition}`"
                    class="col-row"
                  >
                    <span class="col-c1">{{ c.ordinalPosition }}</span>
                    <span class="col-ellipsis col-name">{{
                      c.columnName
                    }}</span>
                    <span class="col-ellipsis col-type">{{
                      c.columnType
                    }}</span>
                    <span class="col-center">
                      <Tag
                        :color="c.nullable === 'NO' ? 'red' : 'green'"
                        class="col-tag"
                      >
                        {{ c.nullable === 'NO' ? '否' : '是' }}
                      </Tag>
                    </span>
                    <span class="col-ellipsis">{{ c.columnDef || '-' }}</span>
                    <span class="col-ellipsis col-remark">{{
                      c.remarks || ''
                    }}</span>
                  </div>
                </div>
              </template>
              <div v-else class="flex h-full items-center justify-center">
                <Spin v-if="columnsLoading" />
                <Empty
                  v-else-if="!loading"
                  description="未选择对象或暂无列数据"
                />
              </div>
            </div>
          </div>

          <!-- 右：导出配置 -->
          <div
            class="bg-card flex w-[340px] shrink-0 flex-col overflow-hidden rounded-lg"
          >
            <div
              class="border-b border-[var(--ant-color-split)] px-4 py-3 text-[15px] font-medium"
            >
              导出配置
            </div>
            <div class="min-h-0 flex-1 overflow-auto p-4">
              <Form layout="vertical" size="small">
                <FormItem label="文档标题">
                  <Input
                    v-model:value="form.title"
                    allow-clear
                    :maxlength="100"
                    placeholder="请输入文档标题"
                  />
                </FormItem>
                <FormItem label="导出格式">
                  <RadioGroup v-model:value="form.format">
                    <Radio
                      v-for="opt in formatOptions"
                      :key="opt.value"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </Radio>
                  </RadioGroup>
                </FormItem>
                <FormItem label="版本号">
                  <Input
                    v-model:value="form.version"
                    allow-clear
                    :maxlength="50"
                    placeholder="选填"
                  />
                </FormItem>
                <FormItem label="描述">
                  <Input
                    v-model:value="form.description"
                    allow-clear
                    :maxlength="200"
                    placeholder="选填"
                  />
                </FormItem>
                <FormItem
                  v-if="form.format === 'EXCEL'"
                  label="Excel Sheet 模式"
                >
                  <RadioGroup v-model:value="form.excelSheetMode">
                    <Radio value="PER_TABLE">每表一个 Sheet</Radio>
                    <Radio value="SINGLE">所有表一个 Sheet</Radio>
                  </RadioGroup>
                </FormItem>
                <FormItem label="已选导出对象">
                  <div
                    class="text-[12px] text-[var(--ant-color-text-secondary)]"
                  >
                    表 {{ checkedTables }} · 视图 {{ checkedViews }} · 共
                    {{ form.tables.length }} 个
                  </div>
                </FormItem>
              </Form>
            </div>
            <div class="border-t border-[var(--ant-color-split)] p-3">
              <Button
                :loading="exporting"
                :disabled="exportDisabled"
                type="primary"
                block
                @click="handleExport"
              >
                生成并下载
              </Button>
            </div>
          </div>
        </div>
      </Spin>

      <!-- 导出进度弹窗（居中，导出期间不可关闭，仿 SmartSQL） -->
      <Modal
        v-model:open="exporting"
        centered
        :closable="false"
        :footer="null"
        :keyboard="false"
        :mask-closable="false"
        :width="420"
        title="正在导出文档，请勿关闭窗口…"
      >
        <div class="py-2">
          <div class="mb-3 flex items-start gap-2">
            <span
              class="min-w-0 flex-1 text-[13px] break-all text-[var(--ant-color-text)]"
            >
              {{
                progressTask?.status === 'SUCCESS'
                  ? '文档已生成，正在下载…'
                  : progressTask?.currentTable ||
                    '正在为您准备导出文档，请耐心等候'
              }}
            </span>
          </div>
          <Progress
            :percent="progressPercent"
            :status="progressTask?.status === 'FAILED' ? 'exception' : 'active'"
          />
          <div
            class="mt-1 text-right text-[12px] text-[var(--ant-color-text-secondary)]"
          >
            {{ progressTask?.current ?? 0 }}/{{ progressTask?.total ?? 0 }}
          </div>
          <template v-if="progressTask?.status === 'FAILED'">
            <div class="mt-2 text-[12px] text-[var(--ant-color-error)]">
              {{ progressTask?.error || '文档生成失败' }}
            </div>
            <div class="mt-3 text-right">
              <Button size="small" @click="exporting = false">关闭</Button>
            </div>
          </template>
        </div>
      </Modal>
    </div>
  </Page>
</template>

<style scoped>
/* Spin 高度链：让内部容器继承高度，避免列表/表格塌陷 */
:deep(.ant-spin-nested-loading) {
  height: 100%;
}

:deep(.ant-spin-container) {
  height: 100%;
}

/* 对象行：tag + 名称 + 备注 + 已选对勾 */
.obj-item {
  position: relative;
  display: flex;
  gap: 8px;
  align-items: center;
  height: 32px;
  padding: 0 10px;
  margin: 1px 6px;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.15s ease;
}

.obj-item:hover {
  background-color: var(--ant-color-fill-tertiary);
}

.obj-item:focus-visible {
  outline: 2px solid var(--ant-color-primary);
  outline-offset: -2px;
}

/* 已加入导出：浅灰底（柔和、随主题适配暗色模式） */
.obj-item-selected {
  background-color: var(--ant-color-fill-quaternary);
}

.obj-item-selected:hover {
  background-color: var(--ant-color-fill-tertiary);
}

/* 当前浏览对象：左侧细蓝条 */
.obj-item-active::before {
  position: absolute;
  top: 6px;
  bottom: 6px;
  left: 0;
  width: 3px;
  content: '';
  background-color: var(--ant-color-primary);
  border-radius: 2px;
}

.obj-item .ant-tag {
  padding: 0 5px;
  margin-inline-end: 0;
  font-size: 11px;
  line-height: 18px;
}

.obj-name {
  min-width: 0;
  max-width: 52%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.obj-remarks {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--ant-color-text-quaternary);
  text-align: right;
  white-space: nowrap;
}

/* 行首勾选框：点击不触发行浏览，仅切换导出范围 */
.obj-check-box {
  display: flex;
  flex-shrink: 0;
  align-items: center;
}

.obj-check-box .ant-checkbox-inner {
  width: 14px;
  height: 14px;
}

/* ── 中栏：列结构列表 ── */
.col-head,
.col-row {
  display: grid;
  grid-template-columns: 44px minmax(120px, 1.2fr) minmax(
      120px,
      1.2fr
    ) 52px minmax(100px, 1fr) minmax(120px, 1.4fr);
  gap: 8px;
  align-items: center;
  padding: 0 14px;
}

.col-head {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 38px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ant-color-text-secondary);
  background: var(--ant-color-fill-quaternary);
  border-bottom: 1px solid var(--ant-color-split);
}

.col-body {
  height: calc(100% - 38px);
  overflow-y: auto;
}

.col-row {
  height: 36px;
  font-size: 13px;
  border-bottom: 1px solid var(--ant-color-split);
  transition: background-color 0.12s ease;
}

.col-row:hover {
  background-color: var(--ant-color-fill-quaternary);
}

.col-c1 {
  font-size: 12px;
  color: var(--ant-color-text-quaternary);
  text-align: center;
}

.col-center {
  text-align: center;
}

.col-name {
  font-weight: 500;
}

.col-type {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--ant-color-text-secondary);
}

.col-remark {
  color: var(--ant-color-text-quaternary);
}

.col-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-tag {
  padding: 0 5px;
  margin-inline-end: 0;
  font-size: 11px;
  line-height: 16px;
}
</style>

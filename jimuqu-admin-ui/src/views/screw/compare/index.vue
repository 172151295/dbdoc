<script setup lang="ts">
import type {
  ColumnDiff,
  CompareResult,
  DbCompareRequest,
  TableDiff,
} from '@/api/screw/model';

import { computed, ref } from 'vue';

import { compareDo } from '@/api/screw';
import { Page } from '@/components';
import { Card, Empty, Modal, Spin, Switch, Table, Tag } from 'antdv-next';

import ConnectionSelect from '../components/connection-select.vue';

const sourceId = ref<number>();
const targetId = ref<number>();
const diffOnly = ref(false);

const comparing = ref(false);
const result = ref<CompareResult>();

const summary = computed(() => result.value?.summary);

const columnDiffColumns = [
  { title: '表', dataIndex: 'table', key: 'table' },
  { title: '列', dataIndex: 'column', key: 'column' },
  { title: '源', dataIndex: 'source', key: 'source' },
  { title: '目标', dataIndex: 'target', key: 'target' },
  {
    title: '类型',
    dataIndex: 'type',
    key: 'type',
    width: 180,
  },
];

const tableDiffColumns = [
  { title: '表', dataIndex: 'table', key: 'table' },
  { title: '类型', dataIndex: 'type', key: 'type', width: 120 },
  { title: '差异', dataIndex: 'detail', key: 'detail' },
];

function typeColor(type: string) {
  switch (type) {
    case 'COLUMN_DIFF':
      return 'blue';
    case 'COLUMN_MISSING_IN_SOURCE':
      return 'red';
    case 'COLUMN_MISSING_IN_TARGET':
      return 'orange';
    case 'PK_DIFF':
      return 'purple';
    default:
      return 'default';
  }
}

function typeLabel(type: string) {
  switch (type) {
    case 'COLUMN_DIFF':
      return '字段差异';
    case 'COLUMN_MISSING_IN_SOURCE':
      return '源缺失列';
    case 'COLUMN_MISSING_IN_TARGET':
      return '目标缺失列';
    case 'PK_DIFF':
      return '主键差异';
    default:
      return type;
  }
}

function tableTypeLabel(type: string) {
  return type === 'REMARK' ? '表注释' : type;
}

async function handleCompare() {
  if (sourceId.value == null || targetId.value == null) {
    window.message.warning('请选择源连接和目标连接');
    return;
  }
  if (sourceId.value === targetId.value) {
    window.message.warning('源连接与目标连接不能相同');
    return;
  }
  comparing.value = true;
  result.value = undefined;
  try {
    const payload: DbCompareRequest = {
      sourceId: sourceId.value,
      targetId: targetId.value,
      diffOnly: diffOnly.value,
    };
    result.value = await compareDo(payload);
  } finally {
    comparing.value = false;
  }
}

const columnDiffList = computed<ColumnDiff[]>(() => {
  const list = result.value?.columnDiffs ?? [];
  return list;
});

const tableDiffList = computed<TableDiff[]>(() => {
  const list = result.value?.tableDiffs ?? [];
  return list;
});

// ==================== 同步脚本 ====================

const syncScript = computed<null | string>(
  () => result.value?.syncScript ?? null,
);
const syncDialect = computed<null | string>(
  () => result.value?.syncDialect ?? null,
);
const syncWarnings = computed<string[]>(() => result.value?.syncWarnings ?? []);

/** 未处理的目标多出对象（非删除策略，需人工确认） */
const skippedCount = computed(() => {
  const s = summary.value;
  if (!s) return 0;
  return (
    (s.syncSkippedTargetOnlyTables ?? 0) + (s.syncSkippedTargetOnlyColumns ?? 0)
  );
});

const syncLineCount = computed(() => {
  const text = syncScript.value;
  return text ? text.split('\n').length : 0;
});

const syncSizeText = computed(() => {
  const text = syncScript.value;
  if (!text) return '';
  const bytes = new TextEncoder().encode(text).length;
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(2)} MB`
    : `${(bytes / 1024).toFixed(1)} KB`;
});

const scriptFullscreen = ref(false);

/** 统计标签（为 0 的不显示） */
const syncCountTags = computed(() => {
  const s = summary.value;
  if (!s || !syncScript.value) return [];
  const items: { color: string; label: string }[] = [];
  if (s.syncCreateTables)
    items.push({ label: `${s.syncCreateTables} 建表`, color: 'red' });
  if (s.syncAddColumns)
    items.push({ label: `${s.syncAddColumns} 加列`, color: 'green' });
  if (s.syncModifyColumns)
    items.push({ label: `${s.syncModifyColumns} 改列`, color: 'orange' });
  if (s.syncTableComments)
    items.push({ label: `${s.syncTableComments} 表注释`, color: 'purple' });
  if (s.syncColumnComments)
    items.push({ label: `${s.syncColumnComments} 列注释`, color: 'purple' });
  return items;
});

async function copySyncScript() {
  const text = syncScript.value;
  if (!text) {
    window.message.warning('暂无可复制的同步脚本');
    return;
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      // 非安全上下文（http 直连）回退到隐藏域方式
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    window.message.success('同步脚本已复制');
  } catch {
    window.message.error('复制失败，请在脚本区手动全选复制');
  }
}

function downloadSyncScript() {
  const text = syncScript.value;
  if (!text) {
    window.message.warning('暂无可下载的同步脚本');
    return;
  }
  const now = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  const stamp = `${now.getFullYear()}${p(now.getMonth() + 1)}${p(now.getDate())}-${p(now.getHours())}${p(now.getMinutes())}${p(now.getSeconds())}`;
  const dialectPart = (syncDialect.value ?? '')
    .replace(/[^A-Za-z0-9_\u4e00-\u9fa5]/g, '')
    .replace(/\s+/g, '');
  const file = dialectPart
    ? `db-sync-${dialectPart}-${stamp}.sql`
    : `db-sync-${stamp}.sql`;
  // 加 BOM：Windows 客户端打开时中文注释不乱码
  const blob = new Blob(['\ufeff' + text], {
    type: 'text/plain;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = file;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  window.message.success(`已下载 ${file}`);
}
</script>

<template>
  <Page :auto-content-height="true">
    <div class="flex h-full flex-col gap-4">
      <Card size="small">
        <div class="grid grid-cols-1 items-end gap-6 md:grid-cols-4">
          <div class="flex flex-col gap-2">
            <span class="text-[14px] text-[var(--ant-color-text-secondary)]"
              >源连接</span
            >
            <ConnectionSelect
              v-model:value="sourceId"
              class="w-full"
              placeholder="请选择源连接"
            />
          </div>
          <div class="flex flex-col gap-2">
            <span class="text-[14px] text-[var(--ant-color-text-secondary)]"
              >目标连接</span
            >
            <ConnectionSelect
              v-model:value="targetId"
              class="w-full"
              placeholder="请选择目标连接"
            />
          </div>
          <div class="flex items-center gap-2">
            <Switch v-model:checked="diffOnly" />
            <span class="text-[14px] text-[var(--ant-color-text-secondary)]"
              >仅看差异</span
            >
          </div>
          <div class="flex justify-end">
            <a-button :loading="comparing" type="primary" @click="handleCompare"
              >对比</a-button
            >
          </div>
        </div>
      </Card>

      <Spin :spinning="comparing" size="large">
        <div v-if="result" class="flex h-full flex-col gap-4 overflow-auto">
          <!-- 汇总 -->
          <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Card size="small">
              <div class="text-[12px] text-[var(--ant-color-text-description)]">
                仅源表
              </div>
              <div
                class="text-[24px] font-semibold text-[var(--ant-color-error)]"
              >
                {{ summary?.onlySourceTables ?? 0 }}
              </div>
            </Card>
            <Card size="small">
              <div class="text-[12px] text-[var(--ant-color-text-description)]">
                仅目标表
              </div>
              <div
                class="text-[24px] font-semibold text-[var(--ant-color-warning)]"
              >
                {{ summary?.onlyTargetTables ?? 0 }}
              </div>
            </Card>
            <Card size="small">
              <div class="text-[12px] text-[var(--ant-color-text-description)]">
                表差异
              </div>
              <div
                class="text-[24px] font-semibold text-[var(--ant-color-info)]"
              >
                {{ summary?.tableDiffs ?? 0 }}
              </div>
            </Card>
            <Card size="small">
              <div class="text-[12px] text-[var(--ant-color-text-description)]">
                列差异
              </div>
              <div
                class="text-[24px] font-semibold text-[var(--ant-color-primary)]"
              >
                {{ summary?.columnDiffs ?? 0 }}
              </div>
            </Card>
          </div>

          <!-- 仅单侧表 -->
          <div v-if="diffOnly" class="grid grid-cols-2 gap-4">
            <Card size="small" title="仅源存在的表">
              <div
                v-if="(result.onlySourceTables ?? []).length === 0"
                class="text-[12px] text-[var(--ant-color-text-description)]"
              >
                无
              </div>
              <div v-else class="flex flex-wrap gap-2">
                <Tag
                  v-for="name in result.onlySourceTables"
                  :key="name"
                  color="red"
                  >{{ name }}</Tag
                >
              </div>
            </Card>
            <Card size="small" title="仅目标存在的表">
              <div
                v-if="(result.onlyTargetTables ?? []).length === 0"
                class="text-[12px] text-[var(--ant-color-text-description)]"
              >
                无
              </div>
              <div v-else class="flex flex-wrap gap-2">
                <Tag
                  v-for="name in result.onlyTargetTables"
                  :key="name"
                  color="orange"
                  >{{ name }}</Tag
                >
              </div>
            </Card>
          </div>

          <!-- 表差异 -->
          <Card size="small" title="表差异">
            <Table
              :columns="tableDiffColumns"
              :data-source="tableDiffList"
              :pagination="false"
              row-key="table"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'type'">
                  <Tag :color="typeColor((record as TableDiff).type)">
                    {{ tableTypeLabel((record as TableDiff).type) }}
                  </Tag>
                </template>
              </template>
            </Table>
          </Card>

          <!-- 同步脚本 -->
          <Card v-if="syncScript || syncWarnings.length" size="small">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-[14px] font-medium">同步脚本</span>
                <Tag v-if="syncDialect" color="blue">{{ syncDialect }}</Tag>
                <template v-if="syncScript">
                  <Tag
                    v-for="item in syncCountTags"
                    :key="item.label"
                    :color="item.color"
                  >
                    {{ item.label }}
                  </Tag>
                </template>
                <Tag v-if="skippedCount" color="warning"
                  >未处理 {{ skippedCount }}</Tag
                >
                <span
                  v-if="syncScript"
                  class="text-[12px] text-[var(--ant-color-text-description)]"
                >
                  共 {{ syncLineCount }} 行 · {{ syncSizeText }}
                </span>
              </div>
              <div v-if="syncScript" class="flex gap-2">
                <a-button size="small" @click="copySyncScript">复制</a-button>
                <a-button size="small" @click="downloadSyncScript"
                  >下载 .sql</a-button
                >
                <a-button
                  size="small"
                  type="primary"
                  @click="scriptFullscreen = true"
                >
                  全屏查看
                </a-button>
              </div>
            </div>

            <!-- 生成警告：跨方言 / 不做删除 / 主键差异等需人工确认项 -->
            <div
              v-if="syncWarnings.length"
              class="mt-2 flex flex-col gap-1 rounded border border-[var(--ant-color-warning)] bg-[var(--ant-color-warning-bg)] p-2 text-[12px] leading-5 text-[var(--ant-color-warning)]"
            >
              <div v-for="(warn, idx) in syncWarnings" :key="idx">
                {{ warn }}
              </div>
            </div>

            <pre
              v-if="syncScript"
              class="mt-2 max-h-[384px] overflow-auto rounded bg-[var(--ant-color-fill-tertiary)] p-3 text-[12px] leading-5 whitespace-pre text-[var(--ant-color-text)]"
              >{{ syncScript }}</pre>
          </Card>

          <!-- 同步脚本全屏查看 -->
          <Modal
            v-model:open="scriptFullscreen"
            :footer="null"
            width="80vw"
            title="同步脚本"
          >
            <div class="flex items-center justify-between gap-2 pb-2">
              <span
                class="text-[12px] text-[var(--ant-color-text-description)]"
              >
                目标方言：{{ syncDialect || '未知' }} · 共
                {{ syncLineCount }} 行
              </span>
              <div class="flex gap-2">
                <a-button size="small" @click="copySyncScript">复制</a-button>
                <a-button
                  size="small"
                  type="primary"
                  @click="downloadSyncScript"
                >
                  下载 .sql
                </a-button>
              </div>
            </div>
            <pre
              class="max-h-[70vh] overflow-auto rounded bg-[var(--ant-color-fill-tertiary)] p-3 text-[12px] leading-5 whitespace-pre"
              >{{ syncScript }}</pre>
          </Modal>

          <!-- 列差异 -->
          <Card size="small" title="列差异">
            <Table
              :columns="columnDiffColumns"
              :data-source="columnDiffList"
              :pagination="{ pageSize: 10 }"
              row-key="column"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'type'">
                  <Tag :color="typeColor((record as ColumnDiff).type)">
                    {{ typeLabel((record as ColumnDiff).type) }}
                  </Tag>
                </template>
              </template>
            </Table>
          </Card>
        </div>

        <div v-else class="flex h-full items-center justify-center">
          <Empty description="请选择源连接和目标连接后点击「对比」" />
        </div>
      </Spin>
    </div>
  </Page>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import { Button, Input, RadioGroup, Space } from 'antdv-next';

import { downloadText } from '../utils';
import ToolShell from './ToolShell.vue';

const input = ref('');
const format = ref<'csv' | 'xls'>('xls');
const error = ref('');
const rows = ref(0);
const cols = ref(0);

function parse(): null | Record<string, unknown>[] {
  try {
    const data = JSON.parse(input.value);
    if (
      !Array.isArray(data) ||
      data.length === 0 ||
      typeof data[0] !== 'object'
    ) {
      throw new Error('需为对象数组，如 [{ "name": "张三", "age": 30 }]');
    }
    error.value = '';
    return data as Record<string, unknown>[];
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
    return null;
  }
}

function exportFile() {
  const data = parse();
  if (!data) {
    return;
  }
  const keys = Array.from(new Set(data.flatMap((r) => Object.keys(r))));
  rows.value = data.length;
  cols.value = keys.length;

  if (format.value === 'csv') {
    const esc = (v: unknown) => {
      const s = v == null ? '' : String(v);
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const csv = [
      keys.join(','),
      ...data.map((r) => keys.map((k) => esc(r[k])).join(',')),
    ].join('\n');
    downloadText(`json-${Date.now()}.csv`, csv, 'text/csv');
    return;
  }

  // HTML 表格 → .xls（Excel 兼容，零依赖）
  const esc = (v: unknown) =>
    String(v ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  const thead = `<tr>${keys.map((k) => `<th>${esc(k)}</th>`).join('')}</tr>`;
  const tbody = data
    .map((r) => `<tr>${keys.map((k) => `<td>${esc(r[k])}</td>`).join('')}</tr>`)
    .join('');
  const html = `<html><head><meta charset="utf-8"></head><body><table border="1">${thead}${tbody}</table></body></html>`;
  downloadText(`json-${Date.now()}.xls`, html, 'application/vnd.ms-excel');
}
</script>

<template>
  <ToolShell
    title="JSON 转 Excel"
    description="对象数组 → .xls / .csv（纯前端生成，带 UTF-8 BOM 中文不乱码）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Input.TextArea
        v-model:value="input"
        :rows="8"
        placeholder='粘贴对象数组，如 [{"name":"张三","age":30},{"name":"李四","age":25}]'
      />
      <Space wrap>
        <RadioGroup
          v-model:value="format"
          option-type="button"
          :options="[
            { label: 'Excel (.xls)', value: 'xls' },
            { label: 'CSV (.csv)', value: 'csv' },
          ]"
        />
        <Button type="primary" @click="exportFile">导出</Button>
      </Space>
      <div v-if="error" class="text-[13px] text-[var(--ant-color-error)]">
        {{ error }}
      </div>
      <div
        v-if="rows"
        class="text-[13px] text-[var(--ant-color-text-secondary)]"
      >
        已导出 {{ rows }} 行 × {{ cols }} 列
      </div>
    </Space>
  </ToolShell>
</template>

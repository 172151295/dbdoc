<script setup lang="ts">
import { computed, h, ref } from 'vue';

import { Empty, Input, Table, Tag } from 'antdv-next';

import { MIME_TABLE } from '../data';
import ToolShell from './ToolShell.vue';

const keyword = ref('');
const limit = ref(50);

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  const base = kw
    ? MIME_TABLE.filter(
        (e) => e.ext.includes(kw) || e.mime.includes(kw) || e.type.includes(kw),
      )
    : MIME_TABLE;
  return base.slice(0, limit.value);
});

const columns = [
  { title: '扩展名', dataIndex: 'ext', key: 'ext', width: 120 },
  { title: 'MIME 类型', dataIndex: 'mime', key: 'mime' },
  {
    title: '分类',
    dataIndex: 'type',
    key: 'type',
    width: 100,
    customRender: ({ text }: { text: string }) =>
      h(Tag, { color: 'blue' }, () => text),
  },
];
</script>

<template>
  <ToolShell
    title="MimeType 对照表"
    description="常见文件扩展名与 MIME 类型速查"
  >
    <div class="flex max-w-3xl flex-col gap-3">
      <div class="flex items-center gap-2">
        <Input
          v-model:value="keyword"
          placeholder="搜索扩展名 / MIME / 分类（如 png / image / 文档）"
          allow-clear
          style="width: 340px"
        />
        <span class="text-[13px] text-[var(--ant-color-text-description)]"
          >显示 {{ filtered.length }} / {{ MIME_TABLE.length }} 条</span
        >
      </div>
      <Table
        v-if="filtered.length"
        :columns="columns"
        :data-source="filtered"
        :pagination="false"
        size="small"
        :scroll="{ y: 420 }"
        row-key="ext"
      />
      <Empty v-else description="无匹配项" class="py-12" />
    </div>
  </ToolShell>
</template>

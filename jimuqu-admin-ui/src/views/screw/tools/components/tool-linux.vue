<script setup lang="ts">
import { computed, ref } from 'vue';

import { Empty, Input, Select, Table } from 'antdv-next';

import { LINUX_COMMANDS } from '../data';
import ToolShell from './ToolShell.vue';

const groups = Array.from(new Set(LINUX_COMMANDS.map((c) => c.group)));
const group = ref<string>('全部');
const keyword = ref('');

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return LINUX_COMMANDS.filter((c) => {
    if (group.value !== '全部' && c.group !== group.value) {
      return false;
    }
    if (!kw) {
      return true;
    }
    return (
      c.cmd.includes(kw) ||
      c.desc.includes(kw) ||
      c.example.toLowerCase().includes(kw)
    );
  });
});

const columns = [
  { title: '命令', dataIndex: 'cmd', key: 'cmd', width: 120 },
  { title: '说明', dataIndex: 'desc', key: 'desc', width: 200 },
  { title: '示例', dataIndex: 'example', key: 'example', ellipsis: true },
];
</script>

<template>
  <ToolShell
    title="Linux 命令大全"
    description="常用命令速查（文件/网络/系统/包管理等分类）"
  >
    <div class="flex max-w-4xl flex-col gap-3">
      <div class="flex flex-wrap items-center gap-2">
        <Select
          v-model:value="group"
          style="width: 150px"
          :options="[
            { value: '全部', label: '全部' },
            ...groups.map((g) => ({ value: g, label: g })),
          ]"
        />
        <Input
          v-model:value="keyword"
          placeholder="搜索命令 / 说明 / 示例"
          allow-clear
          style="width: 300px"
        />
        <span class="text-[13px] text-[var(--ant-color-text-description)]"
          >共 {{ filtered.length }} 条</span
        >
      </div>
      <Table
        v-if="filtered.length"
        :columns="columns"
        :data-source="filtered"
        :pagination="false"
        size="small"
        :scroll="{ y: 420 }"
        row-key="cmd"
      />
      <Empty v-else description="无匹配命令" class="py-12" />
    </div>
  </ToolShell>
</template>

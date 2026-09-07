<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { Icon } from '@iconify/vue';
import { Empty, Input, InputNumber, Select, Space, Spin } from 'antdv-next';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

interface IconInfo {
  name: string;
  body: string;
}

// 内置常用图标集（@iconify/json 按需懒加载，避免首屏膨胀）
const COLLECTIONS = [
  { value: 'lucide', label: 'Lucide' },
  { value: 'mdi', label: 'Material Design' },
  { value: 'ri', label: 'Remix Icon' },
  { value: 'ant-design', label: 'Ant Design' },
];

const collection = ref('lucide');
const keyword = ref('');
const limit = ref(80);
const icons = ref<IconInfo[]>([]);
const loading = ref(false);
const total = ref(0);

async function load() {
  loading.value = true;
  try {
    const mod = await import(
      /* @vite-ignore */ `@iconify/json/json/${collection.value}.json`
    );
    const data = mod.default as { icons: Record<string, { body?: string }> };
    const all = Object.entries(data.icons)
      .filter(([, v]) => v && v.body)
      .map(([name, v]) => ({ name, body: v.body! }));
    total.value = all.length;
    icons.value = all;
  } catch (e) {
    window.message.error(
      `加载图标集失败：${e instanceof Error ? e.message : String(e)}`,
    );
    icons.value = [];
  } finally {
    loading.value = false;
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  const base = kw
    ? icons.value.filter((i) => i.name.toLowerCase().includes(kw))
    : icons.value;
  return base.slice(0, limit.value);
});

async function onPick(name: string) {
  if (await copyText(name)) {
    window.message.success(`已复制图标名：${name}`);
  }
}

watch([collection], load, { immediate: true });
</script>

<template>
  <ToolShell
    title="Icon 图标"
    description="内置 4 套常用图标集，点击复制图标名（如 lucide:home）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <Select
          v-model:value="collection"
          :options="COLLECTIONS"
          style="width: 180px"
        />
        <Input
          v-model:value="keyword"
          placeholder="搜索图标（如 home / user / setting）"
          style="width: 260px"
          allow-clear
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >每页</span
        >
        <InputNumber
          v-model:value="limit"
          :min="20"
          :max="300"
          :step="20"
          style="width: 90px"
        />
        <span class="text-[13px] text-[var(--ant-color-text-description)]"
          >共 {{ total }} 个图标</span
        >
      </Space>
      <Spin :spinning="loading">
        <div
          v-if="filtered.length"
          class="grid grid-cols-6 gap-2 sm:grid-cols-8 md:grid-cols-10 lg:grid-cols-12"
        >
          <button
            v-for="ic in filtered"
            :key="ic.name"
            class="flex flex-col items-center gap-1 rounded-md border border-[var(--ant-color-border-secondary)] p-2 transition hover:border-[var(--ant-color-primary)] hover:bg-[var(--ant-color-primary-bg)]"
            :title="ic.name"
            @click="onPick(ic.name)"
          >
            <Icon :icon="`${collection}:${ic.name}`" class="text-[22px]" />
            <span
              class="max-w-full truncate text-[10px] text-[var(--ant-color-text-description)]"
              >{{ ic.name }}</span
            >
          </button>
        </div>
        <Empty v-else description="无匹配图标" class="py-12" />
      </Spin>
    </Space>
  </ToolShell>
</template>

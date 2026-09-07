<script setup lang="ts">
import type { DbConnection } from '@/api/screw/model';

import { computed, onMounted, ref } from 'vue';

import { connectionList } from '@/api/screw';
import { Select } from 'antdv-next';

const props = withDefaults(
  defineProps<{
    /** 是否允许清空 */
    allowClear?: boolean;
    /** 是否禁用 */
    disabled?: boolean;
    /** 占位文案 */
    placeholder?: string;
    /** 当前选中连接 id（配合 v-model:value 使用） */
    value?: number;
  }>(),
  {
    value: undefined,
    placeholder: '请选择连接',
    allowClear: true,
    disabled: false,
  },
);

const emit = defineEmits<{
  change: [value?: number];
  'update:value': [value?: number];
}>();

const loading = ref(false);
const connections = ref<DbConnection[]>([]);

/** 选项文案：连接名（类型 · host/database），便于区分同名连接 */
const options = computed(() =>
  connections.value.map((item) => ({
    label: `${item.name}（${item.dbType} · ${item.host}/${item.database}）`,
    value: item.id,
  })),
);

const selectedValue = computed<number | undefined>({
  get: () => props.value,
  set: (value) => {
    emit('update:value', value);
    emit('change', value);
  },
});

async function load() {
  loading.value = true;
  try {
    connections.value = await connectionList();
  } finally {
    loading.value = false;
  }
}

/** 下拉打开时刷新一次，保证新增连接后选项最新 */
function handleDropdownVisibleChange(open: boolean) {
  if (open) {
    load();
  }
}

onMounted(load);

defineExpose({ reload: load });
</script>

<template>
  <Select
    v-model:value="selectedValue"
    :allow-clear="allowClear"
    :disabled="disabled"
    :loading="loading"
    :options="options"
    :placeholder="placeholder"
    option-filter-prop="label"
    show-search
    @dropdown-visible-change="handleDropdownVisibleChange"
  />
</template>

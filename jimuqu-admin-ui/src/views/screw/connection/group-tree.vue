<script setup lang="ts">
import type { ConnectionGroup } from '@/api/screw/model';

import { computed } from 'vue';

import {
  AppstoreOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@antdv-next/icons';
import { Button, Empty, Popconfirm, Space } from 'antdv-next';

defineOptions({ inheritAttrs: false });

const props = withDefaults(defineProps<Props>(), {
  counts: () => ({}),
  total: 0,
  loading: false,
});

const emit = defineEmits<{
  add: [];
  edit: [group: ConnectionGroup];
  reload: [];
  remove: [group: ConnectionGroup];
  select: [key: number | string];
}>();

interface Props {
  groups: ConnectionGroup[];
  /** 分组 id -> 组内连接数 */
  counts?: Record<number, number>;
  /** 全部连接总数 */
  total?: number;
  loading?: boolean;
}

/** 'all' 或分组 id */
const selected = defineModel<number | string>({ default: 'all' });

const displayGroups = computed(() => {
  const sorted = [...props.groups].sort(
    (a, b) => (a.sort ?? 0) - (b.sort ?? 0),
  );
  return sorted;
});

function handleSelect(key: number | string) {
  selected.value = key;
  emit('select', key);
}
</script>

<template>
  <div :class="$attrs.class">
    <div
      class="bg-background flex h-full flex-col overflow-hidden rounded-[var(--ant-border-radius-lg)]"
    >
      <div
        class="bg-background flex items-center justify-between px-[8px] py-[6px]"
      >
        <span class="pl-[8px] text-[14px] font-medium">连接分组</span>
        <Space :size="2">
          <a-button
            :disabled="loading"
            size="small"
            type="text"
            @click="emit('add')"
          >
            <template #icon><PlusOutlined /></template>
            新增
          </a-button>
          <a-button
            :disabled="loading"
            size="small"
            type="text"
            @click="emit('reload')"
          >
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </Space>
      </div>

      <div class="flex-1 overflow-y-auto px-[8px] pb-[8px]">
        <!-- 全部连接 -->
        <div
          class="group-item cursor-pointer rounded-[6px] px-[10px] py-[8px] transition-colors"
          :class="{
            'text-primary bg-[var(--ant-color-primary-bg)]': selected === 'all',
          }"
          role="button"
          tabindex="0"
          @click="handleSelect('all')"
        >
          <div class="flex items-center justify-between">
            <span class="flex items-center gap-[6px] text-[13px]">
              <AppstoreOutlined />
              全部连接
            </span>
            <span
              class="rounded-full px-[6px] text-[12px] leading-[18px]"
              :class="
                selected === 'all'
                  ? 'bg-[var(--ant-color-primary)] text-white'
                  : 'bg-[var(--ant-color-fill-secondary)]'
              "
            >
              {{ total }}
            </span>
          </div>
        </div>

        <div
          v-if="displayGroups.length > 0"
          class="my-[8px] border-t border-[var(--ant-color-split)]"
        />

        <!-- 分组列表 -->
        <template v-if="displayGroups.length > 0">
          <div
            v-for="group in displayGroups"
            :key="group.id"
            class="group-item cursor-pointer rounded-[6px] px-[10px] py-[8px] transition-colors"
            :class="{
              'text-primary bg-[var(--ant-color-primary-bg)]':
                selected === group.id,
            }"
            role="button"
            tabindex="0"
            @click="handleSelect(group.id!)"
          >
            <div class="flex items-center justify-between">
              <span
                class="overflow-hidden text-[13px] text-ellipsis whitespace-nowrap"
                :title="group.name"
              >
                {{ group.name }}
              </span>
              <Space :size="0" @click.stop>
                <a-button size="small" type="text" @click="emit('edit', group)">
                  <template #icon><EditOutlined /></template>
                </a-button>
                <Popconfirm
                  :title="`确认删除分组[${group.name}]？`"
                  placement="left"
                  @confirm="emit('remove', group)"
                >
                  <a-button danger size="small" type="text"> 删除 </a-button>
                </Popconfirm>
              </Space>
            </div>
            <div
              class="mt-[4px] text-[12px] text-[var(--ant-color-text-description)]"
            >
              {{ counts[group.id!] ?? 0 }} 个连接
            </div>
          </div>
        </template>

        <Empty
          v-else-if="!loading"
          description="暂无分组"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
          class="mt-6"
        >
          <Button size="small" type="primary" @click="emit('add')">
            新建分组
          </Button>
        </Empty>
      </div>
    </div>
  </div>
</template>

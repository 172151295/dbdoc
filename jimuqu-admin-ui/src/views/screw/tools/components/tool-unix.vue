<script setup lang="ts">
import { computed, ref } from 'vue';

import { Descriptions, DescriptionsItem, Input, InputNumber } from 'antdv-next';
import dayjs from 'dayjs';

import ToolShell from './ToolShell.vue';

const seconds = ref<null | number>(Math.floor(Date.now() / 1000));
const dateStr = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'));

const fromTs = computed(() => {
  if (seconds.value == null || Number.isNaN(seconds.value)) {
    return '-';
  }
  return dayjs(seconds.value * 1000).format('YYYY-MM-DD HH:mm:ss');
});

const fromDate = computed(() => {
  const d = dayjs(dateStr.value);
  return d.isValid() ? String(d.unix()) : '-';
});

const dayjsStr = computed(() =>
  seconds.value == null || Number.isNaN(seconds.value)
    ? '-'
    : dayjs(seconds.value * 1000).toISOString(),
);
</script>

<template>
  <ToolShell
    title="Unix 时间戳转换"
    description="秒级/毫秒级时间戳 ↔ 本地时间，实时换算"
  >
    <div class="flex max-w-2xl flex-col gap-6">
      <div
        class="rounded-lg border border-[var(--ant-color-border-secondary)] p-4"
      >
        <div class="mb-3 text-[14px] font-medium">① 时间戳 → 时间</div>
        <InputNumber
          v-model:value="seconds"
          style="width: 240px"
          :controls="false"
          placeholder="Unix 秒"
        />
        <div class="mt-2 text-[13px] text-[var(--ant-color-text-secondary)]">
          {{ seconds == null ? '-' : seconds * 1000 }} ms
        </div>
        <Descriptions :column="1" size="small" class="mt-3" bordered>
          <DescriptionsItem label="本地时间">{{ fromTs }}</DescriptionsItem>
          <DescriptionsItem label="ISO 8601">{{ dayjsStr }}</DescriptionsItem>
        </Descriptions>
      </div>
      <div
        class="rounded-lg border border-[var(--ant-color-border-secondary)] p-4"
      >
        <div class="mb-3 text-[14px] font-medium">② 时间 → 时间戳</div>
        <Input
          v-model:value="dateStr"
          style="width: 240px"
          placeholder="YYYY-MM-DD HH:mm:ss"
        />
        <div class="mt-2 text-[13px] text-[var(--ant-color-text-secondary)]">
          Unix 秒：<b>{{ fromDate }}</b>
        </div>
      </div>
      <div class="flex gap-3">
        <button
          class="text-[13px] text-[var(--ant-color-link)]"
          @click="seconds = Math.floor(Date.now() / 1000)"
        >
          填入当前时间戳
        </button>
        <button
          class="text-[13px] text-[var(--ant-color-link)]"
          @click="dateStr = dayjs().format('YYYY-MM-DD HH:mm:ss')"
        >
          填入当前时间
        </button>
      </div>
    </div>
  </ToolShell>
</template>

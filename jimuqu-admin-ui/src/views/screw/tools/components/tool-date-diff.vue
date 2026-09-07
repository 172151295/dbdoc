<script setup lang="ts">
import { computed, ref } from 'vue';

import { Descriptions, DescriptionsItem, Input } from 'antdv-next';
import dayjs from 'dayjs';

import ToolShell from './ToolShell.vue';

const start = ref(dayjs().subtract(3, 'day').format('YYYY-MM-DD HH:mm:ss'));
const end = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'));

const diff = computed(() => {
  const a = dayjs(start.value);
  const b = dayjs(end.value);
  if (!a.isValid() || !b.isValid()) {
    return null;
  }
  const ms = Math.abs(b.diff(a));
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const h = Math.floor(m / 60);
  const d = Math.floor(h / 24);
  const w = Math.floor(d / 7);
  return {
    ms,
    s,
    m,
    h,
    d,
    w,
    ymd: `${Math.floor(d / 365)} 年 ${Math.floor((d % 365) / 30)} 月 ${d % 30} 天`,
    hms: `${h} 时 ${m % 60} 分 ${s % 60} 秒`,
    reversed: b.isBefore(a),
  };
});
</script>

<template>
  <ToolShell
    title="时间差计算"
    description="两个时间点之差（天/时/分/秒/毫秒）"
  >
    <div class="flex max-w-2xl flex-col gap-4">
      <div class="flex flex-wrap items-center gap-2">
        <Input
          v-model:value="start"
          style="width: 220px"
          placeholder="开始时间"
        />
        <span class="text-[var(--ant-color-text-secondary)]">→</span>
        <Input
          v-model:value="end"
          style="width: 220px"
          placeholder="结束时间"
        />
      </div>
      <div
        v-if="diff"
        class="text-[13px] text-[var(--ant-color-text-description)]"
      >
        {{ diff.reversed ? '（开始时间晚于结束时间，已取绝对值）' : '' }}
      </div>
      <Descriptions v-if="diff" :column="1" size="small" bordered>
        <DescriptionsItem label="相差毫秒">{{
          diff.ms.toLocaleString()
        }}</DescriptionsItem>
        <DescriptionsItem label="相差秒">{{
          diff.s.toLocaleString()
        }}</DescriptionsItem>
        <DescriptionsItem label="相差分钟">{{
          diff.m.toLocaleString()
        }}</DescriptionsItem>
        <DescriptionsItem label="相差小时">{{
          diff.h.toLocaleString()
        }}</DescriptionsItem>
        <DescriptionsItem label="相差天数">{{
          diff.d.toLocaleString()
        }}</DescriptionsItem>
        <DescriptionsItem label="相差周数">{{
          diff.w.toLocaleString()
        }}</DescriptionsItem>
        <DescriptionsItem label="年月日表达">{{ diff.ymd }}</DescriptionsItem>
        <DescriptionsItem label="时分秒表达">{{ diff.hms }}</DescriptionsItem>
      </Descriptions>
      <div v-else class="text-[13px] text-[var(--ant-color-error)]">
        时间格式无效，请使用 YYYY-MM-DD HH:mm:ss
      </div>
    </div>
  </ToolShell>
</template>

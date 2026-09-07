<script setup lang="ts">
import { computed, ref } from 'vue';

import { TinyColor } from '@ctrl/tinycolor';
import { Descriptions, DescriptionsItem, InputNumber, Space } from 'antdv-next';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const r = ref(22);
const g = ref(119);
const b = ref(255);

const color = computed(
  () => new TinyColor({ r: r.value, g: g.value, b: b.value }),
);
const hex = computed(() => color.value.toHexString().toUpperCase());
const hsl = computed(() => {
  const h = color.value.toHsl();
  return `hsl(${Math.round(h.h)}, ${Math.round(h.s * 100)}%, ${Math.round(h.l * 100)}%)`;
});
const cmyk = computed(() => {
  const c = color.value.toCmyk();
  return `cmyk(${Math.round(c.c)}%, ${Math.round(c.m)}%, ${Math.round(c.y)}%, ${Math.round(c.k)}%)`;
});

async function copyHex() {
  if (await copyText(hex.value)) {
    window.message.success(`已复制 ${hex.value}`);
  }
}

function onColorPick(e: Event) {
  const c = new TinyColor((e.target as HTMLInputElement).value);
  r.value = Math.round(c.r);
  g.value = Math.round(c.g);
  b.value = Math.round(c.b);
}
</script>

<template>
  <ToolShell title="RGB 颜色转换" description="RGB ↔ HEX / HSL / CMYK 实时联动">
    <div class="flex max-w-2xl flex-col gap-4">
      <div
        class="h-24 rounded-lg border border-[var(--ant-color-border)]"
        :style="{ background: hex }"
      />
      <Space wrap>
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >R</span
        >
        <InputNumber
          v-model:value="r"
          :min="0"
          :max="255"
          style="width: 90px"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >G</span
        >
        <InputNumber
          v-model:value="g"
          :min="0"
          :max="255"
          style="width: 90px"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >B</span
        >
        <InputNumber
          v-model:value="b"
          :min="0"
          :max="255"
          style="width: 90px"
        />
        <input
          v-model="hex"
          type="color"
          class="h-8 w-12 cursor-pointer rounded border"
          @change="onColorPick"
        />
      </Space>
      <Descriptions :column="1" size="small" bordered>
        <DescriptionsItem label="HEX">
          <button
            class="font-mono text-[var(--ant-color-link)]"
            @click="copyHex"
          >
            {{ hex }}
          </button>
        </DescriptionsItem>
        <DescriptionsItem label="HSL">{{ hsl }}</DescriptionsItem>
        <DescriptionsItem label="CMYK">{{ cmyk }}</DescriptionsItem>
      </Descriptions>
    </div>
  </ToolShell>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

import { Button, Input, InputNumber, Select, Space } from 'antdv-next';
import QRCode from 'qrcode';

import { downloadDataUrl } from '../utils';
import ToolShell from './ToolShell.vue';

const text = ref('https://example.com');
const size = ref(256);
const errorLevel = ref<'H' | 'L' | 'M' | 'Q'>('M');
const dataUrl = ref('');

async function render() {
  if (!text.value) {
    dataUrl.value = '';
    return;
  }
  try {
    dataUrl.value = await QRCode.toDataURL(text.value, {
      width: size.value,
      margin: 2,
      errorCorrectionLevel: errorLevel.value,
      color: { dark: '#000000', light: '#ffffff' },
    });
  } catch {
    window.message.error('二维码生成失败');
  }
}

function download() {
  if (dataUrl.value) {
    downloadDataUrl(dataUrl.value, `qrcode-${Date.now()}.png`);
  }
}

watch([text, size, errorLevel], render, { immediate: true });
</script>

<template>
  <ToolShell title="二维码生成器" description="本地生成二维码 PNG（qrcode 库）">
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <Input
          v-model:value="text"
          placeholder="输入文本/URL"
          style="width: 340px"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >尺寸</span
        >
        <InputNumber
          v-model:value="size"
          :min="128"
          :max="1024"
          :step="32"
          style="width: 90px"
        />
        <Select
          v-model:value="errorLevel"
          style="width: 90px"
          :options="[
            { value: 'L', label: 'L 低' },
            { value: 'M', label: 'M 中' },
            { value: 'Q', label: 'Q 较高' },
            { value: 'H', label: 'H 高' },
          ]"
        />
      </Space>
      <div class="flex flex-col items-start gap-3">
        <img
          v-if="dataUrl"
          :src="dataUrl"
          alt="QR"
          class="rounded-md border border-[var(--ant-color-border)] bg-white p-2"
          :style="{ width: `${Math.min(size, 320)}px` }"
        />
        <Button type="primary" :disabled="!dataUrl" @click="download"
          >下载 PNG</Button
        >
      </div>
    </Space>
  </ToolShell>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import { downloadByData } from '@/utils/file/download';
import { Button, Input, RadioGroup, Space } from 'antdv-next';

import ToolShell from './ToolShell.vue';

const text = ref('DB');
const bg = ref('#1677ff');
const fg = ref('#ffffff');
const shape = ref<'circle' | 'round' | 'square'>('round');
const canvasRef = ref<HTMLCanvasElement>();

function draw() {
  const canvas = canvasRef.value;
  if (!canvas) {
    return;
  }
  const S = 256;
  canvas.width = S;
  canvas.height = S;
  const ctx = canvas.getContext('2d')!;
  ctx.clearRect(0, 0, S, S);
  const r =
    shape.value === 'circle' ? S / 2 : shape.value === 'round' ? S * 0.2 : 0;
  ctx.beginPath();
  ctx.roundRect(0, 0, S, S, r);
  ctx.fillStyle = bg.value;
  ctx.fill();
  ctx.fillStyle = fg.value;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const chars = Array.from(text.value.trim() || '?').slice(0, 2);
  const fontSize = chars.length > 1 ? S * 0.42 : S * 0.56;
  ctx.font = `bold ${fontSize}px "Microsoft YaHei", "PingFang SC", sans-serif`;
  ctx.fillText(chars.join(''), S / 2, S / 2 + fontSize * 0.05);
}

/** 将 PNG Blob 封装为 ICO 文件（PNG 压缩格式，Windows Vista+ 支持） */
function buildIco(png: Blob): Promise<Blob> {
  return png.arrayBuffer().then((buf) => {
    const pngBytes = new Uint8Array(buf);
    const header = new Uint8Array(6 + 16);
    const dv = new DataView(header.buffer);
    dv.setUint16(0, 0, true); // reserved
    dv.setUint16(2, 1, true); // type: icon
    dv.setUint16(4, 1, true); // count
    // ICONDIRENTRY
    header[6] = 0; // width 256 → 0
    header[7] = 0; // height 256 → 0
    header[8] = 0; // palette
    header[9] = 0; // reserved
    dv.setUint16(10, 1, true); // planes
    dv.setUint16(12, 32, true); // bpp
    dv.setUint32(14, pngBytes.length, true); // size
    dv.setUint32(18, 22, true); // offset
    return new Blob([header, pngBytes], { type: 'image/x-icon' });
  });
}

async function downloadIco() {
  const canvas = canvasRef.value;
  if (!canvas) {
    return;
  }
  const png = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/png'),
  );
  if (!png) {
    window.message.error('生成失败');
    return;
  }
  const ico = await buildIco(png);
  downloadByData(ico, `icon-${Date.now()}.ico`, 'image/x-icon');
}

function downloadPng() {
  const canvas = canvasRef.value;
  if (!canvas) {
    return;
  }
  canvas.toBlob((blob) => {
    if (blob) {
      downloadByData(blob, `icon-${Date.now()}.png`, 'image/png');
    }
  }, 'image/png');
}
</script>

<template>
  <ToolShell
    title="Ico 图标生成"
    description="文字/配色 → 256×256 ICO 图标（Canvas 绘制，含 PNG 预览）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <Input
          v-model:value="text"
          placeholder="1-2 个字符"
          style="width: 120px"
          @change="draw"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >背景</span
        >
        <input
          v-model="bg"
          type="color"
          class="h-8 w-10 cursor-pointer rounded border"
          @change="draw"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >前景</span
        >
        <input
          v-model="fg"
          type="color"
          class="h-8 w-10 cursor-pointer rounded border"
          @change="draw"
        />
        <RadioGroup
          v-model:value="shape"
          option-type="button"
          :options="[
            { label: '圆角', value: 'round' },
            { label: '圆形', value: 'circle' },
            { label: '方形', value: 'square' },
          ]"
          @change="draw"
        />
      </Space>
      <canvas
        ref="canvasRef"
        class="rounded-md border border-[var(--ant-color-border)]"
        style="width: 128px; height: 128px"
      />
      <Space>
        <Button type="primary" @click="downloadIco">下载 .ico</Button>
        <Button @click="downloadPng">下载 .png</Button>
      </Space>
    </Space>
  </ToolShell>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue';

import { Button, Input, Select, Space } from 'antdv-next';

import { downloadDataUrl } from '../utils';
import ToolShell from './ToolShell.vue';

const STYLES: { label: string; value: string }[] = [
  { value: 'ink', label: '墨韵' },
  { value: 'neon', label: '霓虹' },
  { value: 'gradient', label: '渐变' },
  { value: 'minimal', label: '极简' },
];

const text = ref('山高水长');
const style = ref('ink');
const canvasRef = ref<HTMLCanvasElement>();

function draw() {
  const canvas = canvasRef.value;
  if (!canvas) {
    return;
  }
  const W = 720;
  const H = 360;
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d')!;
  const s = text.value.trim() || '文字物语';
  const words = s.split(/[\s,，。；;]+/).filter(Boolean);
  const content = words.length ? words : [s];
  const fontSize = Math.min(
    96,
    Math.floor(W / Math.max(...content.map((w) => Array.from(w).length))),
  );

  // 背景
  const bg =
    style.value === 'neon' || style.value === 'ink'
      ? '#0f1420'
      : style.value === 'gradient'
        ? '#f7f8fc'
        : '#fafafa';
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';

  const lineHeight = fontSize * 1.5;
  const startY = H / 2 - ((content.length - 1) * lineHeight) / 2;

  content.forEach((line, i) => {
    const y = startY + i * lineHeight;
    if (style.value === 'ink') {
      // 多层阴影模拟墨迹
      ctx.font = `900 ${fontSize}px "Microsoft YaHei", "PingFang SC", sans-serif`;
      for (let k = 6; k >= 1; k--) {
        ctx.fillStyle = `rgba(255,255,255,${0.04 * k})`;
        ctx.fillText(line, W / 2 + k * 2, y + k * 2);
      }
      ctx.fillStyle = '#f2efe6';
      ctx.fillText(line, W / 2, y);
      ctx.fillStyle = 'rgba(0,0,0,0.15)';
      ctx.fillRect(
        W / 2 - ctx.measureText(line).width / 2 - 16,
        y + fontSize * 0.55,
        ctx.measureText(line).width + 32,
        3,
      );
    } else if (style.value === 'neon') {
      ctx.font = `800 ${fontSize}px "Microsoft YaHei", "PingFang SC", sans-serif`;
      ctx.shadowColor = '#22d3ee';
      ctx.shadowBlur = 30;
      ctx.fillStyle = '#a5f3fc';
      ctx.fillText(line, W / 2, y);
      ctx.shadowColor = '#c084fc';
      ctx.shadowBlur = 18;
      ctx.fillText(line, W / 2, y);
      ctx.shadowBlur = 0;
    } else if (style.value === 'gradient') {
      ctx.font = `800 ${fontSize}px "Microsoft YaHei", "PingFang SC", sans-serif`;
      const g = ctx.createLinearGradient(0, y - fontSize, W, y + fontSize);
      g.addColorStop(0, '#6366f1');
      g.addColorStop(0.5, '#ec4899');
      g.addColorStop(1, '#f59e0b');
      ctx.fillStyle = g;
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 4;
      ctx.strokeText(line, W / 2, y);
      ctx.fillText(line, W / 2, y);
    } else {
      // minimal
      ctx.font = `600 ${fontSize}px "Microsoft YaHei", "PingFang SC", sans-serif`;
      ctx.fillStyle = '#111111';
      ctx.fillText(line, W / 2, y);
      const w = ctx.measureText(line).width;
      ctx.fillStyle = '#111111';
      ctx.fillRect(W / 2 - w / 2, y + fontSize * 0.55, w, 2);
    }
  });
}

function download() {
  const canvas = canvasRef.value;
  if (!canvas) {
    return;
  }
  downloadDataUrl(canvas.toDataURL('image/png'), `文字物语-${Date.now()}.png`);
}

nextTick(draw);
</script>

<template>
  <ToolShell
    title="文字物语"
    description="将文字生成艺术海报图片（Canvas 绘制，可下载 PNG）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <Input
          v-model:value="text"
          placeholder="输入文字（空格/逗号分隔自动换行）"
          style="width: 320px"
          @change="draw"
        />
        <Select
          v-model:value="style"
          :options="STYLES"
          style="width: 140px"
          @change="draw"
        />
        <Button type="primary" @click="download">下载 PNG</Button>
      </Space>
      <div
        class="flex justify-center rounded-lg border border-[var(--ant-color-border)] bg-[#0f1420] p-4"
      >
        <canvas
          ref="canvasRef"
          class="max-w-full rounded-md shadow"
          style="width: 720px; max-width: 100%"
        />
      </div>
    </Space>
  </ToolShell>
</template>

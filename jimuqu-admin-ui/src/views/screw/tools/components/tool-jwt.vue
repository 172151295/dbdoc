<script setup lang="ts">
import { computed, ref } from 'vue';

import { Button, Empty, Input, Space, Tag } from 'antdv-next';
import dayjs from 'dayjs';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const token = ref('');
const output = ref<null | { exp?: number; header: string; payload: string }>(
  null,
);

function b64urlToJson(s: string): string {
  const base64 = s.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(
    base64.length + ((4 - (base64.length % 4)) % 4),
    '=',
  );
  const bin = atob(padded);
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  const json = new TextDecoder().decode(bytes);
  return JSON.stringify(JSON.parse(json), null, 2);
}

function run() {
  const parts = token.value.trim().split('.');
  if (parts.length < 3) {
    window.message.error('不是合法的 JWT（应含 3 段，用 . 分隔）');
    output.value = null;
    return;
  }
  try {
    const header = b64urlToJson(parts[0]!);
    const payload = b64urlToJson(parts[1]!);
    const parsed = JSON.parse(payload);
    output.value = {
      header,
      payload,
      exp: typeof parsed.exp === 'number' ? parsed.exp : undefined,
    };
  } catch (e) {
    window.message.error(
      `解析失败：${e instanceof Error ? e.message : String(e)}`,
    );
    output.value = null;
  }
}

const remaining = computed(() => {
  if (!output.value?.exp) {
    return null;
  }
  const sec = output.value.exp - dayjs().unix();
  return sec >= 0
    ? `剩余 ${Math.floor(sec / 3600)} 小时 ${Math.floor((sec % 3600) / 60)} 分 ${sec % 60} 秒`
    : '已过期';
});

async function copyPayload() {
  if (output.value && (await copyText(output.value.payload))) {
    window.message.success('已复制 Payload');
  }
}
</script>

<template>
  <ToolShell
    title="JWT 解码器"
    description="本地解析 JWT 的 Header / Payload（不校验签名）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Input.TextArea
        v-model:value="token"
        :rows="4"
        placeholder="粘贴 JWT（eyJhbGciOi...）..."
      />
      <Space>
        <Button type="primary" @click="run">解析</Button>
        <Tag
          v-if="remaining"
          :color="remaining.includes('已过期') ? 'red' : 'green'"
          >{{ remaining }}</Tag
        >
      </Space>
      <div v-if="output" class="grid w-full gap-4 md:grid-cols-2">
        <div>
          <div class="mb-1 text-[13px] font-medium">Header</div>
          <pre
            class="m-0 max-h-64 overflow-auto rounded-md bg-[var(--ant-color-fill-quaternary)] p-3 text-[12px]"
            >{{ output.header }}</pre>
        </div>
        <div>
          <div class="mb-1 text-[13px] font-medium">Payload</div>
          <pre
            class="m-0 max-h-64 overflow-auto rounded-md bg-[var(--ant-color-fill-quaternary)] p-3 text-[12px]"
            >{{ output.payload }}</pre>
        </div>
      </div>
      <Empty v-else description="解析后可查看 Header 与 Payload" />
      <Button :disabled="!output" @click="copyPayload">复制 Payload</Button>
    </Space>
  </ToolShell>
</template>

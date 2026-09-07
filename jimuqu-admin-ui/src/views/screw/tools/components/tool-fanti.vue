<script setup lang="ts">
import { ref } from 'vue';

import { Button, Input, RadioGroup, Space } from 'antdv-next';
import { Converter } from 'opencc-js';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const mode = ref<'s2t' | 's2twp' | 't2s'>('s2t');
const input = ref('');
const output = ref('');

// 预建转换器（懒建，避免首屏开销）
let s2t: null | ReturnType<typeof Converter> = null;
let t2s: null | ReturnType<typeof Converter> = null;
let s2twp: null | ReturnType<typeof Converter> = null;

function ensure() {
  s2t ??= Converter({ from: 'cn', to: 'tw' });
  t2s ??= Converter({ from: 'tw', to: 'cn' });
  s2twp ??= Converter({ from: 'cn', to: 'twp' });
}

function run() {
  ensure();
  output.value =
    mode.value === 's2t'
      ? s2t!(input.value)
      : mode.value === 't2s'
        ? t2s!(input.value)
        : s2twp!(input.value);
}

async function copy() {
  if (await copyText(output.value)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell
    title="繁简转换"
    description="基于 OpenCC 数据，支持简体⇄繁体及台湾惯用词"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <RadioGroup
        v-model:value="mode"
        option-type="button"
        :options="[
          { label: '简体 → 繁体', value: 's2t' },
          { label: '繁体 → 简体', value: 't2s' },
          { label: '简体 → 繁体（台湾惯用）', value: 's2twp' },
        ]"
      />
      <Input.TextArea
        v-model:value="input"
        :rows="6"
        placeholder="输入文本..."
      />
      <Button type="primary" @click="run">转换</Button>
      <Input.TextArea
        v-model:value="output"
        :rows="6"
        placeholder="结果..."
        readonly
      />
      <Button :disabled="!output" @click="copy">复制结果</Button>
    </Space>
  </ToolShell>
</template>

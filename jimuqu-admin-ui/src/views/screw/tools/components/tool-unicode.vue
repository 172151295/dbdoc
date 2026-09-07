<script setup lang="ts">
import { ref } from 'vue';

import { Button, Input, RadioGroup, Space } from 'antdv-next';

import { copyText, fromUnicodeEsc, toUnicodeEsc } from '../utils';
import ToolShell from './ToolShell.vue';

const mode = ref<'decode' | 'encode'>('encode');
const input = ref('');
const output = ref('');

function run() {
  output.value =
    mode.value === 'encode'
      ? toUnicodeEsc(input.value)
      : fromUnicodeEsc(input.value);
}

function swap() {
  input.value = output.value;
  output.value = '';
  mode.value = mode.value === 'encode' ? 'decode' : 'encode';
  run();
}

async function copy() {
  if (await copyText(output.value)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell title="Unicode 中文互转" description="中文 ↔ \\uXXXX 转义">
    <Space direction="vertical" size="middle" class="w-full">
      <RadioGroup
        v-model:value="mode"
        option-type="button"
        :options="[
          { label: '中文 → \\uXXXX', value: 'encode' },
          { label: '\\uXXXX → 中文', value: 'decode' },
        ]"
      />
      <Input.TextArea
        v-model:value="input"
        :rows="6"
        placeholder="输入内容..."
      />
      <Space>
        <Button type="primary" @click="run">转换</Button>
        <Button @click="swap">⇅ 互换</Button>
      </Space>
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

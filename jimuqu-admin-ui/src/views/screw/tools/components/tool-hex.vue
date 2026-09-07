<script setup lang="ts">
import { ref } from 'vue';

import { Button, Input, RadioGroup, Space } from 'antdv-next';

import { copyText, hexToStr, strToHex } from '../utils';
import ToolShell from './ToolShell.vue';

const mode = ref<'decode' | 'encode'>('encode');
const input = ref('');
const output = ref('');

function run() {
  try {
    output.value =
      mode.value === 'encode' ? strToHex(input.value) : hexToStr(input.value);
  } catch (e) {
    window.message.error(e instanceof Error ? e.message : '转换失败');
  }
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
  <ToolShell
    title="Hex 编码/解码"
    description="文本 ↔ 十六进制（UTF-8 字节，大写输出）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <RadioGroup
        v-model:value="mode"
        option-type="button"
        :options="[
          { label: '编码（文本 → Hex）', value: 'encode' },
          { label: '解码（Hex → 文本）', value: 'decode' },
        ]"
      />
      <Input.TextArea
        v-model:value="input"
        :rows="6"
        placeholder="输入内容..."
      />
      <Space>
        <Button type="primary" @click="run">{{
          mode === 'encode' ? '编码' : '解码'
        }}</Button>
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

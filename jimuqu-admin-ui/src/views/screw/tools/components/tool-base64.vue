<script setup lang="ts">
import { ref } from 'vue';

import { Button, Input, RadioGroup, Space } from 'antdv-next';

import { base64ToUtf8, copyText, utf8ToBase64 } from '../utils';
import ToolShell from './ToolShell.vue';

const mode = ref<'decode' | 'encode'>('encode');
const input = ref('');
const output = ref('');

function run() {
  try {
    output.value =
      mode.value === 'encode'
        ? utf8ToBase64(input.value)
        : base64ToUtf8(input.value);
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
  <ToolShell title="Base64 编码/解码" description="UTF-8 安全，支持中文">
    <Space direction="vertical" size="middle" class="w-full">
      <RadioGroup
        v-model:value="mode"
        option-type="button"
        :options="[
          { label: '编码（文本 → Base64）', value: 'encode' },
          { label: '解码（Base64 → 文本）', value: 'decode' },
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
        <Button @click="swap">⇅ 互换（结果回填输入）</Button>
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

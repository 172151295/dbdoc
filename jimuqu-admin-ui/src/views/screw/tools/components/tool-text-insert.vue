<script setup lang="ts">
import { ref } from 'vue';

import { Button, Checkbox, Input, Space } from 'antdv-next';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const input = ref('');
const prefix = ref('');
const suffix = ref('');
const perLine = ref(true);
const output = ref('');

function run() {
  if (perLine.value) {
    output.value = input.value
      .split('\n')
      .map((line) => (line ? `${prefix.value}${line}${suffix.value}` : line))
      .join('\n');
  } else {
    output.value = `${prefix.value}${input.value}${suffix.value}`;
  }
}

async function copy() {
  if (await copyText(output.value)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell
    title="文本两端插入字符"
    description="给每行或整段文本统一加前后缀（引号、括号、标签等）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Input.TextArea
        v-model:value="input"
        :rows="6"
        placeholder="输入文本（多行则按行处理）..."
      />
      <Space wrap>
        <Input
          v-model:value="prefix"
          placeholder="前缀（如 &quot; ' ( <tag>）"
          style="width: 220px"
        />
        <Input
          v-model:value="suffix"
          placeholder="后缀（如 &quot; ' ) &lt;/tag&gt;）"
          style="width: 220px"
        />
        <Checkbox v-model:checked="perLine">逐行处理</Checkbox>
      </Space>
      <Button type="primary" @click="run">执行插入</Button>
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

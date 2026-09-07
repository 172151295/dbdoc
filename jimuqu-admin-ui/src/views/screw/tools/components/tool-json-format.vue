<script setup lang="ts">
import { ref } from 'vue';
import VueJsonPretty from 'vue-json-pretty';
import 'vue-json-pretty/lib/styles.css';

import { Button, Input, RadioGroup, Space } from 'antdv-next';

import { copyText, formatJson } from '../utils';
import ToolShell from './ToolShell.vue';

const input = ref('');
const indent = ref<2 | 4>(2);
const view = ref<'text' | 'tree'>('text');
const output = ref('');
const error = ref('');

function run() {
  try {
    output.value = formatJson(input.value, indent.value);
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
    output.value = '';
  }
}

function compress() {
  try {
    output.value = JSON.stringify(JSON.parse(input.value));
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
    output.value = '';
  }
}

async function copy() {
  if (await copyText(output.value)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell title="JSON 格式化" description="格式化 / 压缩 / 树形视图">
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <RadioGroup
          v-model:value="indent"
          option-type="button"
          :options="[
            { label: '2 空格', value: 2 },
            { label: '4 空格', value: 4 },
          ]"
        />
        <RadioGroup
          v-model:value="view"
          option-type="button"
          :options="[
            { label: '文本视图', value: 'text' },
            { label: '树形视图', value: 'tree' },
          ]"
        />
      </Space>
      <Input.TextArea
        v-model:value="input"
        :rows="8"
        placeholder="粘贴 JSON..."
      />
      <Space>
        <Button type="primary" @click="run">格式化</Button>
        <Button @click="compress">压缩</Button>
      </Space>
      <div v-if="view === 'text'">
        <Input.TextArea
          v-model:value="output"
          :rows="8"
          placeholder="结果..."
          readonly
        />
      </div>
      <div
        v-else-if="output"
        class="max-h-96 overflow-auto rounded-md bg-[var(--ant-color-fill-quaternary)] p-3"
      >
        <VueJsonPretty :data="JSON.parse(output)" :deep="3" show-line-number />
      </div>
      <div v-if="error" class="text-[13px] text-[var(--ant-color-error)]">
        {{ error }}
      </div>
      <Button :disabled="!output" @click="copy">复制结果</Button>
    </Space>
  </ToolShell>
</template>

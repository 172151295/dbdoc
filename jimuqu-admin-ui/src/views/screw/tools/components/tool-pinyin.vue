<script setup lang="ts">
import { ref } from 'vue';

import { Button, Input, RadioGroup, Space } from 'antdv-next';
import { pinyin } from 'pinyin-pro';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const input = ref('');
const toneType = ref<'none' | 'num' | 'symbol'>('symbol');
const pattern = ref<'first' | 'pinyin'>('pinyin');
const separator = ref('');
const output = ref('');

function run() {
  if (!input.value) {
    output.value = '';
    return;
  }
  output.value = pinyin(input.value, {
    toneType: toneType.value,
    pattern: pattern.value,
    type: 'string',
    separator: separator.value,
  });
}

async function copy() {
  if (await copyText(output.value)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell
    title="汉字转拼音"
    description="基于 pinyin-pro，支持声调符号/数字/无音调与首字母"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Input.TextArea
        v-model:value="input"
        :rows="6"
        placeholder="输入中文文本..."
      />
      <Space wrap>
        <RadioGroup
          v-model:value="toneType"
          option-type="button"
          :options="[
            { label: '声调符号 nǐ', value: 'symbol' },
            { label: '声调数字 ni3', value: 'num' },
            { label: '无音调 ni', value: 'none' },
          ]"
        />
        <RadioGroup
          v-model:value="pattern"
          option-type="button"
          :options="[
            { label: '全拼', value: 'pinyin' },
            { label: '首字母', value: 'first' },
          ]"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >分隔符</span
        >
        <Input
          v-model:value="separator"
          placeholder="默认空"
          style="width: 90px"
        />
      </Space>
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

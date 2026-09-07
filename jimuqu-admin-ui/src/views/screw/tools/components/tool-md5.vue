<script setup lang="ts">
import { computed, ref } from 'vue';

import { Button, Checkbox, Input, Space } from 'antdv-next';
import CryptoJS from 'crypto-js';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const input = ref('');
const uppercase = ref(false);
const bits16 = ref(false);

const md5 = computed(() => {
  const full = CryptoJS.MD5(input.value).toString(CryptoJS.enc.Hex);
  const base = bits16.value ? full.slice(8, 24) : full;
  return uppercase.value ? base.toUpperCase() : base;
});

async function copy() {
  if (await copyText(md5.value)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell
    title="MD5 文本加密"
    description="单向散列（32 位 Hex），支持大小写与 16 位截取"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Input.TextArea
        v-model:value="input"
        :rows="6"
        placeholder="输入要加密的文本..."
      />
      <Space wrap>
        <Checkbox v-model:checked="uppercase">大写输出</Checkbox>
        <Checkbox v-model:checked="bits16">16 位（截取中间）</Checkbox>
      </Space>
      <Input.TextArea
        :value="md5"
        :rows="3"
        placeholder="MD5 结果（实时）..."
        readonly
      />
      <Button :disabled="!md5" @click="copy">复制结果</Button>
    </Space>
  </ToolShell>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import { Button, Input, InputNumber, Space } from 'antdv-next';

import { copyText, rmbUpper } from '../utils';
import ToolShell from './ToolShell.vue';

const num = ref<null | number>(1234567.89);
const output = computed(() => {
  if (num.value == null || Number.isNaN(num.value)) {
    return '';
  }
  try {
    return rmbUpper(num.value);
  } catch {
    return '';
  }
});

async function copy() {
  if (await copyText(output.value)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell
    title="人民币大写转换"
    description="数字金额 → 中文大写（元角分，财务规范）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >金额</span
        >
        <InputNumber
          v-model:value="num"
          style="width: 260px"
          :precision="2"
          placeholder="请输入金额"
        />
      </Space>
      <Input.TextArea
        :value="output"
        :rows="3"
        placeholder="大写结果..."
        readonly
      />
      <Button :disabled="!output" @click="copy">复制结果</Button>
    </Space>
  </ToolShell>
</template>

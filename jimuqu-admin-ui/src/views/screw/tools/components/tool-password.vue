<script setup lang="ts">
import { ref } from 'vue';

import { Button, Checkbox, InputNumber, Slider, Space } from 'antdv-next';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const length = ref(16);
const count = ref(5);
const lower = ref(true);
const upper = ref(true);
const digits = ref(true);
const symbols = ref(false);
const excludeAmbiguous = ref(true);
const outputs = ref<string[]>([]);

const CHARS = {
  lower: 'abcdefghijklmnopqrstuvwxyz',
  upper: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
  digits: '0123456789',
  symbols: '!@#$%^&*()-_=+[]{};:,.<>?',
  ambiguous: '0O1lI|`\'"',
};

function generate() {
  const pool = [
    lower.value ? CHARS.lower : '',
    upper.value ? CHARS.upper : '',
    digits.value ? CHARS.digits : '',
    symbols.value ? CHARS.symbols : '',
  ]
    .join('')
    .replace(
      excludeAmbiguous.value
        ? new RegExp(`[${CHARS.ambiguous.replace(/[\\\]]/g, '\\$&')}]`, 'g')
        : /(?!)/,
      '',
    );
  if (!pool) {
    window.message.warning('至少选择一种字符类型');
    return;
  }
  const arr = new Uint32Array(length.value * count.value);
  crypto.getRandomValues(arr);
  const list: string[] = [];
  for (let i = 0; i < count.value; i++) {
    let s = '';
    for (let j = 0; j < length.value; j++) {
      const idx = arr[i * length.value + j] ?? 0;
      s += pool[idx % pool.length];
    }
    list.push(s);
  }
  outputs.value = list;
}

async function copyOne(p: string) {
  if (await copyText(p)) {
    window.message.success('已复制');
  }
}

async function copyAll() {
  if (outputs.value.length && (await copyText(outputs.value.join('\n')))) {
    window.message.success('已复制全部');
  }
}
</script>

<template>
  <ToolShell
    title="密码生成器"
    description="基于 crypto.getRandomValues 的安全随机密码"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >长度</span
        >
        <InputNumber
          v-model:value="length"
          :min="6"
          :max="64"
          style="width: 80px"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >生成条数</span
        >
        <InputNumber
          v-model:value="count"
          :min="1"
          :max="20"
          style="width: 80px"
        />
      </Space>
      <Slider v-model:value="length" :min="6" :max="64" class="max-w-md" />
      <Space wrap>
        <Checkbox v-model:checked="lower">小写 a-z</Checkbox>
        <Checkbox v-model:checked="upper">大写 A-Z</Checkbox>
        <Checkbox v-model:checked="digits">数字 0-9</Checkbox>
        <Checkbox v-model:checked="symbols">符号 !@#...</Checkbox>
        <Checkbox v-model:checked="excludeAmbiguous">排除易混字符</Checkbox>
      </Space>
      <Button type="primary" @click="generate">生成</Button>
      <div v-if="outputs.length" class="flex flex-col gap-2">
        <pre
          v-for="(p, i) in outputs"
          :key="i"
          class="m-0 cursor-pointer rounded-md bg-[var(--ant-color-fill-quaternary)] px-3 py-2 text-[14px]"
          :title="`复制第 ${i + 1} 条`"
          @click="copyOne(p)"
          >{{ p }}</pre>
      </div>
      <Button :disabled="!outputs.length" @click="copyAll">复制全部</Button>
    </Space>
  </ToolShell>
</template>

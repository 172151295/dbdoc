<script setup lang="ts">
import { ref } from 'vue';

import { Button, Checkbox, InputNumber, Space } from 'antdv-next';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const count = ref(5);
const uppercase = ref(false);
const noHyphen = ref(false);
const outputs = ref<string[]>([]);

function uuid(): string {
  const u = crypto.randomUUID();
  return uppercase.value ? u.toUpperCase() : u;
}

function generate() {
  outputs.value = Array.from({ length: count.value }, () => {
    const u = uuid();
    return noHyphen.value ? u.replace(/-/g, '') : u;
  });
}

async function copyAll() {
  if (outputs.value.length && (await copyText(outputs.value.join('\n')))) {
    window.message.success('已复制全部');
  }
}

async function copyOne(u: string) {
  if (await copyText(u)) {
    window.message.success('已复制');
  }
}
</script>

<template>
  <ToolShell
    title="UUID 生成器"
    description="基于 crypto.randomUUID 的 UUID v4"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >生成数量</span
        >
        <InputNumber
          v-model:value="count"
          :min="1"
          :max="100"
          style="width: 90px"
        />
        <Checkbox v-model:checked="uppercase">大写</Checkbox>
        <Checkbox v-model:checked="noHyphen">去连字符</Checkbox>
        <Button type="primary" @click="generate">生成</Button>
      </Space>
      <div v-if="outputs.length" class="flex flex-col gap-2">
        <div
          v-for="(u, i) in outputs"
          :key="i"
          class="cursor-pointer rounded-md bg-[var(--ant-color-fill-quaternary)] px-3 py-1.5 font-mono text-[13px]"
          title="点击复制"
          @click="copyOne(u)"
        >
          {{ u }}
        </div>
      </div>
      <Button :disabled="!outputs.length" @click="copyAll">复制全部</Button>
    </Space>
  </ToolShell>
</template>

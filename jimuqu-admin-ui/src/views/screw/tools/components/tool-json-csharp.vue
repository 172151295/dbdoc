<script setup lang="ts">
import { ref } from 'vue';

import { Button, Checkbox, Input, Space } from 'antdv-next';

import { copyText, toPascal } from '../utils';
import ToolShell from './ToolShell.vue';

const input = ref('');
const addJsonProperty = ref(false);
const output = ref('');
const error = ref('');

function csType(v: unknown, key: string): string {
  if (v === null || v === undefined) {
    return 'object';
  }
  if (Array.isArray(v)) {
    const inner = v.length ? v[0] : undefined;
    return `List<${inner === undefined || inner === null ? 'object' : csType(inner, key)}>`;
  }
  switch (typeof v) {
    case 'boolean':
      return 'bool';
    case 'number':
      return Number.isInteger(v)
        ? Math.abs(v) > 2147483647
          ? 'long'
          : 'int'
        : 'decimal';
    case 'string':
      return 'string';
    default:
      return 'object';
  }
}

function genClass(
  obj: Record<string, unknown>,
  name: string,
  out: string[],
  seen: Set<string>,
): void {
  let clsName = toPascal(name) || 'Root';
  while (seen.has(clsName)) {
    clsName += '_';
  }
  seen.add(clsName);
  out.push(`public class ${clsName}\n{`);
  for (const [k, v] of Object.entries(obj)) {
    const prop = toPascal(k) || 'Value';
    let type = csType(v, k);
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      genClass(v as Record<string, unknown>, k, out, seen);
      type = toPascal(k) || 'Object';
    } else if (
      Array.isArray(v) &&
      v.length &&
      v[0] &&
      typeof v[0] === 'object' &&
      !Array.isArray(v[0])
    ) {
      genClass(v[0] as Record<string, unknown>, k, out, seen);
      type = `List<${toPascal(k) || 'Object'}>`;
    }
    const attr = addJsonProperty.value ? `    [JsonProperty("${k}")]\n` : '';
    out.push(`${attr}    public ${type} ${prop} { get; set; }`);
  }
  out.push('}\n');
}

function run() {
  try {
    const data = JSON.parse(input.value);
    if (typeof data !== 'object' || data === null || Array.isArray(data)) {
      throw new Error('需为 JSON 对象（或对象数组会用 Root/Item 命名）');
    }
    const out: string[] = [];
    const seen = new Set<string>();
    if (addJsonProperty.value) {
      out.unshift('using Newtonsoft.Json;\n');
    }
    out.push('using System.Collections.Generic;\n');
    genClass(data, 'Root', out, seen);
    output.value = out.join('\n');
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
  <ToolShell
    title="JSON 转 C#"
    description="生成 C# 实体类（递归嵌套类，PascalCase 属性名）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Input.TextArea
        v-model:value="input"
        :rows="8"
        placeholder='粘贴 JSON 对象，如 {"name":"张三","age":30,"hobbies":["a","b"]}'
      />
      <Space wrap>
        <Checkbox v-model:checked="addJsonProperty"
          >生成 [JsonProperty] 特性（Newtonsoft）</Checkbox
        >
        <Button type="primary" @click="run">生成</Button>
      </Space>
      <Input.TextArea
        v-model:value="output"
        :rows="12"
        placeholder="C# 类代码..."
        readonly
      />
      <div v-if="error" class="text-[13px] text-[var(--ant-color-error)]">
        {{ error }}
      </div>
      <Button :disabled="!output" @click="copy">复制结果</Button>
    </Space>
  </ToolShell>
</template>

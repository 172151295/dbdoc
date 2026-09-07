<script setup lang="ts">
import { ref } from 'vue';

import {
  Button,
  Input,
  InputNumber,
  RadioGroup,
  Select,
  Space,
} from 'antdv-next';
import { format } from 'sql-formatter';

import { copyText } from '../utils';
import ToolShell from './ToolShell.vue';

const LANGUAGES = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'tsql', label: 'SQL Server (T-SQL)' },
  { value: 'plsql', label: 'Oracle (PL/SQL)' },
  { value: 'sqlite', label: 'SQLite' },
  { value: 'hive', label: 'Hive' },
  { value: 'spark', label: 'Spark SQL' },
  { value: 'bigquery', label: 'BigQuery' },
  { value: 'redshift', label: 'Redshift' },
  { value: 'snowflake', label: 'Snowflake' },
  { value: 'db2', label: 'DB2' },
  { value: 'n1ql', label: 'Couchbase N1QL' },
];

const input = ref('');
const language = ref('mysql');
const keywordCase = ref<'lower' | 'preserve' | 'upper'>('upper');
const indentStyle = ref<'standard' | 'tabularLeft' | 'tabularRight'>(
  'standard',
);
const tabWidth = ref(2);
const output = ref('');
const error = ref('');

function run() {
  try {
    output.value = format(input.value, {
      language: language.value as NonNullable<
        Parameters<typeof format>[1]
      >['language'],
      keywordCase: keywordCase.value,
      indentStyle: indentStyle.value,
      tabWidth: tabWidth.value,
      linesBetweenQueries: 1,
    });
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
    title="SQL 格式化"
    description="基于 sql-formatter，支持 11 种方言"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Space wrap>
        <Select
          v-model:value="language"
          :options="LANGUAGES"
          style="width: 200px"
        />
        <RadioGroup
          v-model:value="keywordCase"
          option-type="button"
          :options="[
            { label: '大写关键字', value: 'upper' },
            { label: '小写关键字', value: 'lower' },
            { label: '保持原样', value: 'preserve' },
          ]"
        />
        <span class="text-[13px] text-[var(--ant-color-text-secondary)]"
          >缩进</span
        >
        <InputNumber
          v-model:value="tabWidth"
          :min="2"
          :max="8"
          :step="2"
          style="width: 70px"
        />
      </Space>
      <Input.TextArea
        v-model:value="input"
        :rows="8"
        placeholder="粘贴 SQL..."
      />
      <Button type="primary" @click="run">格式化</Button>
      <Input.TextArea
        v-model:value="output"
        :rows="8"
        placeholder="结果..."
        readonly
      />
      <div v-if="error" class="text-[13px] text-[var(--ant-color-error)]">
        {{ error }}
      </div>
      <Button :disabled="!output" @click="copy">复制结果</Button>
    </Space>
  </ToolShell>
</template>

<script setup lang="ts">
import type {
  CodeGenerateRequest,
  CodeLanguage,
  TableBriefList,
} from '@/api/screw/model';

import { computed, ref, watch } from 'vue';

import { codeGenerate, metadataTables } from '@/api/screw';
import { Page } from '@/components';
import { downloadByData } from '@/utils/file/download';
import {
  Button,
  Card,
  Checkbox,
  CheckboxGroup,
  Form,
  FormItem,
  Input,
  InputSearch,
  Radio,
  RadioGroup,
  Space,
  Spin,
  Switch,
} from 'antdv-next';

import ConnectionSelect from '../components/connection-select.vue';

const form = ref<{
  connectionId?: number;
  language: CodeLanguage;
  lombok: boolean;
  namespace: string;
  packageName: string;
  swagger: boolean;
  tables: string[];
}>({
  connectionId: undefined,
  language: 'JAVA',
  packageName: 'com.example.entity',
  namespace: 'Example.Entities',
  lombok: true,
  swagger: false,
  tables: [],
});

const connectionId = computed({
  get: () => form.value.connectionId,
  set: (value: number | undefined) => {
    form.value.connectionId = value;
  },
});

const loading = ref(false);
/** 轻量表清单（仅表名，千表大库秒级返回；代码生成无需列信息） */
const tableBrief = ref<TableBriefList>();
const tables = computed(() => tableBrief.value?.tables ?? []);
const tableNames = computed(() => tables.value.map((t) => t.tableName));

// ── 表名搜索 + 渲染上限 ──
const searchKeyword = ref('');
/** 万表场景下一次性渲染上万复选框会卡死浏览器，仅展示匹配前 N 个 */
const DISPLAY_LIMIT = 500;
const filteredNames = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return tableNames.value;
  return tableNames.value.filter((n) => n.toLowerCase().includes(kw));
});
const shownNames = computed(() => filteredNames.value.slice(0, DISPLAY_LIMIT));
const hasMore = computed(() => filteredNames.value.length > DISPLAY_LIMIT);

const generating = ref(false);
const resultMap = ref<Record<string, string>>({});
const activeTable = ref<string>('');

const resultTables = computed(() => Object.keys(resultMap.value));
const activeCode = computed(() => resultMap.value[activeTable.value] ?? '');

watch(connectionId, async (value) => {
  loading.value = true;
  tableBrief.value = undefined;
  form.value.tables = [];
  resultMap.value = {};
  activeTable.value = '';
  try {
    if (value != null) {
      const brief = await metadataTables(value);
      tableBrief.value = brief;
      form.value.tables = (brief.tables ?? []).map((t) => t.tableName);
    }
  } finally {
    loading.value = false;
  }
});

async function handleGenerate() {
  if (form.value.connectionId == null) {
    window.message.warning('请先选择连接');
    return;
  }
  if (form.value.tables.length === 0) {
    window.message.warning('请至少选择一张要生成的表');
    return;
  }
  generating.value = true;
  try {
    const payload: CodeGenerateRequest = {
      connectionId: form.value.connectionId,
      language: form.value.language,
      packageName:
        form.value.language === 'JAVA' ? form.value.packageName : undefined,
      namespace:
        form.value.language === 'CSHARP' ? form.value.namespace : undefined,
      lombok: form.value.language === 'JAVA' ? form.value.lombok : undefined,
      swagger: form.value.swagger,
      tables: form.value.tables,
    };
    const result = await codeGenerate(payload);
    resultMap.value = result;
    const keys = Object.keys(result);
    activeTable.value = keys[0] ?? '';
    if (keys.length === 0) {
      window.message.warning('未生成任何代码');
    } else {
      window.message.success(`已生成 ${keys.length} 张表代码`);
    }
  } finally {
    generating.value = false;
  }
}

function handleCheckAll(value: boolean) {
  form.value.tables = value ? [...tableNames.value] : [];
}

const checkedCount = computed(() => form.value.tables.length);

async function handleCopy() {
  if (!activeCode.value) return;
  await navigator.clipboard.writeText(activeCode.value);
  window.message.success('已复制到剪贴板');
}

function handleDownloadCurrent() {
  if (!activeCode.value || !activeTable.value) return;
  const ext = form.value.language === 'JAVA' ? 'java' : 'cs';
  const fileName = `${activeTable.value}.${ext}`;
  const blob = new Blob([activeCode.value], {
    type: 'text/plain;charset=utf-8',
  });
  downloadByData(blob, fileName, 'text/plain;charset=utf-8');
}
</script>

<template>
  <Page :auto-content-height="true">
    <div class="flex h-full flex-col gap-4">
      <Card size="small">
        <div class="flex items-center gap-4">
          <span class="text-[14px] text-[var(--ant-color-text-secondary)]"
            >选择连接</span
          >
          <ConnectionSelect
            v-model:value="connectionId"
            class="max-w-[360px]"
            placeholder="请选择要生成代码的连接"
          />
        </div>
      </Card>

      <div class="bg-card flex-1 overflow-hidden rounded-lg">
        <div class="flex h-full flex-col">
          <div class="border-b border-[var(--ant-color-split)] px-4 py-3">
            <div class="text-[16px] font-medium">代码生成配置</div>
          </div>

          <Spin :spinning="loading" size="large">
            <div class="h-full overflow-auto p-4">
              <Form layout="vertical">
                <div class="grid grid-cols-2 gap-x-6 gap-y-4">
                  <FormItem label="语言" required>
                    <RadioGroup v-model:value="form.language">
                      <Radio value="JAVA">Java</Radio>
                      <Radio value="CSHARP">C#</Radio>
                    </RadioGroup>
                  </FormItem>
                  <FormItem v-if="form.language === 'JAVA'" label="包名">
                    <Input
                      v-model:value="form.packageName"
                      allow-clear
                      placeholder="com.example.entity"
                    />
                  </FormItem>
                  <FormItem v-if="form.language === 'CSHARP'" label="命名空间">
                    <Input
                      v-model:value="form.namespace"
                      allow-clear
                      placeholder="Example.Entities"
                    />
                  </FormItem>
                  <FormItem v-if="form.language === 'JAVA'" label="Lombok">
                    <Switch v-model:checked="form.lombok" />
                    <span
                      class="ml-2 text-[12px] text-[var(--ant-color-text-description)]"
                      >生成 Lombok 注解</span
                    >
                  </FormItem>
                  <FormItem label="Swagger 注解">
                    <Switch v-model:checked="form.swagger" />
                    <span
                      class="ml-2 text-[12px] text-[var(--ant-color-text-description)]"
                      >生成 Swagger 注解</span
                    >
                  </FormItem>
                </div>

                <FormItem label="要生成的表">
                  <div class="flex w-full flex-col gap-2">
                    <Checkbox
                      :checked="
                        checkedCount === tableNames.length &&
                        tableNames.length > 0
                      "
                      :indeterminate="
                        checkedCount > 0 && checkedCount < tableNames.length
                      "
                      :disabled="tableNames.length === 0"
                      @change="(e: any) => handleCheckAll(e.target.checked)"
                    >
                      全选（{{ checkedCount }}/{{ tableNames.length }}）
                    </Checkbox>
                    <InputSearch
                      v-model:value="searchKeyword"
                      allow-clear
                      placeholder="搜索表名"
                      size="small"
                      class="max-w-[360px]"
                    />
                    <CheckboxGroup v-model:value="form.tables">
                      <Space wrap>
                        <Checkbox
                          v-for="name in shownNames"
                          :key="name"
                          :value="name"
                        >
                          {{ name }}
                        </Checkbox>
                      </Space>
                    </CheckboxGroup>
                    <span
                      v-if="hasMore"
                      class="text-[12px] text-[var(--ant-color-text-description)]"
                    >
                      匹配 {{ filteredNames.length }} 个，仅显示前
                      {{ DISPLAY_LIMIT }} 个，请输入关键字缩小范围
                    </span>
                  </div>
                </FormItem>
              </Form>

              <div class="flex justify-end">
                <Button
                  :loading="generating"
                  type="primary"
                  @click="handleGenerate"
                  >生成代码</Button
                >
              </div>

              <div v-if="resultTables.length > 0" class="mt-6 flex gap-4">
                <div
                  class="bg-card w-[220px] shrink-0 rounded-lg border border-[var(--ant-color-split)]"
                >
                  <div
                    class="border-b border-[var(--ant-color-split)] px-3 py-2 text-[13px] font-medium"
                  >
                    生成结果（{{ resultTables.length }}）
                  </div>
                  <div class="max-h-[420px] overflow-auto p-2">
                    <div class="flex flex-col gap-1">
                      <Button
                        v-for="name in resultTables"
                        :key="name"
                        :type="activeTable === name ? 'primary' : 'default'"
                        size="small"
                        block
                        @click="activeTable = name"
                      >
                        {{ name }}
                      </Button>
                    </div>
                  </div>
                </div>
                <div
                  class="min-w-0 flex-1 overflow-hidden rounded-lg border border-[var(--ant-color-split)]"
                >
                  <div
                    class="flex items-center justify-between border-b border-[var(--ant-color-split)] bg-[var(--ant-color-fill-quaternary)] px-3 py-2"
                  >
                    <span class="text-[13px] font-medium">{{
                      activeTable
                    }}</span>
                    <Space>
                      <Button size="small" @click="handleCopy">复制</Button>
                      <Button size="small" @click="handleDownloadCurrent"
                        >下载</Button
                      >
                    </Space>
                  </div>
                  <pre
                    class="max-h-[420px] overflow-auto bg-[var(--ant-color-fill-quaternary)] p-4 text-[12px] leading-5"
                    >{{ activeCode }}</pre>
                </div>
              </div>
            </div>
          </Spin>
        </div>
      </div>
    </div>
  </Page>
</template>

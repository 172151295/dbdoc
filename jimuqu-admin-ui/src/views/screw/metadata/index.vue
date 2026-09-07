<script setup lang="ts">
import type { TableRelation } from '@/api/screw/model';

import { computed, ref } from 'vue';

import { metadataRelations } from '@/api/screw';
import { Page } from '@/components';
import { Card, Empty, Spin, Table, Tag } from 'antdv-next';

import ConnectionSelect from '../components/connection-select.vue';
import RelationGraph from './relation-graph.vue';

const connectionId = ref<number>();
const keyword = ref('');

const loading = ref(false);
const relations = ref<TableRelation[]>([]);

/** 表名过滤：命中子表或父表即保留 */
const filteredRelations = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) {
    return relations.value;
  }
  return relations.value.filter((item) =>
    [item.childTable, item.parentTable].some((table) =>
      (table ?? '').toLowerCase().includes(kw),
    ),
  );
});

/** 图边：同一外键（约束名 + 两端表）只画一条，避免复合外键多条重叠 */
const graphLinks = computed(() => {
  const seen = new Set<string>();
  const links: { source: string; target: string }[] = [];
  for (const item of filteredRelations.value) {
    const source = item.childTable;
    const target = item.parentTable;
    if (!source || !target) {
      continue;
    }
    const key = `${item.fkName ?? ''}|${source}|${target}`;
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    links.push({ source, target });
  }
  return links;
});

/** 图节点：外键关联度（子+父）决定尺寸与枢纽标记 */
const graphNodes = computed(() => {
  const degree = new Map<string, number>();
  for (const link of graphLinks.value) {
    degree.set(link.source, (degree.get(link.source) ?? 0) + 1);
    degree.set(link.target, (degree.get(link.target) ?? 0) + 1);
  }
  return [...degree.entries()]
    .map(([id, d]) => ({ id, degree: d }))
    .sort((a, b) => a.id.localeCompare(b.id));
});

const involvedTables = computed(() => {
  const set = new Set<string>();
  for (const item of filteredRelations.value) {
    if (item.childTable) set.add(item.childTable);
    if (item.parentTable) set.add(item.parentTable);
  }
  return set;
});

const hubCount = computed(
  () => graphNodes.value.filter((node) => node.degree >= 5).length,
);

const statCards = computed(() => [
  {
    color: 'var(--ant-color-primary)',
    label: '外键约束',
    value: graphLinks.value.length,
  },
  {
    color: 'var(--ant-color-info)',
    label: '关联列对',
    value: filteredRelations.value.length,
  },
  {
    color: 'var(--ant-color-success)',
    label: '涉及表',
    value: involvedTables.value.size,
  },
  {
    color: 'var(--ant-color-warning)',
    label: '枢纽表（度≥5）',
    value: hubCount.value,
  },
]);

const relationColumns = [
  { dataIndex: 'fkName', key: 'fkName', title: '外键名', width: 220 },
  { dataIndex: 'childTable', key: 'childTable', title: '子表' },
  { dataIndex: 'childColumn', key: 'childColumn', title: '子列' },
  { dataIndex: 'parentTable', key: 'parentTable', title: '父表' },
  { dataIndex: 'parentColumn', key: 'parentColumn', title: '父列' },
];

async function handleLoad() {
  if (connectionId.value == null) {
    window.message.warning('请选择连接');
    return;
  }
  loading.value = true;
  relations.value = [];
  try {
    relations.value = (await metadataRelations(connectionId.value)) ?? [];
  } finally {
    loading.value = false;
  }
}

/** 选择连接后自动采集一次，避免额外点击 */
function handleConnectionChange(value?: number) {
  if (value == null) {
    relations.value = [];
    return;
  }
  handleLoad();
}

const graphHeight = computed(() => {
  const count = graphNodes.value.length;
  // 节点多时给足可视高度，配合画布缩放平移查看
  return count > 80 ? 640 : count > 30 ? 560 : 480;
});
</script>

<template>
  <Page :auto-content-height="true">
    <div class="flex h-full flex-col gap-4">
      <Card size="small">
        <div class="grid grid-cols-1 items-end gap-6 md:grid-cols-4">
          <div class="flex flex-col gap-2">
            <span class="text-[14px] text-[var(--ant-color-text-secondary)]"
              >连接</span
            >
            <ConnectionSelect
              v-model:value="connectionId"
              class="w-full"
              placeholder="请选择连接"
              @change="handleConnectionChange"
            />
          </div>
          <div class="flex flex-col gap-2">
            <span class="text-[14px] text-[var(--ant-color-text-secondary)]"
              >表名过滤</span
            >
            <a-input
              v-model:value="keyword"
              allow-clear
              placeholder="按子表/父表名过滤，如 t_order"
            />
          </div>
          <div class="flex justify-end">
            <a-button :loading="loading" type="primary" @click="handleLoad">
              加载外键
            </a-button>
          </div>
        </div>
      </Card>

      <Spin :spinning="loading" size="large">
        <div
          v-if="relations.length > 0"
          class="flex h-full flex-col gap-4 overflow-auto"
        >
          <!-- 汇总 -->
          <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Card v-for="item in statCards" :key="item.label" size="small">
              <div class="text-[12px] text-[var(--ant-color-text-description)]">
                {{ item.label }}
              </div>
              <div
                class="text-[24px] font-semibold"
                :style="{ color: item.color }"
              >
                {{ item.value }}
              </div>
            </Card>
          </div>

          <!-- ER 关系图 -->
          <Card v-if="graphLinks.length > 0" size="small" title="ER 关系图">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-[14px] font-medium"
                  >{{ graphNodes.length }} 张表</span
                >
                <span
                  class="text-[12px] text-[var(--ant-color-text-description)]"
                >
                  箭头方向：子表 → 父表 · 节点越大外键关联越多 ·
                  支持滚轮缩放与拖拽平移
                </span>
              </div>
              <div class="flex flex-wrap gap-2">
                <Tag color="blue">普通表</Tag>
                <Tag color="warning">枢纽表（度≥5）</Tag>
              </div>
            </div>
            <div class="mt-3">
              <RelationGraph
                :height="graphHeight"
                :links="graphLinks"
                :nodes="graphNodes"
              />
            </div>
          </Card>

          <!-- 外键明细 -->
          <Card size="small" title="外键明细">
            <Table
              :columns="relationColumns"
              :data-source="filteredRelations"
              :pagination="{ pageSize: 20, showSizeChanger: true }"
              row-key="fkName"
              size="small"
            />
          </Card>
        </div>

        <div v-else class="flex h-full items-center justify-center">
          <Empty
            description="请选择连接后加载外键关系；该库若未建外键约束将无数据"
          />
        </div>
      </Spin>
    </div>
  </Page>
</template>

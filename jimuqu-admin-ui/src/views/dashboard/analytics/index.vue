<script setup lang="ts">
import type { DbConnection, DbTypeInfo } from '@/api/screw/model';
import type { EChartsOption } from 'echarts';

import { computed, onMounted, ref, shallowRef, watch } from 'vue';
import { useRouter } from 'vue-router';

import { connectionDbTypes, connectionList, groupList } from '@/api/screw';
import { Page } from '@/components';
import { Card, Tag } from 'antdv-next';

import BaseChart from './components/base-chart.vue';
import StatCard from './components/stat-card.vue';

defineOptions({ name: 'DashboardAnalytics' });

const router = useRouter();

/* ==================== 真实数据：连接 / 分组 / 数据库类型 ==================== */
const connections = ref<DbConnection[]>([]);
const groups = ref<{ id?: number; name: string }[]>([]);
const dbTypes = ref<DbTypeInfo[]>([]);

onMounted(async () => {
  try {
    const [conns, grps, types] = await Promise.all([
      connectionList(),
      groupList(),
      connectionDbTypes(),
    ]);
    connections.value = conns ?? [];
    groups.value = grps ?? [];
    dbTypes.value = types ?? [];
  } catch {
    // 后端不可用时静默降级，页面仍可浏览
  }
});

/* ==================== 顶部统计卡片 ==================== */
const statCards = computed(() => [
  {
    title: '数据库连接',
    value: connections.value.length,
    desc: '已配置的连接总数',
    color: 'primary' as const,
    icon: 'icon-[lucide--database]',
    suffix: ' 个',
  },
  {
    title: '连接分组',
    value: groups.value.length,
    desc: '用于归类管理连接',
    color: 'success' as const,
    icon: 'icon-[lucide--folder-open]',
    suffix: ' 组',
  },
  {
    title: '支持数据库',
    value: dbTypes.value.length,
    desc: '引擎支持的数据库类型',
    color: 'warning' as const,
    icon: 'icon-[lucide--hard-drive]',
    suffix: ' 种',
  },
  {
    title: '导出格式',
    value: 8,
    desc: 'Word、HTML、Markdown 等共 8 种',
    color: 'destructive' as const,
    icon: 'icon-[lucide--file-output]',
    suffix: ' 种',
  },
]);

/* ==================== 连接类型分布（真实数据） ==================== */
const typeDistOption = shallowRef<EChartsOption>({});

function buildTypeDistOption(list: DbConnection[]): EChartsOption {
  // 统计各数据库类型的连接数
  const counter = new Map<string, number>();
  for (const c of list) {
    const key = c.dbType || '其他';
    counter.set(key, (counter.get(key) ?? 0) + 1);
  }
  const palette = [
    '#1677ff',
    '#52c41a',
    '#faad14',
    '#ff4d4f',
    '#722ed1',
    '#13c2c2',
    '#eb2f96',
  ];
  const data = [...counter.entries()].map(([name, value], i) => ({
    name,
    value,
    itemStyle: { color: palette[i % palette.length] },
  }));

  return {
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      icon: 'circle',
      itemHeight: 8,
      itemWidth: 8,
      textStyle: { color: '#666' },
    },
    series: [
      {
        name: '连接分布',
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold',
            formatter: '{b}\n{c} 个',
          },
        },
        labelLine: { show: false },
        data:
          data.length > 0
            ? data
            : [
                {
                  name: '暂无连接',
                  value: 0,
                  itemStyle: { color: '#e8e8e8' },
                },
              ],
      },
    ],
    tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
  };
}

/* ==================== 最近添加的连接 ==================== */
const recentConnections = computed(() =>
  [...connections.value]
    .sort(
      (a, b) =>
        new Date(b.createTime ?? 0).getTime() -
        new Date(a.createTime ?? 0).getTime(),
    )
    .slice(0, 6),
);

const typeColorMap: Record<string, string> = {
  MYSQL: 'blue',
  ORACLE: 'red',
  POSTGRESQL: 'cyan',
  SQLSERVER: 'purple',
  MARIADB: 'geekblue',
  SQLITE: 'green',
  H2: 'orange',
  DM: 'magenta',
};

function typeColor(dbType: string) {
  return typeColorMap[(dbType ?? '').toUpperCase()] ?? 'default';
}

/* ==================== 快捷入口（与左侧菜单一致） ==================== */
const quickActions = [
  {
    title: '连接管理',
    desc: '新增或维护数据库连接',
    icon: 'icon-[lucide--cable]',
    path: '/screw/connection',
    color: 'text-success',
    bg: 'bg-success/10',
  },
  {
    title: '对象导出',
    desc: '浏览表/视图并导出文档',
    icon: 'icon-[lucide--file-output]',
    path: '/screw/export',
    color: 'text-primary',
    bg: 'bg-primary/10',
  },
  {
    title: '元数据',
    desc: '查看表结构与外键关系',
    icon: 'icon-[lucide--network]',
    path: '/screw/metadata',
    color: 'text-destructive',
    bg: 'bg-destructive/10',
  },
  {
    title: '常用工具',
    desc: 'SQL 格式化等实用小工具',
    icon: 'icon-[lucide--wrench]',
    path: '/screw/tools',
    color: 'text-warning',
    bg: 'bg-warning/10',
  },
  {
    title: '关于',
    desc: '版本信息与开源许可',
    icon: 'icon-[lucide--copyright]',
    path: '/jimu/about',
    color: 'text-muted-foreground',
    bg: 'bg-muted',
  },
];

function go(path: string) {
  router.push(path);
}

// 数据到达后构建图表
watch(connections, (list: DbConnection[]) => {
  typeDistOption.value = buildTypeDistOption(list);
});
</script>

<template>
  <Page content-class="p-4 lg:p-6">
    <div class="enter-y flex flex-col gap-4 lg:gap-6">
      <!-- 顶部统计卡片 -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          v-for="(item, index) in statCards"
          :key="index"
          :color="item.color"
          :desc="item.desc"
          :icon="item.icon"
          :suffix="item.suffix"
          :title="item.title"
          :value="item.value"
        />
      </div>

      <!-- 图表 + 最近连接 -->
      <div class="grid grid-cols-1 gap-4 lg:gap-6 xl:grid-cols-3">
        <Card :styles="{ body: { padding: '20px' } }">
          <template #title>
            <div class="flex items-center gap-2">
              <span
                class="icon-[lucide--pie-chart] text-primary size-[18px]"
              ></span>
              <span>连接类型分布</span>
            </div>
          </template>
          <BaseChart :option="typeDistOption" height="300px" />
        </Card>

        <Card
          class="xl:col-span-2"
          :styles="{ body: { padding: '12px 20px' } }"
        >
          <template #title>
            <div class="flex items-center gap-2">
              <span
                class="icon-[lucide--history] text-success size-[18px]"
              ></span>
              <span>最近连接</span>
            </div>
          </template>
          <template #extra>
            <a
              class="text-primary cursor-pointer text-xs"
              @click="go('/screw/connection')"
            >
              管理连接 →
            </a>
          </template>
          <div
            v-if="recentConnections.length === 0"
            class="text-muted-foreground py-10 text-center text-sm"
          >
            暂无连接，请先到「连接管理」添加
          </div>
          <div v-else class="flex flex-col">
            <div
              v-for="conn in recentConnections"
              :key="conn.id"
              class="hover:bg-muted/50 flex cursor-pointer items-center gap-3 rounded-lg px-2 py-3 transition-colors"
              @click="go('/screw/export')"
            >
              <div class="bg-muted flex-center size-9 shrink-0 rounded-lg">
                <span
                  class="icon-[lucide--database] text-primary size-4"
                ></span>
              </div>
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="truncate text-sm font-medium">
                    {{ conn.name }}
                  </span>
                  <Tag
                    :color="typeColor(conn.dbType)"
                    class="!m-0 !text-[11px]"
                  >
                    {{ conn.dbType }}
                  </Tag>
                </div>
                <p class="text-muted-foreground mt-0.5 truncate text-xs">
                  {{ conn.host }}{{ conn.port ? `:${conn.port}` : '' }} /
                  {{ conn.database }}
                </p>
              </div>
              <span
                class="text-muted-foreground hidden shrink-0 text-xs sm:inline"
              >
                {{ (conn.createTime || '').slice(0, 10) }}
              </span>
            </div>
          </div>
        </Card>
      </div>

      <!-- 快捷入口 -->
      <Card :styles="{ body: { padding: '20px' } }">
        <template #title>
          <div class="flex items-center gap-2">
            <span class="icon-[lucide--zap] text-warning size-[18px]"></span>
            <span>快捷入口</span>
          </div>
        </template>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <div
            v-for="(action, index) in quickActions"
            :key="index"
            class="card-box hover:shadow-float flex cursor-pointer items-center gap-3 p-4 transition-all duration-300 hover:-translate-y-0.5"
            @click="go(action.path)"
          >
            <div
              class="flex-center size-10 shrink-0 rounded-lg"
              :class="action.bg"
            >
              <span :class="`${action.icon} size-5 ${action.color}`"></span>
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ action.title }}</p>
              <span class="text-muted-foreground mt-1 block truncate text-xs">
                {{ action.desc }}
              </span>
            </div>
          </div>
        </div>
      </Card>
    </div>
  </Page>
</template>

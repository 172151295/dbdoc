<script setup lang="ts">
import type { ConnectionGroup, DbConnection } from '@/api/screw/model';

import { computed, onMounted, ref } from 'vue';

import {
  connectionList,
  connectionRemove,
  connectionTest,
  groupList,
  groupRemove,
} from '@/api/screw';
import { Page, useVbenModal } from '@/components';
import { withDefaultVxeGridOptions } from '@/components/vxe-table';
import { Popconfirm, Space, Spin, Tag } from 'antdv-next';
import { VxeGrid } from 'vxe-table';

import ConnectionDrawer from './connection-drawer.vue';
import { columns } from './data';
import GroupEditModal from './group-edit-modal.vue';
import GroupTree from './group-tree.vue';

const tableLoading = ref(false);
const groupsLoading = ref(false);
const groups = ref<ConnectionGroup[]>([]);
const connections = ref<DbConnection[]>([]);
const selectedGroupId = ref<'all' | number>('all');
const testLoadingId = ref<null | number>(null);

const totalCount = computed(() => connections.value.length);

/** 当前选中分组下的连接 */
const visibleConnections = computed(() => {
  if (selectedGroupId.value === 'all') {
    return connections.value;
  }
  return connections.value.filter(
    (conn) => conn.groupId === selectedGroupId.value,
  );
});

const groupCountMap = computed(() => {
  const map: Record<number, number> = {};
  for (const conn of connections.value) {
    map[conn.groupId] = (map[conn.groupId] ?? 0) + 1;
  }
  return map;
});

const gridOptions = computed(() =>
  withDefaultVxeGridOptions<DbConnection>({
    columns,
    height: '100%',
    keepSource: true,
    // 本页用 :data 客户端绑定，不走 vxe 代理服务端加载，须整体关闭 proxy 模式
    // 否则全局 proxyConfig(autoLoad:true + response 映射) 会让 vxe 进入代理分页：
    //   - 无 ajax.query 时忽略绑定数据而显示"暂无数据"
    //   - 分页总数读不到 ajax response，显示"共 0 条记录"
    // 用 enabled:false 即走纯客户端分页（类型要求 ProxyConfig 对象，不能直接写 false）
    proxyConfig: {
      enabled: false,
    },
    // 客户端分页时 vxe 不会从 :data 自动推导总数，必须显式绑定 total，
    // 否则分页器固定显示"共 0 条记录"
    pagerConfig: {
      total: visibleConnections.value.length,
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      slots: { buttons: 'toolbar-left', tools: 'toolbar-right' },
    },
    id: 'screw-connection-index',
  }),
);

async function loadGroups() {
  groupsLoading.value = true;
  try {
    groups.value = await groupList();
  } finally {
    groupsLoading.value = false;
  }
}

async function loadConnections() {
  tableLoading.value = true;
  try {
    connections.value = await connectionList();
  } finally {
    tableLoading.value = false;
  }
}

async function loadAll() {
  await Promise.all([loadGroups(), loadConnections()]);
}

onMounted(() => {
  loadAll();
});

// ---- 分组 ----
// 注意：connectedComponent 模式下 onConfirm 等回调以子组件内部 useVbenModal
// 为准（父层 options 会经 provide 注入、被子组件同名回调覆盖），
// 父层只需在模板上监听子组件 emit('reload') 做数据刷新。
const [GroupModal, groupModalApi] = useVbenModal({
  connectedComponent: GroupEditModal,
});

const [ConnModal, connModalApi] = useVbenModal({
  connectedComponent: ConnectionDrawer,
});

function handleAddGroup() {
  groupModalApi.setData({});
  groupModalApi.open();
}

function handleEditGroup(group: ConnectionGroup) {
  groupModalApi.setData({ record: group });
  groupModalApi.open();
}

function handleRemoveGroup(group: ConnectionGroup) {
  window.modal.confirm({
    title: '提示',
    okType: 'danger',
    content: `确认删除分组[${group.name}]？分组下的连接不会删除，仅归入"全部连接"。`,
    onOk: async () => {
      await groupRemove(group.id!);
      // 若删除的是当前选中分组，回到全部
      if (selectedGroupId.value === group.id) {
        selectedGroupId.value = 'all';
      }
      await loadAll();
    },
  });
}

// ---- 连接 ----
function handleAddConnection() {
  connModalApi.setData({ record: undefined });
  connModalApi.open();
}

function handleEditConnection(record: DbConnection) {
  connModalApi.setData({ record });
  connModalApi.open();
}

async function handleDeleteConnection(row: DbConnection) {
  window.modal.confirm({
    title: '提示',
    okType: 'danger',
    content: `确认删除连接[${row.name}]？`,
    onOk: async () => {
      await connectionRemove(row.id);
      window.message.success('删除成功');
      await loadConnections();
    },
  });
}

/** 行内测试：编辑态走已存密码（仅 id），无则用当前行完整信息 */
async function handleTestConnection(row: DbConnection) {
  testLoadingId.value = row.id;
  try {
    const msg = await connectionTest({ id: row.id });
    window.message.success(msg);
  } catch (error) {
    console.error(error);
  } finally {
    testLoadingId.value = null;
  }
}

function handleSelectGroup(key: number | string) {
  selectedGroupId.value = key === 'all' ? 'all' : (key as number);
}
</script>

<template>
  <Page :auto-content-height="true">
    <div class="flex h-full gap-4">
      <!-- 左：分组树 -->
      <div class="w-[240px] shrink-0">
        <GroupTree
          class="h-full"
          :groups="groups"
          :counts="groupCountMap"
          :loading="groupsLoading"
          :total="totalCount"
          v-model="selectedGroupId"
          @add="handleAddGroup"
          @edit="handleEditGroup"
          @remove="handleRemoveGroup"
          @reload="loadAll"
          @select="handleSelectGroup"
        />
      </div>

      <!-- 右：连接表格 -->
      <div
        class="bg-card flex min-w-0 flex-1 flex-col overflow-hidden rounded-lg"
      >
        <Spin
          class="min-h-0 flex-1"
          :spinning="tableLoading"
          :styles="{ container: { height: '100%' } }"
        >
          <VxeGrid
            class="h-full p-2 pt-0"
            v-bind="gridOptions"
            :data="visibleConnections"
          >
            <template #toolbar-left>
              <span class="text-[16px] font-medium">连接列表</span>
            </template>
            <template #toolbar-right>
              <Space>
                <a-button type="primary" @click="handleAddConnection">
                  新增连接
                </a-button>
              </Space>
            </template>

            <template #dbType="{ row }">
              <Tag color="blue">{{ row.dbType }}</Tag>
            </template>

            <template #createTime="{ row }">
              {{ row.createTime || '-' }}
            </template>

            <template #action="{ row }">
              <Space>
                <action-button
                  :loading="testLoadingId === row.id"
                  @click.stop="handleTestConnection(row)"
                >
                  测试
                </action-button>
                <action-button @click.stop="handleEditConnection(row)">
                  编辑
                </action-button>
                <Popconfirm
                  placement="left"
                  title="确认删除该连接？"
                  @confirm="handleDeleteConnection(row)"
                >
                  <action-button danger @click.stop="">删除</action-button>
                </Popconfirm>
              </Space>
            </template>
          </VxeGrid>
        </Spin>
      </div>
    </div>

    <GroupModal @reload="loadAll" />
    <ConnModal @reload="loadAll" />
  </Page>
</template>

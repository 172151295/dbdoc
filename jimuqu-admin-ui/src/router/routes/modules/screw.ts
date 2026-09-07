import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:database',
      order: 100,
      title: 'Screw 数据库文档',
    },
    name: 'Screw',
    path: '/screw',
    redirect: '/screw/connection',
    children: [
      {
        component: () => import('@/views/screw/connection/index.vue'),
        meta: {
          icon: 'lucide:cable',
          title: '连接管理',
        },
        name: 'ScrewConnection',
        path: '/screw/connection',
      },
      {
        component: () => import('@/views/screw/metadata/index.vue'),
        meta: {
          icon: 'lucide:network',
          title: '元数据',
        },
        name: 'ScrewMetadata',
        path: '/screw/metadata',
      },
      {
        component: () => import('@/views/screw/export/index.vue'),
        meta: {
          icon: 'lucide:file-output',
          title: '对象导出',
        },
        name: 'ScrewExport',
        path: '/screw/export',
      },
      {
        component: () => import('@/views/screw/code/index.vue'),
        meta: {
          icon: 'lucide:code',
          title: '代码生成',
          hideInMenu: true,
        },
        name: 'ScrewCode',
        path: '/screw/code',
      },
      {
        component: () => import('@/views/screw/compare/index.vue'),
        meta: {
          icon: 'lucide:git-compare',
          title: '库表比较',
          hideInMenu: true,
        },
        name: 'ScrewCompare',
        path: '/screw/compare',
      },
      {
        component: () => import('@/views/screw/tools/index.vue'),
        meta: {
          icon: 'lucide:wrench',
          title: '常用工具',
        },
        name: 'ScrewTools',
        path: '/screw/tools',
      },
    ],
  },
];

export default routes;

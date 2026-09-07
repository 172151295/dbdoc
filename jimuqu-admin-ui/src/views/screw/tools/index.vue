<script setup lang="ts">
import type { Component } from 'vue';

import { computed, defineAsyncComponent, ref } from 'vue';

import { Page } from '@/components';
import { Card } from 'antdv-next';

// 工具分类（对标 SmartSQL 28 项）
const toolCategories = [
  {
    name: '文本处理',
    tools: [
      { id: 'base64', name: 'Base64 编码/解码', icon: 'lucide:file-code' },
      {
        id: 'textInsert',
        name: '文本两端插入字符',
        icon: 'lucide:text-cursor',
      },
      { id: 'wordCount', name: '字数统计', icon: 'lucide:file-text' },
      { id: 'fanTi', name: '繁简转换', icon: 'lucide:languages' },
      { id: 'nPinYin', name: '汉字转拼音', icon: 'lucide:letter-text' },
      { id: 'storyOfWords', name: '文字物语', icon: 'lucide:book-open' },
    ],
  },
  {
    name: '加密解密',
    tools: [
      { id: 'md5', name: 'MD5 文本加密', icon: 'lucide:lock' },
      { id: 'jwt', name: 'JWT 解码器', icon: 'lucide:key' },
      { id: 'hexEncode', name: 'Hex 编码/解码', icon: 'lucide:hash' },
      { id: 'passwordGen', name: '密码生成器', icon: 'lucide:shield' },
    ],
  },
  {
    name: '格式转换',
    tools: [
      { id: 'sqlFormatter', name: 'SQL 格式化', icon: 'lucide:database' },
      { id: 'jsonFormatter', name: 'JSON 格式化', icon: 'lucide:braces' },
      { id: 'jsonToExcel', name: 'JSON 转 Excel', icon: 'lucide:table' },
      { id: 'json2CSharp', name: 'JSON 转 C#', icon: 'lucide:code-2' },
      { id: 'urlEncode', name: 'URL 编码/解码', icon: 'lucide:link' },
      { id: 'unicode', name: 'Unicode 中文互转', icon: 'lucide:translate' },
    ],
  },
  {
    name: '时间日期',
    tools: [
      { id: 'unixConvert', name: 'Unix 时间戳转换', icon: 'lucide:clock' },
      { id: 'dateDiff', name: '时间差计算', icon: 'lucide:calendar-clock' },
    ],
  },
  {
    name: '编码生成',
    tools: [
      { id: 'uuidGen', name: 'UUID 生成器', icon: 'lucide:identicon' },
      { id: 'qrCode', name: '二维码生成器', icon: 'lucide:qr-code' },
      { id: 'base64ToImg', name: '图片转 Base64', icon: 'lucide:image' },
      { id: 'ico', name: 'Ico 图标生成', icon: 'lucide:icon' },
      { id: 'icon', name: 'Icon 图标', icon: 'lucide:icons' },
    ],
  },
  {
    name: '其他工具',
    tools: [
      { id: 'rgbToHex', name: 'RGB 颜色转换', icon: 'lucide:palette' },
      { id: 'mimeType', name: 'MimeType 对照表', icon: 'lucide:file-type' },
      { id: 'linuxCommand', name: 'Linux 命令大全', icon: 'lucide:terminal' },
      { id: 'rmb', name: '人民币大写转换', icon: 'lucide:banknote' },
    ],
  },
];

/** 工具 ID → 懒加载组件（保持网格与组件一一对应） */
const toolComponents: Record<string, () => Promise<Component>> = {
  base64: () => import('./components/tool-base64.vue'),
  textInsert: () => import('./components/tool-text-insert.vue'),
  wordCount: () => import('./components/tool-word-count.vue'),
  fanTi: () => import('./components/tool-fanti.vue'),
  nPinYin: () => import('./components/tool-pinyin.vue'),
  storyOfWords: () => import('./components/tool-story.vue'),
  md5: () => import('./components/tool-md5.vue'),
  jwt: () => import('./components/tool-jwt.vue'),
  hexEncode: () => import('./components/tool-hex.vue'),
  passwordGen: () => import('./components/tool-password.vue'),
  sqlFormatter: () => import('./components/tool-sql-format.vue'),
  jsonFormatter: () => import('./components/tool-json-format.vue'),
  jsonToExcel: () => import('./components/tool-json-excel.vue'),
  json2CSharp: () => import('./components/tool-json-csharp.vue'),
  urlEncode: () => import('./components/tool-url.vue'),
  unicode: () => import('./components/tool-unicode.vue'),
  unixConvert: () => import('./components/tool-unix.vue'),
  dateDiff: () => import('./components/tool-date-diff.vue'),
  uuidGen: () => import('./components/tool-uuid.vue'),
  qrCode: () => import('./components/tool-qr.vue'),
  base64ToImg: () => import('./components/tool-img-base64.vue'),
  ico: () => import('./components/tool-ico.vue'),
  icon: () => import('./components/tool-icon.vue'),
  rgbToHex: () => import('./components/tool-rgb.vue'),
  mimeType: () => import('./components/tool-mime.vue'),
  linuxCommand: () => import('./components/tool-linux.vue'),
  rmb: () => import('./components/tool-rmb.vue'),
};

const activeTool = ref<null | string>(null);

const activeComponent = computed(() => {
  const loader = activeTool.value
    ? toolComponents[activeTool.value]
    : undefined;
  return loader ? defineAsyncComponent(loader) : null;
});

const activeToolTitle = computed(() => {
  for (const category of toolCategories) {
    const t = category.tools.find((tool) => tool.id === activeTool.value);
    if (t) {
      return t.name;
    }
  }
  return '';
});

function selectTool(toolId: string) {
  activeTool.value = toolId;
}
</script>

<template>
  <Page :auto-content-height="true">
    <div class="flex h-full flex-col gap-4">
      <!-- 工具分类网格 -->
      <div
        v-if="!activeTool"
        class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
      >
        <Card
          v-for="category in toolCategories"
          :key="category.name"
          :title="category.name"
          size="small"
        >
          <div class="grid grid-cols-2 gap-2">
            <button
              v-for="tool in category.tools"
              :key="tool.id"
              class="tool-card"
              @click="selectTool(tool.id)"
            >
              <div class="tool-icon">
                <span class="iconify" :data-icon="tool.icon" />
              </div>
              <span class="tool-name">{{ tool.name }}</span>
            </button>
          </div>
        </Card>
      </div>

      <!-- 工具详情（懒加载工具组件） -->
      <component
        :is="activeComponent"
        v-if="activeComponent"
        :title="activeToolTitle"
        class="flex min-h-0 flex-1 flex-col"
        @back="activeTool = null"
      />
    </div>
  </Page>
</template>

<style scoped>
.tool-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 16px 12px;
  cursor: pointer;
  background: var(--ant-color-bg-container);
  border: 1px solid var(--ant-color-border);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.tool-card:hover {
  background: var(--ant-color-primary-bg);
  border-color: var(--ant-color-primary);
  transform: translateY(-2px);
}

.tool-icon {
  font-size: 24px;
  color: var(--ant-color-primary);
}

.tool-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--ant-color-text);
}
</style>

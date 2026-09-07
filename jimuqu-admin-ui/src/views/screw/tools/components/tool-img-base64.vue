<script setup lang="ts">
import { ref } from 'vue';

import { Button, Input, Space, Upload } from 'antdv-next';

import { copyText, downloadDataUrl } from '../utils';
import ToolShell from './ToolShell.vue';

const dataUrl = ref('');
const base64Input = ref('');
const fileName = ref('image');

function onFile(file: File) {
  fileName.value = file.name.replace(/\.[^.]+$/, '') || 'image';
  const reader = new FileReader();
  reader.onload = () => {
    dataUrl.value = String(reader.result);
    base64Input.value = String(reader.result);
  };
  reader.readAsDataURL(file);
  return false;
}

function showFromInput() {
  const v = base64Input.value.trim();
  if (!/^data:image\//.test(v) && !/^[A-Za-z0-9+/=\s]+$/.test(v)) {
    window.message.warning('请输入 dataURL 或纯 Base64 数据');
    return;
  }
  dataUrl.value = /^data:/.test(v)
    ? v
    : `data:image/png;base64,${v.replace(/\s/g, '')}`;
}

function download() {
  if (dataUrl.value) {
    downloadDataUrl(dataUrl.value, `${fileName.value || 'image'}.png`);
  }
}

async function copy() {
  if (await copyText(dataUrl.value)) {
    window.message.success('已复制 dataURL');
  }
}
</script>

<template>
  <ToolShell
    title="图片转 Base64"
    description="图片文件 ↔ Base64 dataURL（FileReader 本地处理）"
  >
    <Space direction="vertical" size="middle" class="w-full">
      <Upload
        :before-upload="onFile"
        :show-upload-list="false"
        accept="image/*"
      >
        <Button type="primary">选择图片</Button>
      </Upload>
      <Input.TextArea
        v-model:value="base64Input"
        :rows="5"
        placeholder="或在此粘贴 dataURL / Base64"
      />
      <Space>
        <Button @click="showFromInput">载入 Base64</Button>
        <Button :disabled="!dataUrl" @click="copy">复制 dataURL</Button>
        <Button :disabled="!dataUrl" @click="download">下载 PNG</Button>
      </Space>
      <img
        v-if="dataUrl"
        :src="dataUrl"
        alt="preview"
        class="max-h-64 rounded-md border border-[var(--ant-color-border)] bg-white p-2"
      />
    </Space>
  </ToolShell>
</template>

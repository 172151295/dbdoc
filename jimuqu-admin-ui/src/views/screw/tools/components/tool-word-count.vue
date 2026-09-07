<script setup lang="ts">
import { computed, ref } from 'vue';

import { Descriptions, DescriptionsItem, Input } from 'antdv-next';

import ToolShell from './ToolShell.vue';

const text = ref('');

const stats = computed(() => {
  const s = text.value;
  const chars = Array.from(s).length;
  const charsNoSpace = Array.from(s.replace(/\s/g, '')).length;
  const han = (s.match(/[\u4e00-\u9fa5]/g) || []).length;
  const words = (s.trim() ? s.trim().split(/\s+/) : []).length;
  const lines = s ? s.split('\n').length : 0;
  const bytes = new TextEncoder().encode(s).length;
  return { chars, charsNoSpace, han, words, lines, bytes };
});
</script>

<template>
  <ToolShell title="字数统计" description="实时统计字符/汉字/单词/行数/字节">
    <Input.TextArea
      v-model:value="text"
      :rows="12"
      placeholder="粘贴或输入文本..."
    />
    <Descriptions :column="2" size="small" class="mt-4" bordered>
      <DescriptionsItem label="字符数（含空格）">{{
        stats.chars
      }}</DescriptionsItem>
      <DescriptionsItem label="字符数（不含空格）">{{
        stats.charsNoSpace
      }}</DescriptionsItem>
      <DescriptionsItem label="汉字数">{{ stats.han }}</DescriptionsItem>
      <DescriptionsItem label="单词数">{{ stats.words }}</DescriptionsItem>
      <DescriptionsItem label="行数">{{ stats.lines }}</DescriptionsItem>
      <DescriptionsItem label="字节数（UTF-8）">{{
        stats.bytes
      }}</DescriptionsItem>
    </Descriptions>
  </ToolShell>
</template>

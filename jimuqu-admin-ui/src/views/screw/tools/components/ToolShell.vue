<script setup lang="ts">
defineProps<{
  description?: string;
  title: string;
}>();

const emit = defineEmits<{ back: [] }>();
</script>

<template>
  <div
    class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-[var(--ant-color-border-secondary)]"
  >
    <div
      class="flex items-center gap-3 border-b border-[var(--ant-color-split)] px-4 py-3"
    >
      <button
        class="text-[14px] text-[var(--ant-color-link)] hover:opacity-75"
        @click="emit('back')"
      >
        ← 返回工具列表
      </button>
      <div class="text-[15px] font-medium">{{ title }}</div>
      <div
        v-if="description"
        class="text-[12px] text-[var(--ant-color-text-description)]"
      >
        {{ description }}
      </div>
    </div>
    <div class="min-h-0 flex-1 overflow-auto p-4">
      <slot />
    </div>
  </div>
</template>

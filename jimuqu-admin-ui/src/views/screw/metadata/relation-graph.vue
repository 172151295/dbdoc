<script setup lang="ts">
import type { EChartsOption } from 'echarts';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';

import echarts from '@/components/echarts';
import { usePreferences } from '@/core/preferences';
import { useDebounceFn, useResizeObserver, useWindowSize } from '@vueuse/core';

interface GraphNode {
  degree: number;
  id: string;
}

interface GraphLink {
  source: string;
  target: string;
}

const props = withDefaults(
  defineProps<{
    height?: number;
    links?: GraphLink[];
    nodes?: GraphNode[];
  }>(),
  {
    height: 520,
    links: () => [],
    nodes: () => [],
  },
);

/** 空值归一，避免可选 prop 在布局计算中判空 */
const nodes = computed(() => props.nodes ?? []);
const links = computed(() => props.links ?? []);

const chartRef = ref<HTMLDivElement>();
let chartInstance: echarts.ECharts | null = null;

const { isDark } = usePreferences();
const { height: winHeight, width: winWidth } = useWindowSize();

/** 分层布局：横向按外键深度展开，纵向按同层序号展开（确定性布局，可复现） */
const LAYER_GAP = 200;
const NODE_GAP = 48;
const COMP_GAP = 90;

function buildLayout() {
  const adjacency = new Map<string, Set<string>>();
  const degree = new Map<string, number>();
  for (const node of nodes.value) {
    adjacency.set(node.id, new Set());
    degree.set(node.id, node.degree);
  }
  for (const link of links.value) {
    const out = adjacency.get(link.source);
    const inn = adjacency.get(link.target);
    if (out && inn) {
      out.add(link.target);
      inn.add(link.source);
    }
  }

  const positions = new Map<string, { x: number; y: number }>();
  const seen = new Set<string>();
  let offsetX = 0;

  for (const start of nodes.value) {
    if (seen.has(start.id)) {
      continue;
    }
    // 连通分量（含孤立节点）
    const component: string[] = [];
    const stack = [start.id];
    seen.add(start.id);
    while (stack.length > 0) {
      const cur = stack.pop()!;
      component.push(cur);
      for (const next of adjacency.get(cur) ?? []) {
        if (!seen.has(next)) {
          seen.add(next);
          stack.push(next);
        }
      }
    }

    // 分量内取度数最高者作根，BFS 分层
    const root = component.reduce((a, b) =>
      (degree.get(b) ?? 0) > (degree.get(a) ?? 0) ? b : a,
    );
    const depth = new Map<string, number>([[root, 0]]);
    const queue = [root];
    while (queue.length > 0) {
      const cur = queue.shift()!;
      for (const next of adjacency.get(cur) ?? []) {
        if (!depth.has(next)) {
          depth.set(next, (depth.get(cur) ?? 0) + 1);
          queue.push(next);
        }
      }
    }

    const layers = new Map<number, string[]>();
    for (const id of component) {
      const level = depth.get(id) ?? 0;
      const list = layers.get(level);
      if (list) {
        list.push(id);
      } else {
        layers.set(level, [id]);
      }
    }

    let maxLevel = 0;
    for (const [level, ids] of layers) {
      maxLevel = Math.max(maxLevel, level);
      ids.sort();
      ids.forEach((id, index) => {
        positions.set(id, {
          x: offsetX + level * LAYER_GAP,
          y: (index - (ids.length - 1) / 2) * NODE_GAP,
        });
      });
    }
    offsetX += (maxLevel + 1) * LAYER_GAP + COMP_GAP;
  }
  return { degree, positions };
}

function buildOption(): EChartsOption {
  const { degree, positions } = buildLayout();
  const dense = nodes.value.length > 200;
  return {
    animation: !dense,
    series: [
      {
        type: 'graph',
        layout: 'none',
        roam: true,
        categories: [{ name: '普通表' }, { name: '枢纽表' }],
        data: nodes.value.map((node) => {
          const deg = degree.get(node.id) ?? 0;
          const pos = positions.get(node.id);
          return {
            id: node.id,
            name: node.id,
            x: pos?.x ?? 0,
            y: pos?.y ?? 0,
            value: deg,
            symbolSize: deg === 0 ? 12 : Math.min(12 + deg * 3.5, 36),
            category: deg >= 5 ? 1 : 0,
            label: { show: !dense, fontSize: 11 },
          };
        }),
        links: links.value.map((link) => ({
          source: link.source,
          target: link.target,
          lineStyle: { curveness: 0.1 },
        })),
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 7,
        lineStyle: { color: '#8c8c8c', opacity: 0.65, width: 1 },
        scaleLimit: { max: 4, min: 0.05 },
        emphasis: { focus: 'adjacency', label: { show: true } },
      },
    ],
  };
}

function render() {
  nextTick(() => {
    if (!chartRef.value) {
      return;
    }
    if (!chartInstance) {
      chartInstance = echarts.init(
        chartRef.value,
        isDark.value ? 'dark' : null,
      );
    }
    chartInstance.setOption(buildOption(), true);
  });
}

const resizeHandler = useDebounceFn(() => {
  chartInstance?.resize({
    animation: { duration: 300, easing: 'quadraticIn' },
  });
}, 200);

useResizeObserver(chartRef, resizeHandler);
watch([winWidth, winHeight], resizeHandler);

watch(() => [nodes.value, links.value], render, { deep: true });

watch(isDark, () => {
  chartInstance?.dispose();
  chartInstance = null;
  render();
});

onMounted(render);

onBeforeUnmount(() => {
  chartInstance?.dispose();
  chartInstance = null;
});
</script>

<template>
  <div ref="chartRef" :style="{ height: `${height}px`, width: '100%' }"></div>
</template>

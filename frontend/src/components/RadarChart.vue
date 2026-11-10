<template>
  <div ref="chartRef" class="radar-chart"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  dimensions: { type: Array, default: () => [] }
})

const chartRef = ref()
let chart = null

function render() {
  if (!chart) return
  const dims = props.dimensions || []
  chart.setOption(
    {
      tooltip: { trigger: 'item' },
      radar: {
        indicator: dims.map((d) => ({ name: d.name, max: 100 })),
        radius: '65%',
        splitNumber: 4,
        axisName: { color: '#5f6c77', fontSize: 12 },
        axisLine: { lineStyle: { color: '#dfe7ec' } },
        splitLine: { lineStyle: { color: '#dfe7ec' } },
        splitArea: {
          areaStyle: {
            color: ['rgba(22,32,42,0.012)', 'rgba(22,32,42,0.025)']
          }
        }
      },
      series: [
        {
          type: 'radar',
          symbolSize: 4,
          data: [
            {
              value: dims.map((d) => Number(d.score) || 0),
              name: '匹配度',
              areaStyle: { color: 'rgba(31,95,107,0.10)' },
              lineStyle: { color: '#17505c', width: 2 },
              itemStyle: { color: '#17505c' }
            }
          ]
        }
      ]
    },
    true
  )
}

function resize() {
  chart && chart.resize()
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  window.addEventListener('resize', resize)
})

watch(() => props.dimensions, () => render(), { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart && chart.dispose()
})
</script>

<style scoped>
.radar-chart {
  width: 100%;
  height: 420px;
}
</style>
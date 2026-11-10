import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/** 岗位列表的三个视图：每日推荐 / 全部岗位 / 合并视图 */
export type JobViewKey = 'recommend' | 'all' | 'combined'

const VIEWS: JobViewKey[] = ['recommend', 'all', 'combined']

type ListByView<T> = Record<JobViewKey, T[]>

const emptyMap = <T>(): ListByView<T> => ({ recommend: [], all: [], combined: [] })

const isViewKey = (key: string): key is JobViewKey => VIEWS.includes(key as JobViewKey)

/**
 * 岗位视图状态：每个视图各自维护已选筛选类型与候选类型。
 */
export const useJobViewStore = defineStore('jobView', () => {
  const view = ref<JobViewKey>('recommend')
  const filtersByView = ref<ListByView<string>>(emptyMap<string>())
  const optionsByView = ref<ListByView<string>>(emptyMap<string>())

  const currentFilters = computed(() => filtersByView.value[view.value] || [])

  function setView(next: string): void {
    if (isViewKey(next)) view.value = next
  }

  function filtersFor(key: string): string[] {
    return isViewKey(key) ? filtersByView.value[key] || [] : []
  }

  function optionsFor(key: string): string[] {
    return isViewKey(key) ? optionsByView.value[key] || [] : []
  }

  function setOptions(map: Partial<ListByView<string>>): void {
    optionsByView.value = { ...emptyMap<string>(), ...map }
  }

  function toggleType(key: string, type: string): void {
    if (!isViewKey(key)) return
    const current = filtersFor(key)
    const next = current.includes(type)
      ? current.filter((item) => item !== type)
      : [...current, type]
    filtersByView.value = { ...filtersByView.value, [key]: next }
  }

  function clearTypes(key: string): void {
    if (!isViewKey(key)) return
    filtersByView.value = { ...filtersByView.value, [key]: [] }
  }

  /** 候选类型变化后，剔除当前视图里已不存在的筛选项 */
  function pruneTypes(map: Partial<ListByView<string>>): void {
    const next = { ...filtersByView.value }
    for (const key of VIEWS) {
      const allowed = new Set(map[key] || [])
      next[key] = (next[key] || []).filter((type) => allowed.has(type))
    }
    filtersByView.value = next
  }

  return {
    view,
    filtersByView,
    optionsByView,
    currentFilters,
    setView,
    filtersFor,
    optionsFor,
    setOptions,
    toggleType,
    clearTypes,
    pruneTypes
  }
})
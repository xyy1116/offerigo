import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const VIEWS = ['recommend', 'all', 'combined']
const emptyMap = () => ({ recommend: [], all: [], combined: [] })

export const useJobViewStore = defineStore('jobView', () => {
  const view = ref('recommend')
  const filtersByView = ref(emptyMap())
  const optionsByView = ref(emptyMap())

  const currentFilters = computed(() => filtersByView.value[view.value] || [])

  function setView(next) {
    if (VIEWS.includes(next)) view.value = next
  }

  function filtersFor(key) {
    return filtersByView.value[key] || []
  }

  function optionsFor(key) {
    return optionsByView.value[key] || []
  }

  function setOptions(map) {
    optionsByView.value = { ...emptyMap(), ...map }
  }

  function toggleType(key, type) {
    if (!VIEWS.includes(key)) return
    const current = filtersFor(key)
    const next = current.includes(type)
      ? current.filter((item) => item !== type)
      : [...current, type]
    filtersByView.value = { ...filtersByView.value, [key]: next }
  }

  function clearTypes(key) {
    if (!VIEWS.includes(key)) return
    filtersByView.value = { ...filtersByView.value, [key]: [] }
  }

  function pruneTypes(map) {
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
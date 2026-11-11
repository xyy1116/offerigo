<template>
  <div ref="host" class="code-editor" :style="{ height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { basicSetup, EditorView } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { javascript } from '@codemirror/lang-javascript'
import { java } from '@codemirror/lang-java'
import { python } from '@codemirror/lang-python'
import { cpp } from '@codemirror/lang-cpp'
import { oneDark } from '@codemirror/theme-one-dark'
import { keymap } from '@codemirror/view'
import { indentWithTab } from '@codemirror/commands'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'javascript' },
  height: { type: String, default: '320px' }
})
const emit = defineEmits(['update:modelValue'])

const host = ref(null)
let view = null
let observer = null

const isDark = () => document.documentElement.dataset.theme === 'dark'
  || document.documentElement.classList.contains('dark')

function languageExtension() {
  switch (props.language) {
    case 'java':
      return java()
    case 'python':
      return python()
    case 'c':
    case 'cpp':
      return cpp()
    default:
      return javascript({ typescript: false })
  }
}

function themeExtension() {
  const base = EditorView.theme({
    '&': {
      backgroundColor: 'var(--color-surface)',
      color: 'var(--color-text)',
      fontSize: '13px'
    },
    '.cm-scroller': {
      fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
      overflow: 'auto'
    },
    '.cm-gutters': {
      backgroundColor: 'var(--color-surface-muted)',
      color: 'var(--color-text-3)',
      borderRight: '1px solid var(--color-border)'
    },
    '.cm-activeLine': { backgroundColor: 'var(--color-surface-muted)' },
    '.cm-activeLineGutter': { backgroundColor: 'var(--color-surface-muted)' },
    '.cm-content': { caretColor: 'var(--color-text)' },
    '.cm-cursor': { borderLeftColor: 'var(--color-text)' }
  })
  return isDark() ? [base, oneDark] : [base]
}

function build() {
  if (!host.value) return
  if (view) view.destroy()
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: props.modelValue || '',
      extensions: [
        basicSetup,
        languageExtension(),
        keymap.of([indentWithTab]),
        ...themeExtension(),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            emit('update:modelValue', update.state.doc.toString())
          }
        })
      ]
    })
  })
}

onMounted(() => {
  build()
  observer = new MutationObserver(() => build())
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-theme']
  })
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  observer = null
  if (view) view.destroy()
  view = null
})

watch(() => props.modelValue, (value) => {
  if (!view) return
  const current = view.state.doc.toString()
  if (value !== current) {
    view.dispatch({ changes: { from: 0, to: current.length, insert: value || '' } })
  }
})

watch(() => props.language, () => build())
</script>

<style scoped>
.code-editor {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-surface);
}
.code-editor :deep(.cm-editor) {
  height: 100%;
}
.code-editor :deep(.cm-scroller) {
  min-height: 100%;
}
.code-editor :deep(.cm-focused) {
  outline: none;
}
</style>
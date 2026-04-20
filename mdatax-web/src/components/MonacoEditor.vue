<template>
  <div ref="editorContainer" class="monaco-editor-container"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

self.MonacoEnvironment = {
  getWorker(_, label) {
    if (label === 'json') return new jsonWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new cssWorker()
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker()
    if (label === 'typescript' || label === 'javascript') return new tsWorker()
    return new editorWorker()
  }
}

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'sql' },
  theme: { type: String, default: 'vs' },
  readOnly: { type: Boolean, default: false },
  options: { type: Object, default: () => ({}) },
  suggestions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

const editorContainer = ref(null)
let editor = null
let completionDisposable = null

function registerCompletionProvider() {
  if (completionDisposable) {
    completionDisposable.dispose()
    completionDisposable = null
  }
  if (!props.suggestions || props.suggestions.length === 0) return

  completionDisposable = monaco.languages.registerCompletionItemProvider(props.language, {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      }
      const items = props.suggestions.map(s => ({
        label: s.label,
        kind: s.kind || monaco.languages.CompletionItemKind.Text,
        insertText: s.insertText || s.label,
        detail: s.detail || '',
        range
      }))
      return { suggestions: items }
    }
  })
}

onMounted(() => {
  if (!editorContainer.value) return

  editor = monaco.editor.create(editorContainer.value, {
    value: props.modelValue,
    language: props.language,
    theme: props.theme,
    readOnly: props.readOnly,
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 14,
    lineNumbers: 'on',
    roundedSelection: false,
    scrollBeyondLastLine: false,
    quickSuggestions: true,
    suggestOnTriggerCharacters: true,
    ...props.options
  })

  editor.onDidChangeModelContent(() => {
    emit('update:modelValue', editor.getValue())
  })

  registerCompletionProvider()
})

onBeforeUnmount(() => {
  if (completionDisposable) {
    completionDisposable.dispose()
    completionDisposable = null
  }
  if (editor) {
    editor.dispose()
    editor = null
  }
})

watch(() => props.modelValue, (val) => {
  if (editor && editor.getValue() !== val) {
    editor.setValue(val)
  }
})

watch(() => props.suggestions, () => {
  registerCompletionProvider()
}, { deep: true })
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  height: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
</style>

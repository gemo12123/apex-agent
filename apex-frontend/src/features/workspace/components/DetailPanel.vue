<script setup lang="ts">
import { computed } from 'vue'
import { downloadTextFile } from '@/utils/download'
import { renderMarkdown } from '@/utils/markdown'
import type { ArtifactRecord, InvocationRecord } from '@/types/apex'

const props = defineProps<{
  invocation: InvocationRecord | null
  artifact: ArtifactRecord | null
}>()

const activeTitle = computed(() => {
  if (props.artifact) return props.artifact.artifactName
  if (props.invocation) return props.invocation.name
  return '查看详情'
})

const activeMeta = computed(() => {
  if (props.artifact) return '产物详情'
  if (props.invocation) return '调用详情'
  return '详情面板'
})

const markdownContent = computed(() => {
  if (props.artifact) return renderMarkdown(props.artifact.content)
  if (props.invocation) return renderMarkdown(props.invocation.content || '_正在等待调用结果..._')
  return ''
})

function downloadArtifact(): void {
  if (!props.artifact) return
  downloadTextFile(`${props.artifact.artifactName}.md`, props.artifact.content)
}
</script>

<template>
  <section class="detail-panel">
    <header class="detail-panel__header">
      <div>
        <p class="detail-panel__eyebrow">{{ activeMeta }}</p>
        <h2 class="detail-panel__title">{{ activeTitle }}</h2>
      </div>
      <button v-if="artifact" class="ghost-button" type="button" @click="downloadArtifact">导出</button>
    </header>

    <div v-if="artifact || invocation" class="detail-panel__body markdown" v-html="markdownContent" />
    <div v-else class="detail-panel__empty">从右侧执行轨选择一个调用或产物后，可以在这里查看实时内容。</div>
  </section>
</template>

<style scoped>
.detail-panel {
  display: grid;
  grid-template-rows: auto 1fr;
  min-height: 0;
  border: 1px solid var(--border-strong);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow-panel);
}

.detail-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border);
}

.detail-panel__eyebrow {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-size: 0.84rem;
}

.detail-panel__title {
  font-size: 1.08rem;
}

.detail-panel__body {
  min-height: 0;
  overflow: auto;
  padding: 18px;
}

.detail-panel__empty {
  padding: 18px;
  color: var(--text-soft);
}
</style>

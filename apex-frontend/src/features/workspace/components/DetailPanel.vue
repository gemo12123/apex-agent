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
  return 'Select a detail'
})

const markdownContent = computed(() => {
  if (props.artifact) return renderMarkdown(props.artifact.content)
  if (props.invocation) return renderMarkdown(props.invocation.content || '_Waiting for invocation output..._')
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
        <p class="detail-panel__eyebrow">Detail</p>
        <h2 class="detail-panel__title">{{ activeTitle }}</h2>
      </div>
      <button v-if="artifact" class="ghost-button" type="button" @click="downloadArtifact">Export</button>
    </header>

    <div v-if="artifact || invocation" class="detail-panel__body markdown" v-html="markdownContent" />
    <div v-else class="detail-panel__empty">
      Pick an invocation or artifact from the execution rail to inspect the live payload here.
    </div>
  </section>
</template>

<style scoped>
.detail-panel {
  display: grid;
  grid-template-rows: auto 1fr;
  min-height: 0;
  border: 1px solid var(--border-strong);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-panel);
}

.detail-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
}

.detail-panel__eyebrow {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 0.74rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.detail-panel__title {
  margin: 0;
  font-size: 1.08rem;
}

.detail-panel__body {
  min-height: 0;
  overflow: auto;
  padding: 20px;
}

.detail-panel__empty {
  padding: 20px;
  color: var(--text-soft);
}
</style>

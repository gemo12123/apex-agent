<script setup lang="ts">
import { ref, watch } from 'vue'
import type { TimelineEntry } from '@/features/workspace/timeline'
import { downloadTextFile } from '@/utils/download'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  open: boolean
  entries: TimelineEntry[]
}>()

const emit = defineEmits<{
  (event: 'close'): void
}>()

const expandedId = ref<string | null>(null)

watch(
  () => props.entries,
  (entries) => {
    if (entries.length === 0) {
      expandedId.value = null
      return
    }

    if (expandedId.value && entries.some((entry) => entry.id === expandedId.value)) {
      return
    }

    expandedId.value = entries.find((entry) => entry.defaultExpanded)?.id ?? null
  },
  { immediate: true },
)

function toggleEntry(id: string): void {
  expandedId.value = expandedId.value === id ? null : id
}

function exportEntry(entry: TimelineEntry): void {
  if (!entry.exportFileName || !entry.body) {
    return
  }

  downloadTextFile(entry.exportFileName, entry.body)
}

function formatKind(kind: TimelineEntry['kind']): string {
  switch (kind) {
    case 'stage':
      return '阶段'
    case 'invocation':
      return '调用'
    case 'artifact':
      return '产物'
    case 'prompt':
      return '人工确认'
    case 'confirmation':
      return '工具确认'
    case 'session':
      return '会话状态'
  }
}
</script>

<template>
  <aside v-if="open" data-testid="timeline-drawer" class="timeline-drawer">
    <header class="timeline-drawer__header">
      <div class="timeline-drawer__heading">
        <p class="timeline-drawer__eyebrow">Execution Timeline</p>
        <h2 class="timeline-drawer__title">执行轨迹</h2>
      </div>

      <button class="ghost-button" type="button" @click="emit('close')">收起</button>
    </header>

    <div v-if="entries.length === 0" class="timeline-drawer__empty">
      执行开始后，这里会显示计划、调用和产物时间线。
    </div>

    <div v-else class="timeline-drawer__list">
      <article
        v-for="entry in entries"
        :key="entry.id"
        class="timeline-entry"
        :class="`timeline-entry--${entry.tone}`"
      >
        <button
          :data-testid="`timeline-entry-${entry.id}`"
          class="timeline-entry__trigger"
          type="button"
          @click="toggleEntry(entry.id)"
        >
          <span class="timeline-entry__kind">{{ formatKind(entry.kind) }}</span>
          <strong class="timeline-entry__title">{{ entry.title }}</strong>
          <small class="timeline-entry__subtitle">{{ entry.subtitle }}</small>
        </button>

        <div v-if="expandedId === entry.id" class="timeline-entry__detail">
          <div
            v-if="entry.body"
            class="timeline-entry__body markdown"
            v-html="renderMarkdown(entry.body)"
          />
          <p v-else class="timeline-entry__placeholder">当前节点暂无更多详情。</p>

          <button
            v-if="entry.exportFileName && entry.body"
            :data-testid="`timeline-export-${entry.id}`"
            class="ghost-button timeline-entry__export"
            type="button"
            @click="exportEntry(entry)"
          >
            导出
          </button>
        </div>
      </article>
    </div>
  </aside>
</template>

<style scoped>
.timeline-drawer {
  display: grid;
  grid-template-rows: auto 1fr;
  min-height: 0;
  border-left: 1px solid var(--border);
  background: rgba(250, 251, 252, 0.96);
}

.timeline-drawer__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--border);
}

.timeline-drawer__heading {
  display: grid;
  gap: 4px;
}

.timeline-drawer__eyebrow {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.8rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.timeline-drawer__title {
  margin: 0;
  font-size: 1.04rem;
}

.timeline-drawer__empty {
  padding: 18px;
  color: var(--text-soft);
  line-height: 1.7;
}

.timeline-drawer__list {
  min-height: 0;
  overflow: auto;
  padding: 14px;
  display: grid;
  align-content: start;
  gap: 10px;
}

.timeline-entry {
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
  background: rgba(255, 255, 255, 0.82);
  overflow: hidden;
}

.timeline-entry--success {
  border-color: rgba(22, 163, 74, 0.24);
}

.timeline-entry--warning {
  border-color: rgba(217, 119, 6, 0.26);
}

.timeline-entry--danger {
  border-color: rgba(220, 38, 38, 0.24);
}

.timeline-entry--active {
  border-color: rgba(37, 99, 235, 0.24);
}

.timeline-entry__trigger {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 14px;
  border: none;
  background: transparent;
  color: inherit;
  text-align: left;
}

.timeline-entry__kind {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.timeline-entry__title {
  font-size: 0.96rem;
}

.timeline-entry__subtitle {
  color: var(--text-soft);
  font-size: 0.84rem;
}

.timeline-entry__detail {
  display: grid;
  gap: 12px;
  padding: 0 14px 14px;
  border-top: 1px solid var(--border);
}

.timeline-entry__body {
  padding-top: 14px;
}

.timeline-entry__placeholder {
  margin: 0;
  padding-top: 14px;
  color: var(--text-soft);
}

.timeline-entry__export {
  justify-self: flex-start;
}
</style>

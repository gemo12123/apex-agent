<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { AgentSummary } from '@/types/apex'
import TimelineSettingsPopover from '@/features/workspace/components/TimelineSettingsPopover.vue'

const props = defineProps<{
  agents: AgentSummary[]
  selectedAgentKey: string
  userId: string
  hasStarted: boolean
  historyItems: Array<{ id: string; title: string; subtitle?: string; active?: boolean }>
}>()

const emit = defineEmits<{
  (event: 'new-chat'): void
  (event: 'select-history', id: string): void
  (event: 'save-settings', payload: { agentKey: string; userId: string }): void
}>()

const settingsOpen = ref(false)
const footerRef = ref<HTMLElement | null>(null)

watch(
  () => props.hasStarted,
  () => {
    settingsOpen.value = false
  },
)

function handleDocumentPointerDown(event: PointerEvent): void {
  if (!settingsOpen.value || !footerRef.value) {
    return
  }

  const target = event.target
  if (target instanceof Node && footerRef.value.contains(target)) {
    return
  }

  settingsOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
})

const historySkeletons = computed(() => {
  if (props.historyItems.length > 0) {
    return props.historyItems
  }

  return [
    { id: 'placeholder-1', title: '新会话将显示在这里' },
    { id: 'placeholder-2', title: '后续可接入真实历史列表' },
    { id: 'placeholder-3', title: '支持搜索、置顶与分组' },
  ]
})

function handleSaveSettings(payload: { agentKey: string; userId: string }): void {
  emit('save-settings', payload)
  settingsOpen.value = false
}
</script>

<template>
  <aside class="workspace-sidebar">
    <div class="workspace-sidebar__top">
      <div class="workspace-sidebar__brand">
        <p class="workspace-sidebar__brand-title">Apex</p>
        <p class="workspace-sidebar__brand-subtitle">Workspace</p>
      </div>

      <button
        data-testid="new-chat"
        class="workspace-sidebar__new-chat accent-button"
        type="button"
        @click="emit('new-chat')"
      >
        新建会话
      </button>
    </div>

    <div data-testid="sidebar-history" class="workspace-sidebar__history">
      <div class="workspace-sidebar__section-head">
        <p class="workspace-sidebar__section-title">历史对话</p>
        <span class="workspace-sidebar__section-note">即将接入</span>
      </div>

      <button
        v-for="item in historySkeletons"
        :key="item.id"
        class="workspace-sidebar__history-item"
        :class="{ 'workspace-sidebar__history-item--active': item.active }"
        type="button"
        @click="emit('select-history', item.id)"
      >
        {{ item.title }}
        <small v-if="item.subtitle" class="workspace-sidebar__history-subtitle">{{ item.subtitle }}</small>
      </button>

      <p v-if="historyItems.length === 0" class="workspace-sidebar__empty">
        历史会话接入后会显示在这里。
      </p>
    </div>

    <footer ref="footerRef" class="workspace-sidebar__footer">
      <div
        v-if="settingsOpen"
        data-testid="sidebar-settings-popover"
        class="workspace-sidebar__popover"
      >
        <TimelineSettingsPopover
          :agents="agents"
          :selected-agent-key="selectedAgentKey"
          :user-id="userId"
          :has-started="hasStarted"
          @save="handleSaveSettings"
          @cancel="settingsOpen = false"
          @close="settingsOpen = false"
        />
      </div>

      <button
        data-testid="sidebar-settings-trigger"
        class="ghost-button workspace-sidebar__settings-trigger"
        type="button"
        @click="settingsOpen = !settingsOpen"
      >
        设置
      </button>
    </footer>
  </aside>
</template>

<style scoped>
.workspace-sidebar {
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 16px;
  min-height: 100vh;
  padding: 14px 12px;
  border-right: 1px solid var(--border);
  background: var(--surface-muted);
}

.workspace-sidebar__top {
  display: grid;
  gap: 12px;
}

.workspace-sidebar__brand {
  display: grid;
  gap: 2px;
  padding: 4px 2px 2px;
}

.workspace-sidebar__brand-title {
  font-size: 1.24rem;
  font-weight: 700;
  letter-spacing: -0.04em;
}

.workspace-sidebar__brand-subtitle,
.workspace-sidebar__section-title,
.workspace-sidebar__section-note {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.workspace-sidebar__new-chat {
  justify-content: center;
}

.workspace-sidebar__history {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
}

.workspace-sidebar__section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.workspace-sidebar__history-item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-card);
  background: transparent;
  color: var(--text-soft);
  text-align: left;
}

.workspace-sidebar__history-item:hover,
.workspace-sidebar__history-item--active {
  border-color: var(--border);
  background: rgba(255, 255, 255, 0.78);
  color: var(--text-strong);
}

.workspace-sidebar__history-subtitle {
  display: block;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 0.72rem;
}

.workspace-sidebar__empty {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.6;
}

.workspace-sidebar__footer {
  position: relative;
  display: grid;
  gap: 10px;
}

.workspace-sidebar__popover {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  z-index: 2;
}

.workspace-sidebar__settings-trigger {
  justify-content: center;
}

@media (max-width: 720px) {
  .workspace-sidebar {
    min-height: auto;
  }
}
</style>

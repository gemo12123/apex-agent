<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AgentSummary } from '@/types/apex'

const props = defineProps<{
  agents: AgentSummary[]
  selectedAgentKey: string
  userId: string
  historyItems: Array<{ id: string; title: string; active?: boolean }>
}>()

const emit = defineEmits<{
  (event: 'new-chat'): void
  (event: 'update:selectedAgentKey', value: string): void
  (event: 'update:userId', value: string): void
}>()

const userSettingsOpen = ref(false)

const historySkeletons = computed(() => {
  if (props.historyItems.length > 0) {
    return props.historyItems
  }

  return [
    { id: 'placeholder-1', title: '历史会话占位 1' },
    { id: 'placeholder-2', title: '历史会话占位 2' },
    { id: 'placeholder-3', title: '历史会话占位 3' },
  ]
})
</script>

<template>
  <aside class="workspace-sidebar">
    <div class="workspace-sidebar__top">
      <button
        data-testid="new-chat"
        class="workspace-sidebar__new-chat accent-button"
        type="button"
        @click="emit('new-chat')"
      >
        新建会话
      </button>

      <label class="workspace-sidebar__agent">
        <span class="workspace-sidebar__label">Agent</span>
        <select
          data-testid="agent-select"
          :value="selectedAgentKey"
          class="workspace-sidebar__select"
          @change="emit('update:selectedAgentKey', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="agent in agents" :key="agent.agentKey" :value="agent.agentKey">
            {{ agent.name }}
          </option>
        </select>
      </label>
    </div>

    <div class="workspace-sidebar__history">
      <p class="workspace-sidebar__section-title">历史对话</p>

      <button
        v-for="item in historySkeletons"
        :key="item.id"
        class="workspace-sidebar__history-item"
        type="button"
      >
        {{ item.title }}
      </button>

      <p v-if="historyItems.length === 0" class="workspace-sidebar__empty">
        历史会话接入后会显示在这里。
      </p>
    </div>

    <div class="workspace-sidebar__footer">
      <button
        data-testid="toggle-user-settings"
        class="workspace-sidebar__user-toggle ghost-button"
        type="button"
        @click="userSettingsOpen = !userSettingsOpen"
      >
        用户 ID
      </button>

      <div v-if="userSettingsOpen" class="workspace-sidebar__user-settings">
        <label class="workspace-sidebar__user-field">
          <span class="workspace-sidebar__label">当前用户</span>
          <input
            data-testid="user-id-input"
            :value="userId"
            class="workspace-sidebar__input"
            type="text"
            @input="emit('update:userId', ($event.target as HTMLInputElement).value)"
          />
        </label>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.workspace-sidebar {
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 18px;
  min-height: 100vh;
  padding: 18px 14px;
  border-right: 1px solid var(--border);
  background: var(--surface-subtle);
}

.workspace-sidebar__top,
.workspace-sidebar__footer,
.workspace-sidebar__user-settings {
  display: grid;
  gap: 12px;
}

.workspace-sidebar__label,
.workspace-sidebar__section-title {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.workspace-sidebar__agent,
.workspace-sidebar__user-field {
  display: grid;
  gap: 8px;
}

.workspace-sidebar__select,
.workspace-sidebar__input {
  width: 100%;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  color: var(--text-strong);
}

.workspace-sidebar__history {
  display: grid;
  align-content: start;
  gap: 8px;
}

.workspace-sidebar__history-item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.75);
  color: var(--text-soft);
  text-align: left;
}

.workspace-sidebar__empty {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.5;
}
</style>

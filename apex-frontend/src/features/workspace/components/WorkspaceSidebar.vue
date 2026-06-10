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
    { id: 'placeholder-1', title: '新会话将显示在这里' },
    { id: 'placeholder-2', title: '后续可接入真实历史列表' },
    { id: 'placeholder-3', title: '支持搜索、置顶与分组' },
  ]
})
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

      <div class="workspace-sidebar__quick-actions">
        <button class="workspace-sidebar__quick-action ghost-button" type="button" disabled>
          规划任务
        </button>
        <button class="workspace-sidebar__quick-action ghost-button" type="button" disabled>
          最近产物
        </button>
        <button class="workspace-sidebar__quick-action ghost-button" type="button" disabled>
          待确认
        </button>
      </div>
    </div>

    <div class="workspace-sidebar__history">
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
  gap: 22px;
  min-height: 100vh;
  padding: 18px 14px;
  border-right: 1px solid var(--border);
  background:
    linear-gradient(180deg, rgba(248, 246, 241, 0.96), rgba(244, 241, 235, 0.96));
}

.workspace-sidebar__top,
.workspace-sidebar__footer,
.workspace-sidebar__user-settings {
  display: grid;
  gap: 12px;
}

.workspace-sidebar__brand {
  display: grid;
  gap: 2px;
  padding: 4px 2px 2px;
}

.workspace-sidebar__brand-title {
  font-size: 1.28rem;
  font-weight: 700;
  letter-spacing: -0.04em;
}

.workspace-sidebar__brand-subtitle,
.workspace-sidebar__label,
.workspace-sidebar__section-title,
.workspace-sidebar__section-note {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.workspace-sidebar__new-chat {
  justify-content: center;
}

.workspace-sidebar__agent,
.workspace-sidebar__user-field {
  display: grid;
  gap: 8px;
}

.workspace-sidebar__select,
.workspace-sidebar__input {
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--text-strong);
}

.workspace-sidebar__quick-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.workspace-sidebar__quick-action {
  min-height: 38px;
  padding: 0 8px;
  font-size: 0.84rem;
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
  padding: 11px 12px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--text-soft);
  text-align: left;
}

.workspace-sidebar__history-item:hover,
.workspace-sidebar__history-item--active {
  border-color: var(--border-strong);
  background: rgba(255, 255, 255, 0.92);
  color: var(--text-strong);
}

.workspace-sidebar__empty {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .workspace-sidebar {
    min-height: auto;
  }

  .workspace-sidebar__quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>

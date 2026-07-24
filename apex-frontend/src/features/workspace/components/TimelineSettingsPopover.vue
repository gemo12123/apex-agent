<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AgentSummary } from '@/types/apex'

const props = defineProps<{
  agents: AgentSummary[]
  selectedAgentKey: string
  userId: string
  hasStarted: boolean
}>()

const emit = defineEmits<{
  (event: 'save', payload: { agentKey: string; userId: string }): void
  (event: 'cancel'): void
  (event: 'close'): void
}>()

const draftAgentKey = ref(props.selectedAgentKey)
const draftUserId = ref(props.userId)

watch(
  () => [props.selectedAgentKey, props.userId] as const,
  ([nextAgentKey, nextUserId]) => {
    draftAgentKey.value = nextAgentKey
    draftUserId.value = nextUserId
  },
  { immediate: true },
)

function handleSave(): void {
  emit('save', {
    agentKey: draftAgentKey.value,
    userId: draftUserId.value.trim() || 'demo-user',
  })
}

function handleCancel(): void {
  draftAgentKey.value = props.selectedAgentKey
  draftUserId.value = props.userId
  emit('cancel')
}
</script>

<template>
  <section class="timeline-settings-popover">
    <header class="timeline-settings-popover__header">
      <div>
        <p class="timeline-settings-popover__eyebrow">Session Settings</p>
        <h3 class="timeline-settings-popover__title">会话设置</h3>
      </div>
    </header>

    <p v-if="hasStarted" class="timeline-settings-popover__hint">
      对话开始后不可修改智能体和用户 ID
    </p>

    <label class="timeline-settings-popover__field">
      <span class="timeline-settings-popover__label">智能体</span>
      <select
        data-testid="settings-agent-select"
        :value="draftAgentKey"
        class="timeline-settings-popover__control"
        :disabled="hasStarted"
        @change="draftAgentKey = ($event.target as HTMLSelectElement).value"
      >
        <option v-for="agent in agents" :key="agent.agentKey" :value="agent.agentKey">
          {{ agent.name }}
        </option>
      </select>
    </label>

    <label class="timeline-settings-popover__field">
      <span class="timeline-settings-popover__label">用户 ID</span>
      <input
        data-testid="settings-user-id-input"
        :value="draftUserId"
        class="timeline-settings-popover__control"
        type="text"
        :disabled="hasStarted"
        @input="draftUserId = ($event.target as HTMLInputElement).value"
      />
    </label>

    <footer class="timeline-settings-popover__actions">
      <template v-if="hasStarted">
        <button
          data-testid="settings-close"
          class="accent-button"
          type="button"
          @click="emit('close')"
        >
          关闭
        </button>
      </template>
      <template v-else>
        <button
          data-testid="settings-cancel"
          class="ghost-button"
          type="button"
          @click="handleCancel"
        >
          取消
        </button>
        <button
          data-testid="settings-save"
          class="accent-button"
          type="button"
          @click="handleSave"
        >
          保存
        </button>
      </template>
    </footer>
  </section>
</template>

<style scoped>
.timeline-settings-popover {
  display: grid;
  gap: 12px;
  width: min(100%, 288px);
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.14);
}

.timeline-settings-popover__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.timeline-settings-popover__eyebrow,
.timeline-settings-popover__label {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.timeline-settings-popover__title {
  margin: 4px 0 0;
  font-size: 1rem;
}

.timeline-settings-popover__hint {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--surface-subtle);
  color: var(--text-soft);
  line-height: 1.5;
}

.timeline-settings-popover__field {
  display: grid;
  gap: 8px;
}

.timeline-settings-popover__control {
  width: 100%;
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-control);
  background: rgba(255, 255, 255, 0.96);
  color: var(--text-strong);
}

.timeline-settings-popover__control:disabled {
  cursor: not-allowed;
  background: rgba(241, 245, 249, 0.88);
  color: var(--text-soft);
}

.timeline-settings-popover__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

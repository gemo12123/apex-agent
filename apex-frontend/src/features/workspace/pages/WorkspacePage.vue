<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import ChatPane from '@/features/workspace/components/ChatPane.vue'
import DetailPanel from '@/features/workspace/components/DetailPanel.vue'
import ExecutionRail from '@/features/workspace/components/ExecutionRail.vue'
import WelcomeScreen from '@/features/workspace/components/WelcomeScreen.vue'
import { useSessionStore } from '@/stores/session/store'

const sessionStore = useSessionStore()
const {
  agents,
  errorMessage,
  hasStarted,
  isLoadingAgents,
  selectedAgentKey,
  selectedArtifact,
  selectedInvocation,
  session,
  userId,
} = storeToRefs(sessionStore)

const selectedInvocationId = computed(() => selectedInvocation.value?.id ?? null)
const selectedArtifactId = computed(() => selectedArtifact.value?.id ?? null)

onMounted(() => {
  void sessionStore.initialize()
})

function handlePromptSubmit(value: string): void {
  void sessionStore.sendPrompt(value)
}

function handleHumanPrompt(payload: {
  prompt: (typeof session.value.pendingPrompts)[number]
  answer: string | string[]
}): void {
  void sessionStore.answerPrompt(payload.prompt, payload.answer)
}
</script>

<template>
  <main class="workspace-page">
    <WelcomeScreen
      v-if="!hasStarted"
      :agents="agents"
      :selected-agent-key="selectedAgentKey"
      :user-id="userId"
      :loading="isLoadingAgents"
      :error-message="errorMessage"
      @submit="handlePromptSubmit"
      @update:selected-agent-key="sessionStore.setSelectedAgent"
      @update:user-id="sessionStore.setUserId"
    />

    <div v-else class="workspace-page__shell">
      <header class="workspace-page__header">
        <div class="workspace-page__intro">
          <p class="workspace-page__eyebrow">Apex 工作台</p>
          <h1 class="workspace-page__headline">当前会话</h1>
          <p class="workspace-page__description">在左侧继续对话，在右侧查看计划、调用和产物。</p>
        </div>

        <div class="workspace-page__controls">
          <label class="workspace-page__field">
            <span>代理</span>
            <select
              :value="selectedAgentKey"
              class="workspace-page__input"
              @change="sessionStore.setSelectedAgent(($event.target as HTMLSelectElement).value)"
            >
              <option v-for="agent in agents" :key="agent.agentKey" :value="agent.agentKey">
                {{ agent.name }}
              </option>
            </select>
          </label>

          <label class="workspace-page__field">
            <span>用户 ID</span>
            <input
              :value="userId"
              class="workspace-page__input"
              type="text"
              @input="sessionStore.setUserId(($event.target as HTMLInputElement).value)"
            />
          </label>
        </div>
      </header>

      <p v-if="errorMessage" class="workspace-page__error">{{ errorMessage }}</p>

      <section class="workspace-page__layout">
        <ChatPane
          :messages="session.messages"
          :pending-prompts="session.pendingPrompts"
          :status="session.status"
          @send="handlePromptSubmit"
          @stop="sessionStore.stopStream"
          @submit-prompt="handleHumanPrompt"
        />

        <aside class="workspace-page__sidebar">
          <ExecutionRail
            :stages="session.stages"
            :global-artifacts="session.globalArtifacts"
            :selected-invocation-id="selectedInvocationId"
            :selected-artifact-id="selectedArtifactId"
            @select-invocation="sessionStore.selectInvocation($event.stageId, $event.invocationId)"
            @select-artifact="sessionStore.selectArtifact($event.scope, $event.artifactId, $event.stageId)"
          />

          <DetailPanel :invocation="selectedInvocation" :artifact="selectedArtifact" />
        </aside>
      </section>
    </div>
  </main>
</template>

<style scoped>
.workspace-page {
  min-height: 100vh;
}

.workspace-page__shell {
  min-height: 100vh;
  padding: 20px;
}

.workspace-page__header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid var(--border-strong);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}

.workspace-page__intro {
  display: grid;
  gap: 6px;
}

.workspace-page__eyebrow {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-size: 0.84rem;
}

.workspace-page__headline {
  font-size: 1.56rem;
}

.workspace-page__description {
  color: var(--text-soft);
}

.workspace-page__controls {
  display: flex;
  gap: 12px;
}

.workspace-page__field {
  display: grid;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.88rem;
}

.workspace-page__input {
  min-width: 180px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-subtle);
  color: var(--text-strong);
  font: inherit;
}

.workspace-page__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 16px;
  min-height: calc(100vh - 152px);
}

.workspace-page__sidebar {
  display: grid;
  grid-template-rows: minmax(260px, 1fr) minmax(260px, 1fr);
  gap: 16px;
  min-height: 0;
}

.workspace-page__error {
  margin: 0 0 16px;
  padding: 12px 14px;
  border: 1px solid rgba(220, 38, 38, 0.18);
  border-radius: 14px;
  background: #fff3f2;
  color: var(--danger);
}

@media (max-width: 1080px) {
  .workspace-page__shell {
    padding: 18px;
  }

  .workspace-page__header {
    align-items: stretch;
    flex-direction: column;
  }

  .workspace-page__controls {
    flex-direction: column;
  }

  .workspace-page__layout {
    grid-template-columns: 1fr;
  }

  .workspace-page__sidebar {
    grid-template-rows: auto auto;
  }
}
</style>

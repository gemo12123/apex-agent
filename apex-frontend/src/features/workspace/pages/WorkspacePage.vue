<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import ChatPane from '@/features/workspace/components/ChatPane.vue'
import TimelineDrawer from '@/features/workspace/components/TimelineDrawer.vue'
import WorkspaceSidebar from '@/features/workspace/components/WorkspaceSidebar.vue'
import { buildTimelineEntries } from '@/features/workspace/timeline'
import { useSessionStore } from '@/stores/session/store'

const sessionStore = useSessionStore()
const {
  agents,
  errorMessage,
  hasStarted,
  selectedAgentKey,
  session,
  userId,
} = storeToRefs(sessionStore)

const timelineOpen = ref(false)
const timelineEntries = computed(() => buildTimelineEntries(session.value))
const historyItems = computed<Array<{ id: string; title: string; active?: boolean }>>(() => [])

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

function handleToolConfirmation(payload: {
  confirmation: (typeof session.value.pendingConfirmations)[number]
  decision: 'APPROVE' | 'DENY'
  updatedArgs?: Record<string, unknown>
}): void {
  void sessionStore.submitConfirmation(
    payload.confirmation,
    payload.decision,
    payload.updatedArgs ?? {},
  )
}

function handleNewChat(): void {
  timelineOpen.value = false
  sessionStore.resetSession()
}

function toggleTimeline(): void {
  timelineOpen.value = !timelineOpen.value
}
</script>

<template>
  <main class="workspace-page" :class="{ 'workspace-page--timeline-open': timelineOpen }">
    <WorkspaceSidebar
      class="workspace-page__sidebar"
      :agents="agents"
      :selected-agent-key="selectedAgentKey"
      :user-id="userId"
      :history-items="historyItems"
      @new-chat="handleNewChat"
      @update:selected-agent-key="sessionStore.setSelectedAgent"
      @update:user-id="sessionStore.setUserId"
    />

    <section class="workspace-page__main">
      <p v-if="errorMessage" class="workspace-page__error">{{ errorMessage }}</p>

      <ChatPane
        :has-started="hasStarted"
        :messages="session.messages"
        :pending-prompts="session.pendingPrompts"
        :pending-confirmations="session.pendingConfirmations"
        :status="session.status"
        @send="handlePromptSubmit"
        @stop="sessionStore.stopStream"
        @toggle-timeline="toggleTimeline"
        @submit-prompt="handleHumanPrompt"
        @submit-confirmation="handleToolConfirmation"
      />
    </section>

    <TimelineDrawer
      class="workspace-page__timeline-drawer"
      :open="timelineOpen"
      :entries="timelineEntries"
      @close="timelineOpen = false"
    />
  </main>
</template>

<style scoped>
.workspace-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 278px minmax(0, 1fr);
  background:
    radial-gradient(circle at top, rgba(255, 255, 255, 0.56), transparent 42%),
    linear-gradient(180deg, rgba(245, 242, 236, 0.96), rgba(238, 234, 227, 0.96));
  transition: grid-template-columns 180ms ease;
}

.workspace-page--timeline-open {
  grid-template-columns: 278px minmax(0, 1fr) 380px;
}

.workspace-page__sidebar {
  min-width: 0;
}

.workspace-page__main {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 16px;
  min-width: 0;
  min-height: 100vh;
  padding: 24px;
}

.workspace-page__error {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid rgba(220, 38, 38, 0.16);
  border-radius: 16px;
  background: rgba(254, 242, 242, 0.86);
  color: var(--danger);
}

.workspace-page__timeline-drawer {
  min-width: 0;
  min-height: 100vh;
}

@media (max-width: 1100px) {
  .workspace-page,
  .workspace-page--timeline-open {
    grid-template-columns: 1fr;
  }

  .workspace-page__sidebar {
    min-height: auto;
    border-right: none;
    border-bottom: 1px solid var(--border);
  }

  .workspace-page__main {
    min-height: auto;
    padding: 16px;
  }

  .workspace-page__timeline-drawer {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    width: min(92vw, 380px);
    z-index: 20;
    box-shadow: -24px 0 60px rgba(15, 23, 42, 0.18);
  }
}
</style>

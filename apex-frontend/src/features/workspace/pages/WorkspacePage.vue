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

const sidebarOpen = ref(false)
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
  sidebarOpen.value = false
  timelineOpen.value = false
  sessionStore.resetSession()
}

function isCompactViewport(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(max-width: 1100px)').matches
}

function toggleTimeline(): void {
  if (isCompactViewport()) {
    sidebarOpen.value = false
  }
  timelineOpen.value = !timelineOpen.value
}

function toggleSidebar(): void {
  if (isCompactViewport()) {
    timelineOpen.value = false
  }
  sidebarOpen.value = !sidebarOpen.value
}

function closeOverlays(): void {
  sidebarOpen.value = false
  timelineOpen.value = false
}
</script>

<template>
  <main class="workspace-page" :class="{ 'workspace-page--timeline-open': timelineOpen }">
    <button
      v-if="sidebarOpen || timelineOpen"
      class="workspace-page__mobile-backdrop"
      type="button"
      aria-label="关闭侧边抽屉"
      @click="closeOverlays"
    />

    <WorkspaceSidebar
      class="workspace-page__sidebar"
      :class="{ 'workspace-page__sidebar--open': sidebarOpen }"
      :agents="agents"
      :selected-agent-key="selectedAgentKey"
      :user-id="userId"
      :history-items="historyItems"
      @new-chat="handleNewChat"
      @update:selected-agent-key="sessionStore.setSelectedAgent"
      @update:user-id="sessionStore.setUserId"
    />

    <section class="workspace-page__main">
      <div class="workspace-page__main-shell">
        <header class="workspace-page__mobile-bar">
          <button
            data-testid="toggle-sidebar"
            class="ghost-button"
            type="button"
            @click="toggleSidebar"
          >
            侧栏
          </button>
          <p class="workspace-page__mobile-title">Apex Workspace</p>
        </header>

        <p v-if="errorMessage" class="workspace-page__error">{{ errorMessage }}</p>

        <div class="workspace-page__main-column">
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
        </div>
      </div>
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
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  background: var(--page);
  transition: grid-template-columns 180ms ease;
}

.workspace-page--timeline-open {
  grid-template-columns: 260px minmax(0, 1fr) 344px;
}

.workspace-page__sidebar {
  min-width: 0;
}

.workspace-page__main {
  min-width: 0;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  padding: 18px 24px 0 28px;
  background: #fcfcfd;
}

.workspace-page__main-shell {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 8px;
  width: 100%;
  max-width: 1180px;
  min-height: 100%;
  height: 100%;
  margin: 0 auto;
}

.workspace-page__main-column {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex: 1;
}

.workspace-page__mobile-bar,
.workspace-page__mobile-backdrop {
  display: none;
}

.workspace-page__mobile-bar {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workspace-page__mobile-title {
  font-size: 0.92rem;
  font-weight: 700;
  letter-spacing: 0.02em;
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
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: min(88vw, 320px);
    min-height: 100vh;
    z-index: 21;
    transform: translateX(-100%);
    transition: transform 180ms ease;
    box-shadow: 20px 0 60px rgba(15, 23, 42, 0.16);
  }

  .workspace-page__sidebar--open {
    transform: translateX(0);
  }

  .workspace-page__main {
    min-height: auto;
    padding: 16px 16px 0;
  }

  .workspace-page__mobile-bar,
  .workspace-page__mobile-backdrop {
    display: flex;
  }

  .workspace-page__mobile-backdrop {
    position: fixed;
    inset: 0;
    z-index: 19;
    border: none;
    background: rgba(23, 23, 23, 0.22);
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

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import ConversationPane from '@/features/conversation/components/ConversationPane.vue'
import WorkspaceSidebar from '@/features/workspace/components/WorkspaceSidebar.vue'
import { useConversationStore } from '@/features/conversation/store'
import type { HumanPromptRecord, ToolConfirmationRecord } from '@/types/apex'

const store = useConversationStore()
const { agents, errorMessage, hasStarted, histories, loadingHistory, selectedAgentKey, session, userId } = storeToRefs(store)
const sidebarOpen = ref(false)
const timelineOpen = ref(false)
const historyItems = computed(() => histories.value.map((item) => ({
  id: item.sessionId,
  title: item.sessionSummary?.trim() || '未命名会话',
  subtitle: item.agentKey,
  active: item.sessionId === session.value.sessionId,
})))

onMounted(() => { void store.initialize() })
onBeforeUnmount(() => { store.stopStream() })

function handleSettings(payload: { agentKey: string; userId: string }): void {
  selectedAgentKey.value = payload.agentKey
  if (userId.value !== payload.userId.trim()) {
    userId.value = payload.userId.trim() || 'demo-user'
    localStorage.setItem('apex:user-id', userId.value)
    store.resetSession()
    void store.initialize()
  }
}
function toggleSidebar(): void { sidebarOpen.value = !sidebarOpen.value; if (sidebarOpen.value) timelineOpen.value = false }
function toggleTimeline(): void { timelineOpen.value = !timelineOpen.value; if (timelineOpen.value) sidebarOpen.value = false }
function answerPrompt(payload: { prompt: HumanPromptRecord; answer: string | string[] }): void { store.answerPrompt(payload.prompt, payload.answer) }
function answerConfirmation(payload: { confirmation: ToolConfirmationRecord; decision: 'APPROVE' | 'DENY'; updatedArgs?: Record<string, unknown> }): void { store.answerConfirmation(payload.confirmation, payload.decision, payload.updatedArgs) }
function isCompact(): boolean { return window.matchMedia('(max-width: 1100px)').matches }
</script>

<template>
  <main class="conversation-page" :class="{ 'conversation-page--timeline': timelineOpen }">
    <button v-if="sidebarOpen || (timelineOpen && isCompact())" class="conversation-page__backdrop" type="button" aria-label="关闭侧边栏" @click="sidebarOpen = false; timelineOpen = false" />
    <WorkspaceSidebar class="conversation-page__sidebar" :class="{ 'conversation-page__sidebar--open': sidebarOpen }" :agents="agents" :selected-agent-key="selectedAgentKey" :user-id="userId" :has-started="hasStarted" :history-items="historyItems" @new-chat="store.resetSession" @select-history="store.loadHistory" @save-settings="handleSettings" />
    <section class="conversation-page__main">
      <header class="conversation-page__mobile-bar"><button class="ghost-button" type="button" @click="toggleSidebar">侧栏</button><strong>对话工作台</strong><button class="ghost-button" type="button" @click="toggleTimeline">轨迹</button></header>
      <p v-if="errorMessage" class="conversation-page__error">{{ errorMessage }}</p>
      <p v-if="loadingHistory" class="conversation-page__loading">正在回显历史会话…</p>
      <ConversationPane :session="session" @send="store.sendPrompt" @stop="store.stopStream" @toggle-timeline="toggleTimeline" @answer-prompt="answerPrompt" @answer-confirmation="answerConfirmation" @skip-intervention="store.skipIntervention" @submit-interventions="store.submitInterventions" />
    </section>
    <aside v-if="timelineOpen" class="conversation-timeline" aria-label="执行轨迹">
      <header><div><p>Execution Timeline</p><h2>执行轨迹</h2></div><button class="ghost-button" type="button" @click="timelineOpen = false">收起</button></header>
      <div v-if="!session.turns.length" class="conversation-timeline__empty">执行开始后，这里会显示 Turn、Iteration 和工具调用。</div>
      <ol v-else class="conversation-timeline__list"><li v-for="turn in session.turns" :key="turn.no"><strong>Turn {{ String(turn.no).padStart(2, '0') }}</strong><ol><li v-for="iteration in turn.iterations" :key="iteration.no">Iteration {{ iteration.no }}<ul><li v-for="block in iteration.blocks.filter((item) => item.type === 'tool')" :key="block.id">{{ block.type === 'tool' ? block.toolName : '' }}</li></ul></li></ol></li></ol>
    </aside>
  </main>
</template>

<style scoped>
.conversation-page { display:grid; grid-template-columns:260px minmax(0,1fr); min-height:100vh; height:100dvh; background:var(--page); }.conversation-page--timeline { grid-template-columns:260px minmax(0,1fr) 344px; }.conversation-page__main { display:flex; min-width:0; min-height:0; flex-direction:column; padding:18px 24px 0 28px; overflow:hidden; background:#fcfcfd; }.conversation-page__main > :not(.conversation-page__mobile-bar):not(.conversation-page__error):not(.conversation-page__loading) { width:100%; max-width:840px; margin:0 auto; }.conversation-page__mobile-bar,.conversation-page__backdrop { display:none; }.conversation-page__error,.conversation-page__loading { margin:0 0 10px; padding:10px 12px; border-radius:12px; font-size:.86rem; }.conversation-page__error { background:#fef2f2; color:#b91c1c; }.conversation-page__loading { background:#f8fafc; color:var(--text-soft); }.conversation-timeline { display:grid; min-height:0; grid-template-rows:auto 1fr; border-left:1px solid var(--border); background:rgba(250,251,252,.96); }.conversation-timeline header { display:flex; align-items:center; justify-content:space-between; padding:14px 16px; border-bottom:1px solid var(--border); }.conversation-timeline p { margin:0; color:var(--text-muted); font:700 .7rem ui-monospace,SFMono-Regular,Menlo,monospace; letter-spacing:.06em; text-transform:uppercase; }.conversation-timeline h2 { margin:4px 0 0; font-size:1rem; }.conversation-timeline__empty { padding:18px; color:var(--text-soft); line-height:1.7; }.conversation-timeline__list { min-height:0; margin:0; overflow:auto; padding:16px 20px; color:var(--text-soft); line-height:1.8; }.conversation-timeline__list ol,.conversation-timeline__list ul { padding-left:18px; }.conversation-timeline__list strong { color:var(--text-strong); }
@media (max-width:1100px) { .conversation-page,.conversation-page--timeline { grid-template-columns:1fr; }.conversation-page__sidebar { position:fixed; top:0; bottom:0; left:0; z-index:21; width:min(88vw,320px); transform:translateX(-100%); transition:transform 180ms ease; box-shadow:20px 0 60px rgba(15,23,42,.16); }.conversation-page__sidebar--open { transform:translateX(0); }.conversation-page__backdrop { position:fixed; inset:0; z-index:20; display:block; border:0; background:rgba(23,23,23,.22); }.conversation-timeline { position:fixed; top:0; right:0; bottom:0; z-index:22; width:min(92vw,380px); box-shadow:-24px 0 60px rgba(15,23,42,.18); }.conversation-page__mobile-bar { display:flex; align-items:center; justify-content:space-between; margin-bottom:10px; }.conversation-page__main { padding:16px 16px 0; } }
</style>

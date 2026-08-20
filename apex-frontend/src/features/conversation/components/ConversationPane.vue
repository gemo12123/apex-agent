<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import HumanPromptCard from '@/features/workspace/components/HumanPromptCard.vue'
import ToolConfirmationCard from '@/features/workspace/components/ToolConfirmationCard.vue'
import ConversationIterationSection from '@/features/conversation/components/ConversationIterationSection.vue'
import type { ConversationViewModel } from '@/features/conversation/conversation'
import type { HumanPromptRecord, PendingInterventionRecord, ToolConfirmationRecord } from '@/types/apex'

const props = defineProps<{ session: ConversationViewModel }>()
const emit = defineEmits<{
  (event: 'send', value: string): void
  (event: 'stop'): void
  (event: 'toggle-timeline'): void
  (event: 'answer-prompt', payload: { prompt: HumanPromptRecord; answer: string | string[] }): void
  (event: 'answer-confirmation', payload: { confirmation: ToolConfirmationRecord; decision: 'APPROVE' | 'DENY'; updatedArgs?: Record<string, unknown> }): void
  (event: 'skip-intervention', value: PendingInterventionRecord): void
  (event: 'submit-interventions'): void
}>()

const draft = ref('')
const transcript = ref<HTMLElement | null>(null)
const expandedTurns = ref(new Set<string>())
const expandedIterations = ref(new Set<string>())
const followBottom = ref(true)
const showBackToBottom = ref(false)
const canSubmitInterventions = computed(() => props.session.pendingInterventions.length > 0 && props.session.pendingInterventions.every((item) => item.resolution !== 'pending'))
const currentStatus = computed(() => ({ idle: '待开始', streaming: '处理中', 'waiting-intervention': '等待确认', completed: '已完成', aborted: '已停止', error: '异常' })[props.session.status])

watch(() => props.session.turns.map((turn) => `${turn.no}:${turn.iterations.map((item) => item.no).join(',')}`).join('|'), async () => {
  const latest = props.session.turns.at(-1)
  if (latest) {
    expandedTurns.value = new Set([String(latest.no)])
    const current = latest.iterations.at(-1)
    if (current) expandedIterations.value = new Set([`${latest.no}:${current.no}`])
  }
  await nextTick()
  if (followBottom.value && transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight
}, { immediate: true })

watch(() => props.session.turns, async () => {
  await nextTick()
  if (followBottom.value && transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight
}, { deep: true })

function toggleTurn(no: number): void { const next = new Set(expandedTurns.value); next.has(String(no)) ? next.delete(String(no)) : next.add(String(no)); expandedTurns.value = next }
function toggleIteration(key: string): void { const next = new Set(expandedIterations.value); next.has(key) ? next.delete(key) : next.add(key); expandedIterations.value = next }
function expandAll(): void { expandedTurns.value = new Set(props.session.turns.map((item) => String(item.no))); expandedIterations.value = new Set(props.session.turns.flatMap((turn) => turn.iterations.map((item) => `${turn.no}:${item.no}`))) }
function collapseAll(): void { expandedTurns.value = new Set(); expandedIterations.value = new Set() }
function submit(): void { const value = draft.value.trim(); if (!value || props.session.status === 'streaming' || props.session.status === 'waiting-intervention') return; emit('send', value); draft.value = '' }
function onScroll(): void { const element = transcript.value; if (!element) return; followBottom.value = element.scrollHeight - element.scrollTop - element.clientHeight < 60; showBackToBottom.value = !followBottom.value }
function backToBottom(): void { transcript.value?.scrollTo({ top: transcript.value.scrollHeight, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' }); followBottom.value = true; showBackToBottom.value = false }
function isHuman(item: PendingInterventionRecord): item is HumanPromptRecord { return item.kind === 'question' }
</script>

<template>
  <section class="conversation-pane">
    <header v-if="session.turns.length" class="conversation-pane__toolbar">
      <div><p>当前会话</p><strong :title="session.sessionId ?? ''">{{ session.sessionId?.slice(0, 12) }}…</strong></div>
      <div class="conversation-pane__toolbar-actions">
        <button class="ghost-button" type="button" @click="collapseAll">折叠全部</button>
        <button class="ghost-button" type="button" @click="expandAll">展开全部</button>
        <button class="ghost-button" type="button" @click="emit('toggle-timeline')">执行轨迹</button>
        <span class="conversation-pane__status">{{ currentStatus }}</span>
      </div>
    </header>

    <div v-if="!session.turns.length" class="conversation-pane__empty"><h1>开始一段新的对话</h1><p>问题、模型迭代和工具执行会在这里按真实顺序展示。</p></div>
    <div v-else ref="transcript" class="conversation-pane__transcript" @scroll="onScroll">
      <article v-for="turn in session.turns" :key="turn.no" class="turn-card">
        <header class="turn-card__header">
          <button class="turn-card__toggle" type="button" :aria-expanded="expandedTurns.has(String(turn.no))" @click="toggleTurn(turn.no)">›</button>
          <div class="turn-card__copy"><p>Turn {{ String(turn.no).padStart(2, '0') }}</p><strong>{{ turn.question }}</strong></div>
          <span class="turn-card__status">{{ turn.status === 'waiting-intervention' ? '等待确认' : turn.status === 'streaming' ? '处理中' : turn.status === 'error' ? '异常' : '已完成' }}</span>
        </header>
        <template v-if="expandedTurns.has(String(turn.no))">
          <div class="turn-card__question">{{ turn.question }}</div>
          <div class="turn-card__iterations">
            <ConversationIterationSection v-for="iteration in turn.iterations" :key="iteration.no" :turn-no="turn.no" :iteration="iteration" :open="expandedIterations.has(`${turn.no}:${iteration.no}`)" @toggle="toggleIteration(`${turn.no}:${iteration.no}`)" />
          </div>
        </template>
        <p v-else class="turn-card__folded">已折叠 · {{ turn.iterations.length }} 个迭代</p>
      </article>
    </div>

    <button v-if="showBackToBottom" class="conversation-pane__back" type="button" aria-label="回到底部" @click="backToBottom">回到底部</button>
    <footer class="conversation-pane__composer">
      <div class="composer-shell" :class="{ 'composer-shell--intervention': session.status === 'waiting-intervention' }">
        <template v-if="session.status === 'waiting-intervention'">
          <section class="composer-shell__interventions" aria-live="polite"><h2>需要人工介入 · {{ session.pendingInterventions.length }} 项</h2>
            <template v-for="item in session.pendingInterventions" :key="item.id">
              <HumanPromptCard v-if="isHuman(item)" :prompt="item" :show-batch-submit="false" :batch-can-submit="false" @answer="emit('answer-prompt', { prompt: item, answer: $event })" @skip="emit('skip-intervention', item)" />
              <ToolConfirmationCard v-else :confirmation="item" :show-batch-submit="false" :batch-can-submit="false" @answer="emit('answer-confirmation', { confirmation: item, ...$event })" @skip="emit('skip-intervention', item)" />
            </template>
          </section>
          <div class="composer-shell__waiting"><span>等待你的确认 · 已处理 {{ session.pendingInterventions.filter((item) => item.resolution !== 'pending').length }}/{{ session.pendingInterventions.length }}</span><button class="accent-button" type="button" :disabled="!canSubmitInterventions" @click="emit('submit-interventions')">提交并继续</button></div>
        </template>
        <template v-else>
          <label class="sr-only" for="conversation-draft">输入消息</label><textarea id="conversation-draft" v-model="draft" rows="1" :disabled="session.status === 'streaming'" :placeholder="session.turns.length ? '继续补充上下文、追问上一轮结果，或者给 Apex 一个新的执行方向。' : '有问题，尽管问'" @keydown.enter.exact.prevent="session.status === 'streaming' ? emit('stop') : submit()" />
          <button class="composer-shell__send" type="button" :disabled="session.status !== 'streaming' && !draft.trim()" @click="session.status === 'streaming' ? emit('stop') : submit()">{{ session.status === 'streaming' ? '停止' : session.turns.length ? '发送' : '开始' }}</button>
        </template>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.conversation-pane { position: relative; display: flex; flex: 1; min-width: 0; min-height: 0; flex-direction: column; }.conversation-pane__toolbar { display:flex; justify-content:space-between; align-items:center; gap:16px; padding:4px 0 14px; }.conversation-pane__toolbar p,.turn-card__copy p { margin:0 0 4px; color:var(--text-muted); font:700 .7rem ui-monospace,SFMono-Regular,Menlo,monospace; letter-spacing:.07em; text-transform:uppercase; }.conversation-pane__toolbar strong { font-size:.84rem; }.conversation-pane__toolbar-actions { display:flex; align-items:center; gap:8px; }.conversation-pane__status,.turn-card__status { padding:5px 9px; border-radius:999px; background:#f1f2f6; color:var(--text-soft); font-size:.76rem; white-space:nowrap; }.conversation-pane__empty { display:grid; place-content:center; flex:1; text-align:center; }.conversation-pane__empty h1 { margin:0; font-size:1.5rem; }.conversation-pane__empty p { color:var(--text-muted); }.conversation-pane__transcript { min-height:0; flex:1; overflow:auto; padding:4px 2px 110px; }.turn-card { margin-bottom:16px; overflow:hidden; border:1px solid var(--border); border-radius:18px; background:rgba(255,255,255,.92); box-shadow:0 12px 30px -28px rgba(15,23,42,.38); }.turn-card__header { display:flex; align-items:center; gap:10px; padding:14px; }.turn-card__toggle { width:24px; height:24px; border:0; background:transparent; color:var(--text-muted); font-size:1.5rem; line-height:.7; cursor:pointer; }.turn-card__copy { min-width:0; display:grid; gap:2px; }.turn-card__copy strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.turn-card__status { margin-left:auto; }.turn-card__question { max-width:68ch; margin:0 14px 14px auto; padding:10px 14px; border-radius:16px; background:#f1f2f6; line-height:1.6; white-space:pre-wrap; }.turn-card__iterations { display:grid; gap:10px; padding:0 14px 14px; }.turn-card__folded { margin:0; padding:0 14px 14px; color:var(--text-muted); font-size:.84rem; }.conversation-pane__composer { position:absolute; right:0; bottom:0; left:0; padding:30px 0 8px; background:linear-gradient(180deg,transparent,#fcfcfd 42%); }.composer-shell { display:flex; align-items:center; gap:10px; width:min(100%,768px); min-height:58px; margin:auto; padding:0 8px 0 18px; border:1px solid rgba(15,23,42,.12); border-radius:999px; background:#fff; box-shadow:0 18px 34px -26px rgba(15,23,42,.3); }.composer-shell textarea { width:100%; height:24px; min-height:24px; border:0; outline:0; background:transparent; color:var(--text-strong); font:inherit; line-height:1.5; resize:none; }.composer-shell__send { min-width:58px; height:36px; padding:0 16px; border:0; border-radius:999px; background:#050505; color:#fff; font-weight:600; }.composer-shell__send:disabled { opacity:.45; }.composer-shell--intervention { display:block; width:min(100%,768px); max-height:55vh; padding:0; overflow:hidden; border-radius:20px; }.composer-shell__interventions { max-height:42vh; overflow:auto; padding:14px; }.composer-shell__interventions h2 { margin:0 0 12px; font-size:.9rem; }.composer-shell__waiting { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 14px; border-top:1px solid var(--border); color:var(--text-soft); font-size:.84rem; }.conversation-pane__back { position:absolute; right:10px; bottom:84px; z-index:2; padding:8px 12px; border:1px solid var(--border); border-radius:999px; background:#fff; box-shadow:0 8px 22px -12px rgba(15,23,42,.3); cursor:pointer; }@media (max-width:720px) { .conversation-pane__toolbar { align-items:flex-start; flex-direction:column; }.conversation-pane__toolbar-actions { width:100%; overflow:auto; }.composer-shell__waiting { align-items:stretch; flex-direction:column; }.composer-shell__waiting .accent-button { width:100%; justify-content:center; } }
</style>

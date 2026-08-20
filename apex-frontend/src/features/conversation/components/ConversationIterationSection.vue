<script setup lang="ts">
import { computed } from 'vue'
import ConversationToolCard from '@/features/conversation/components/ConversationToolCard.vue'
import { renderMarkdown } from '@/utils/markdown'
import type { ConversationIteration } from '@/features/conversation/conversation'

const props = defineProps<{ iteration: ConversationIteration; open: boolean; turnNo: number }>()
const emit = defineEmits<{ (event: 'toggle'): void }>()
const summary = computed(() => {
  const tools = props.iteration.blocks.filter((block) => block.type === 'tool').length
  if (props.iteration.status === 'waiting') return '等待确认'
  if (props.iteration.status === 'streaming') return tools ? `${tools} 个工具调用 · 正在执行` : '正在生成正文'
  return tools ? `${tools} 个工具调用` : '正文回复'
})
</script>

<template>
  <section class="iteration" :data-iteration="`${turnNo}:${iteration.no}`">
    <button class="iteration__bar" type="button" :aria-expanded="open" @click="emit('toggle')">
      <span class="iteration__chevron" :class="{ 'iteration__chevron--open': open }" aria-hidden="true">›</span>
      <span class="iteration__kicker">Iteration {{ iteration.no }}</span>
      <span v-if="iteration.resumed" class="iteration__resumed">恢复继续</span>
      <span class="iteration__summary">{{ summary }}</span>
      <span class="iteration__dot" :class="`iteration__dot--${iteration.status}`" aria-hidden="true" />
    </button>
    <div v-show="open" class="iteration__body">
      <template v-for="block in iteration.blocks" :key="block.id">
        <details v-if="block.type === 'think'" class="think-block" :open="block.streaming">
          <summary>思考过程</summary>
          <div class="think-block__content">{{ block.content }}</div>
        </details>
        <div v-else-if="block.type === 'content'" class="markdown content-block" v-html="renderMarkdown(block.content || '_正在生成回复…_')" />
        <ConversationToolCard v-else-if="block.type === 'tool'" :block="block" />
        <div v-else class="error-block">{{ block.content }}</div>
      </template>
      <p v-if="iteration.blocks.length === 0 && iteration.status === 'streaming'" class="iteration__placeholder">正在准备回复…</p>
    </div>
  </section>
</template>

<style scoped>
.iteration { overflow: hidden; border: 1px solid var(--border); border-radius: 16px; background: rgba(255,255,255,.72); }.iteration__bar { display: flex; align-items: center; width: 100%; gap: 10px; padding: 12px 14px; border: 0; background: transparent; color: var(--text-strong); text-align: left; cursor: pointer; }.iteration__chevron { transform: rotate(0deg); color: var(--text-muted); font-size: 1.5rem; line-height: .7; transition: transform 180ms ease; }.iteration__chevron--open { transform: rotate(90deg); }.iteration__kicker { font: 700 .7rem ui-monospace, SFMono-Regular, Menlo, monospace; letter-spacing: .06em; text-transform: uppercase; }.iteration__resumed { padding: 2px 7px; border-radius: 999px; background: #eef2ff; color: #4338ca; font-size: .72rem; }.iteration__summary { overflow: hidden; margin-left: auto; color: var(--text-muted); font-size: .8rem; text-overflow: ellipsis; white-space: nowrap; }.iteration__dot { flex: none; width: 7px; height: 7px; border-radius: 50%; background: #9ca3af; }.iteration__dot--streaming { background: #171717; animation: pulse 1.2s infinite; }.iteration__dot--completed { background: #16a34a; }.iteration__dot--waiting { background: #d97706; }.iteration__dot--error { background: #dc2626; }.iteration__body { display: grid; gap: 14px; padding: 4px 14px 16px; }.content-block { line-height: 1.72; }.think-block { border: 1px solid var(--border); border-left: 3px solid #9ca3af; border-radius: 12px; background: #f8fafc; }.think-block summary { padding: 9px 11px; cursor: pointer; color: var(--text-soft); font-size: .82rem; }.think-block__content { padding: 0 11px 11px; color: var(--text-soft); white-space: pre-wrap; font-size: .86rem; line-height: 1.65; }.error-block { padding: 12px; border: 1px solid rgba(220,38,38,.25); border-radius: 12px; background: #fef2f2; color: #b91c1c; }.iteration__placeholder { margin: 0; color: var(--text-muted); font-size: .88rem; }@keyframes pulse { 50% { opacity: .35; } }@media (prefers-reduced-motion: reduce) { .iteration__chevron { transition: none; }.iteration__dot--streaming { animation: none; } }
</style>

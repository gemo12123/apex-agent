<script setup lang="ts">
import { computed } from 'vue'
import type { ConversationBlock } from '@/features/conversation/conversation'

const props = defineProps<{ block: Extract<ConversationBlock, { type: 'tool' }> }>()

const statusLabel = computed(() => ({
  PENDING: '未完成', RUNNING: '执行中', COMPLETE: '已完成', FAILED: '失败', CANCELLED: '已取消',
})[props.block.status])

const resultPreview = computed(() => {
  const source = props.block.result ?? ''
  const lines = source.split('\n').slice(0, 6).join('\n')
  return lines.length > 800 ? `${lines.slice(0, 800)}…` : source.length > lines.length ? `${lines}…` : lines
})

function json(value: Record<string, unknown>): string { return JSON.stringify(value, null, 2) }
</script>

<template>
  <article class="tool-card" :class="`tool-card--${block.status.toLowerCase()}`">
    <header class="tool-card__head">
      <span class="tool-card__mark" aria-hidden="true" />
      <div class="tool-card__copy">
        <strong>{{ block.toolName }}</strong>
        <small>{{ block.id }}</small>
      </div>
      <span class="tool-card__status">{{ statusLabel }}</span>
    </header>

    <details class="tool-card__details">
      <summary>原始参数</summary>
      <pre>{{ json(block.arguments) }}</pre>
    </details>
    <details v-if="block.resolvedArguments" class="tool-card__details">
      <summary>最终参数</summary>
      <pre>{{ json(block.resolvedArguments) }}</pre>
    </details>
    <div v-if="block.result" class="tool-card__result">
      <p>工具结果</p>
      <pre>{{ resultPreview }}</pre>
      <small v-if="resultPreview.length < block.result.length">完整结果请在执行轨迹中查看</small>
    </div>
  </article>
</template>

<style scoped>
.tool-card { overflow: hidden; border: 1px solid var(--border); border-left: 3px solid #6b7280; border-radius: 14px; background: #fff; }
.tool-card--complete { border-left-color: #16a34a; }.tool-card--failed { border-left-color: #dc2626; }.tool-card--cancelled { border-left-color: #9ca3af; }.tool-card--running { border-left-color: #1f2937; }
.tool-card__head { display: flex; align-items: center; gap: 10px; padding: 12px 14px; }.tool-card__mark { width: 9px; height: 9px; border-radius: 50%; background: currentColor; }.tool-card__copy { display: grid; min-width: 0; gap: 2px; }.tool-card__copy small { overflow: hidden; color: var(--text-muted); font: 0.7rem ui-monospace, SFMono-Regular, Menlo, monospace; text-overflow: ellipsis; white-space: nowrap; }.tool-card__status { margin-left: auto; color: var(--text-soft); font-size: .78rem; white-space: nowrap; }
.tool-card__details { border-top: 1px solid var(--border); }.tool-card__details summary { padding: 9px 14px; cursor: pointer; color: var(--text-soft); font-size: .82rem; }.tool-card pre, .tool-card__result pre { max-height: 180px; margin: 0; overflow: auto; padding: 10px 14px 12px; background: #f8fafc; color: #334155; font: .76rem/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; word-break: break-word; }.tool-card__result { border-top: 1px solid var(--border); }.tool-card__result p { margin: 0; padding: 9px 14px 0; color: var(--text-muted); font-size: .8rem; }.tool-card__result small { display: block; padding: 0 14px 12px; color: var(--text-muted); font-size: .76rem; }
</style>

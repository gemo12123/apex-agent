<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import HumanPromptCard from '@/features/workspace/components/HumanPromptCard.vue'
import { formatSessionStatus } from '@/features/workspace/presentation'
import { renderMarkdown } from '@/utils/markdown'
import type { HumanPromptRecord, MessageRecord, SessionViewModel } from '@/types/apex'

const props = defineProps<{
  messages: MessageRecord[]
  pendingPrompts: HumanPromptRecord[]
  status: SessionViewModel['status']
}>()

const emit = defineEmits<{
  (event: 'send', value: string): void
  (event: 'stop'): void
  (event: 'submit-prompt', payload: { prompt: HumanPromptRecord; answer: string | string[] }): void
}>()

const draft = ref('')
const transcriptRef = ref<HTMLElement | null>(null)

watch(
  () => [props.messages.length, props.pendingPrompts.length, props.status],
  async () => {
    await nextTick()
    const element = transcriptRef.value
    if (element) {
      element.scrollTop = element.scrollHeight
    }
  },
)

function submitMessage(): void {
  const value = draft.value.trim()
  if (!value) {
    return
  }

  emit('send', value)
  draft.value = ''
}
</script>

<template>
  <section class="chat-pane">
    <header class="chat-pane__header">
      <div>
        <p class="chat-pane__eyebrow">对话区</p>
        <h2 class="chat-pane__title">实时输出</h2>
      </div>
      <span class="status-pill" :class="`status-pill--${props.status}`">{{ formatSessionStatus(props.status) }}</span>
    </header>

    <div ref="transcriptRef" class="chat-pane__transcript">
      <article
        v-for="message in props.messages"
        :key="message.id"
        class="chat-message"
        :class="`chat-message--${message.role}`"
      >
        <div class="chat-message__card">
          <div v-if="message.role === 'user'" class="chat-message__plain">{{ message.content }}</div>

          <template v-else>
            <details v-if="message.think" class="chat-message__think">
              <summary>查看思考过程</summary>
              <div class="markdown" v-html="renderMarkdown(message.think)" />
            </details>
            <div class="markdown" v-html="renderMarkdown(message.content || '_正在生成回复..._')" />
          </template>
        </div>
      </article>

      <div v-if="props.pendingPrompts.length" class="chat-pane__prompts">
        <HumanPromptCard
          v-for="prompt in props.pendingPrompts"
          :key="prompt.id"
          :prompt="prompt"
          @submit="emit('submit-prompt', { prompt, answer: $event })"
        />
      </div>
    </div>

    <footer class="chat-pane__composer">
      <textarea
        v-model="draft"
        class="chat-pane__textarea"
        rows="4"
        placeholder="继续输入任务，或补充你想让 Apex 执行的内容。"
        :disabled="props.status === 'streaming' || props.status === 'waiting-human'"
        @keydown.enter.exact.prevent="submitMessage"
      />

      <div class="chat-pane__actions">
        <button
          class="ghost-button"
          type="button"
          :disabled="props.status !== 'streaming'"
          @click="emit('stop')"
        >
          停止接收
        </button>
        <button
          class="accent-button"
          type="button"
          :disabled="!draft.trim() || props.status === 'streaming' || props.status === 'waiting-human'"
          @click="submitMessage"
        >
          发送
        </button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.chat-pane {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 0;
  border: 1px solid var(--border-strong);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow-panel);
}

.chat-pane__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
}

.chat-pane__eyebrow {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-size: 0.86rem;
}

.chat-pane__title {
  font-size: 1.16rem;
}

.chat-pane__transcript {
  min-height: 0;
  overflow: auto;
  padding: 20px;
  display: grid;
  gap: 16px;
}

.chat-message {
  display: flex;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message__card {
  max-width: min(78ch, 100%);
  padding: 14px 16px;
  border-radius: 16px;
}

.chat-message--user .chat-message__card {
  background: var(--text-strong);
  color: white;
}

.chat-message--assistant .chat-message__card {
  border: 1px solid var(--border);
  background: var(--surface-subtle);
}

.chat-message__plain {
  white-space: pre-wrap;
  line-height: 1.7;
}

.chat-message__think {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--border);
}

.chat-message__think summary {
  cursor: pointer;
  color: var(--text-muted);
  font-size: 0.92rem;
}

.chat-pane__prompts {
  display: grid;
  gap: 14px;
}

.chat-pane__composer {
  padding: 16px 20px 20px;
  border-top: 1px solid var(--border);
}

.chat-pane__textarea {
  width: 100%;
  min-height: 108px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-subtle);
  color: var(--text-strong);
  font: inherit;
  resize: vertical;
  box-sizing: border-box;
}

.chat-pane__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 720px) {
  .chat-pane__actions {
    flex-direction: column-reverse;
  }
}
</style>

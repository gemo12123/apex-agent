<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import HumanPromptCard from '@/features/workspace/components/HumanPromptCard.vue'
import { renderMarkdown } from '@/utils/markdown'
import type { HumanPromptRecord, MessageRecord } from '@/types/apex'

const props = defineProps<{
  messages: MessageRecord[]
  pendingPrompts: HumanPromptRecord[]
  status: string
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
        <p class="chat-pane__eyebrow">Conversation</p>
        <h2 class="chat-pane__title">Agent stream</h2>
      </div>
      <span class="status-pill" :class="`status-pill--${props.status}`">{{ props.status }}</span>
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
              <summary>Reasoning trace</summary>
              <div class="markdown" v-html="renderMarkdown(message.think)" />
            </details>
            <div class="markdown" v-html="renderMarkdown(message.content || '_Streaming response..._')" />
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
        placeholder="Send a new task to Apex..."
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
          Stop stream
        </button>
        <button
          class="accent-button"
          type="button"
          :disabled="!draft.trim() || props.status === 'streaming' || props.status === 'waiting-human'"
          @click="submitMessage"
        >
          Send
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
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-panel);
}

.chat-pane__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  border-bottom: 1px solid var(--border);
}

.chat-pane__eyebrow {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 0.74rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.chat-pane__title {
  margin: 0;
  font-size: 1.2rem;
}

.chat-pane__transcript {
  min-height: 0;
  overflow: auto;
  padding: 22px;
  display: grid;
  gap: 18px;
}

.chat-message {
  display: flex;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message__card {
  max-width: min(78ch, 100%);
  padding: 16px 18px;
  border-radius: 20px;
}

.chat-message--user .chat-message__card {
  background: linear-gradient(135deg, var(--accent-strong), var(--accent));
  color: white;
}

.chat-message--assistant .chat-message__card {
  border: 1px solid var(--border);
  background: var(--surface-2);
}

.chat-message__plain {
  white-space: pre-wrap;
  line-height: 1.7;
}

.chat-message__think {
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--border);
}

.chat-message__think summary {
  cursor: pointer;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.chat-pane__prompts {
  display: grid;
  gap: 14px;
}

.chat-pane__composer {
  padding: 18px 22px 22px;
  border-top: 1px solid var(--border);
}

.chat-pane__textarea {
  width: 100%;
  min-height: 112px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: var(--surface-2);
  color: var(--text-strong);
  font: inherit;
  resize: vertical;
  box-sizing: border-box;
}

.chat-pane__actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}
</style>

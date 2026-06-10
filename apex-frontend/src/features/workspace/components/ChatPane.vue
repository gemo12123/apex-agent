<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import HumanPromptCard from '@/features/workspace/components/HumanPromptCard.vue'
import ToolConfirmationCard from '@/features/workspace/components/ToolConfirmationCard.vue'
import WelcomeScreen from '@/features/workspace/components/WelcomeScreen.vue'
import { formatSessionStatus } from '@/features/workspace/presentation'
import { renderMarkdown } from '@/utils/markdown'
import type {
  HumanPromptRecord,
  MessageRecord,
  SessionViewModel,
  ToolConfirmationRecord,
} from '@/types/apex'

const props = withDefaults(
  defineProps<{
    hasStarted?: boolean
    messages: MessageRecord[]
    pendingPrompts: HumanPromptRecord[]
    pendingConfirmations: ToolConfirmationRecord[]
    status: SessionViewModel['status']
  }>(),
  {
    hasStarted: true,
  },
)

const emit = defineEmits<{
  (event: 'send', value: string): void
  (event: 'stop'): void
  (event: 'toggle-timeline'): void
  (event: 'submit-prompt', payload: { prompt: HumanPromptRecord; answer: string | string[] }): void
  (
    event: 'submit-confirmation',
    payload: {
      confirmation: ToolConfirmationRecord
      decision: 'APPROVE' | 'DENY'
      updatedArgs?: Record<string, unknown>
    },
  ): void
}>()

const draft = ref('')
const transcriptRef = ref<HTMLElement | null>(null)
const composerDisabled = computed(
  () =>
    props.status === 'streaming' ||
    props.status === 'waiting-human' ||
    props.status === 'waiting-confirmation',
)

watch(
  () => [
    props.hasStarted,
    props.messages.length,
    props.pendingPrompts.length,
    props.pendingConfirmations.length,
    props.status,
  ],
  async () => {
    if (!props.hasStarted) {
      return
    }

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
  <section class="chat-pane" :class="{ 'chat-pane--empty': !props.hasStarted }">
    <header class="chat-pane__header">
      <div class="chat-pane__header-copy">
        <p class="chat-pane__eyebrow">{{ props.hasStarted ? '当前会话' : '新的会话' }}</p>
        <h2 class="chat-pane__title">
          {{ props.hasStarted ? '和 Apex 一起推进任务' : '准备开始一段新的对话' }}
        </h2>
      </div>

      <div class="chat-pane__header-actions">
        <button
          data-testid="toggle-timeline"
          class="ghost-button"
          type="button"
          @click="emit('toggle-timeline')"
        >
          执行轨迹
        </button>

        <span class="status-pill" :class="`status-pill--${props.status}`">
          {{ formatSessionStatus(props.status) }}
        </span>
      </div>
    </header>

    <div v-if="!props.hasStarted" class="chat-pane__welcome">
      <WelcomeScreen @submit="emit('send', $event)" />
    </div>

    <template v-else>
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

        <div v-if="props.pendingConfirmations.length" class="chat-pane__prompts">
          <ToolConfirmationCard
            v-for="confirmation in props.pendingConfirmations"
            :key="confirmation.id"
            :confirmation="confirmation"
            @submit="emit('submit-confirmation', { confirmation, ...$event })"
          />
        </div>

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
        <label class="sr-only" for="chat-pane-draft">继续输入任务</label>
        <textarea
          id="chat-pane-draft"
          v-model="draft"
          class="chat-pane__textarea"
          rows="4"
          placeholder="继续补充上下文、追问上一步结果，或者给 Apex 一个新的执行方向。"
          :disabled="composerDisabled"
          @keydown.enter.exact.prevent="submitMessage"
        />

        <div class="chat-pane__actions">
          <span class="chat-pane__hint">Enter 发送，Shift + Enter 换行</span>

          <div class="chat-pane__action-buttons">
            <button
              class="ghost-button"
              type="button"
              :disabled="props.status !== 'streaming'"
              @click="emit('stop')"
            >
              停止生成
            </button>
            <button
              class="accent-button"
              type="button"
              :disabled="!draft.trim() || composerDisabled"
              @click="submitMessage"
            >
              发送
            </button>
          </div>
        </div>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.chat-pane {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 0;
  border: 1px solid var(--border-strong);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(249, 248, 244, 0.98));
  box-shadow: var(--shadow-panel);
  overflow: hidden;
}

.chat-pane--empty {
  grid-template-rows: auto 1fr;
}

.chat-pane__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--border);
}

.chat-pane__header-copy {
  display: grid;
  gap: 4px;
}

.chat-pane__header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-pane__eyebrow {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.82rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.chat-pane__title {
  margin: 0;
  font-size: 1.16rem;
}

.chat-pane__welcome {
  display: grid;
  align-items: center;
  min-height: 0;
  padding: 32px 28px 36px;
}

.chat-pane__transcript {
  min-height: 0;
  overflow: auto;
  padding: 22px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
}

.chat-message {
  display: flex;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message__card {
  max-width: min(78ch, 100%);
  padding: 12px 14px;
  border-radius: 18px;
}

.chat-message--user .chat-message__card {
  background: linear-gradient(180deg, #1f2937, #111827);
  color: white;
}

.chat-message--assistant .chat-message__card {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.82);
}

.chat-message__plain {
  white-space: pre-wrap;
  line-height: 1.65;
}

.chat-message__think {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--border);
}

.chat-message__think summary {
  cursor: pointer;
  color: var(--text-muted);
  font-size: 0.86rem;
}

.chat-pane__prompts {
  display: grid;
  gap: 14px;
}

.chat-pane__composer {
  padding: 16px 22px 22px;
  border-top: 1px solid var(--border);
}

.chat-pane__textarea {
  width: 100%;
  min-height: 96px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--text-strong);
  font: inherit;
  line-height: 1.65;
  resize: vertical;
  box-sizing: border-box;
}

.chat-pane__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.chat-pane__hint {
  color: var(--text-muted);
  font-size: 0.84rem;
}

.chat-pane__action-buttons {
  display: flex;
  gap: 10px;
}

@media (max-width: 720px) {
  .chat-pane__header,
  .chat-pane__welcome,
  .chat-pane__transcript,
  .chat-pane__composer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .chat-pane__header,
  .chat-pane__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .chat-pane__header-actions,
  .chat-pane__action-buttons {
    width: 100%;
  }

  .chat-pane__action-buttons {
    display: grid;
  }

  .chat-pane__hint {
    text-align: center;
  }
}
</style>

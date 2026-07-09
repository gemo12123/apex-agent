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
const composerButtonLabel = computed(() => {
  if (props.status === 'streaming') {
    return '停止'
  }

  return props.hasStarted ? '发送' : '开始'
})
const composerButtonDisabled = computed(
  () => props.status !== 'streaming' && (!draft.value.trim() || composerDisabled.value),
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

function applySuggestion(value: string): void {
  draft.value = value
}

function submitMessage(): void {
  const value = draft.value.trim()
  if (!value || composerDisabled.value) {
    return
  }

  emit('send', value)
  draft.value = ''
}

function submitComposerAction(): void {
  if (props.status === 'streaming') {
    emit('stop')
    return
  }

  submitMessage()
}
</script>

<template>
  <section class="chat-pane" :class="{ 'chat-pane--empty': !props.hasStarted }">
    <div class="chat-pane__shell">
      <header class="chat-pane__header">
        <div class="chat-pane__header-copy">
          <p class="chat-pane__eyebrow">{{ props.hasStarted ? '当前会话' : '新的会话' }}</p>
          <h2 class="chat-pane__title">
            {{ props.hasStarted ? '继续推进当前任务' : '准备开始一段新的对话' }}
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
        <WelcomeScreen @fill-draft="applySuggestion" />
      </div>

      <div v-else ref="transcriptRef" class="chat-pane__transcript">
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
                <summary>推理过程</summary>
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
        <div class="chat-pane__composer-shell chat-pane__composer-shell--prompt-bar">
          <label class="sr-only" for="chat-pane-draft">继续输入你的任务</label>
          <textarea
            id="chat-pane-draft"
            v-model="draft"
            class="chat-pane__textarea"
            rows="1"
            :disabled="composerDisabled"
            :placeholder="props.hasStarted ? '继续补充上下文、追问上一轮结果，或者给 Apex 一个新的执行方向。' : '有问题，尽管问'"
            @keydown.enter.exact.prevent="submitComposerAction"
          />

          <div class="chat-pane__prompt-actions">
            <button
              data-testid="send-button"
              class="chat-pane__prompt-submit"
              type="button"
              :disabled="composerButtonDisabled"
              :aria-label="composerButtonLabel"
              @click="submitComposerAction"
            >
              {{ composerButtonLabel }}
            </button>
          </div>
        </div>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.chat-pane {
  display: flex;
  flex: 1;
  width: 100%;
  min-height: 0;
  background: transparent;
}

.chat-pane__shell {
  width: 100%;
  max-width: 920px;
  min-height: 100%;
  margin: 0 auto;
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 0 8px;
}

.chat-pane--empty .chat-pane__shell {
  justify-content: center;
  gap: 20px;
}

.chat-pane--empty .chat-pane__header {
  display: none;
}

.chat-pane__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 6px 0 14px;
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
  font-size: 0.78rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.chat-pane__title {
  margin: 0;
  font-size: 1.04rem;
  line-height: 1.35;
}

.chat-pane__welcome {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px 0 10px;
}

.chat-pane--empty .chat-pane__welcome {
  display: contents;
  flex: initial;
  padding: 0;
}

.chat-pane--empty .chat-pane__welcome :deep(.welcome-screen) {
  display: contents;
}

.chat-pane--empty .chat-pane__welcome :deep(.welcome-screen__copy) {
  order: 1;
  width: 100%;
}

.chat-pane--empty .chat-pane__welcome :deep(.welcome-screen__suggestions) {
  order: 3;
  width: 100%;
  margin-top: 4px;
}

.chat-pane__transcript {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 0 18px;
  display: grid;
  gap: 20px;
  align-content: start;
}

.chat-message {
  display: flex;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message__card {
  max-width: min(80ch, 100%);
}

.chat-message--assistant .chat-message__card {
  padding: 0;
  border: none;
  background: transparent;
}

.chat-message--user .chat-message__card {
  max-width: min(68ch, 100%);
  padding: 10px 14px;
  border-radius: 18px;
  background: #f1f2f6;
  color: var(--text-strong);
}

.chat-message__plain {
  white-space: pre-wrap;
  line-height: 1.65;
}

.chat-message__think {
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-subtle);
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
  position: sticky;
  bottom: 20px;
  margin-top: auto;
  padding: 18px 0 8px;
  background: linear-gradient(
    180deg,
    rgba(252, 252, 253, 0) 0%,
    rgba(252, 252, 253, 0.9) 38%,
    #fcfcfd 100%
  );
}

.chat-pane--empty .chat-pane__composer {
  order: 2;
  position: static;
  margin-top: 0;
  padding: 0;
  background: transparent;
}

.chat-pane__composer-shell {
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 40px -30px rgba(15, 23, 42, 0.24);
  padding: 12px 14px 14px;
}

.chat-pane__composer-shell--prompt-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  width: min(100%, 768px);
  min-height: 60px;
  margin: 0 auto;
  padding: 0 8px 0 20px;
  border-color: rgba(15, 23, 42, 0.1);
  border-radius: 999px;
  box-shadow: 0 18px 34px -26px rgba(15, 23, 42, 0.28);
}

.chat-pane__textarea {
  width: 100%;
  min-height: 72px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-strong);
  font: inherit;
  line-height: 1.6;
  resize: none;
  box-sizing: border-box;
}

.chat-pane__composer-shell--prompt-bar .chat-pane__textarea {
  min-height: 24px;
  height: 24px;
  line-height: 1.5;
  overflow: hidden;
}

.chat-pane__composer-shell--prompt-bar .chat-pane__textarea::placeholder {
  color: #8a8f98;
}

.chat-pane__textarea:focus {
  outline: none;
}

.chat-pane__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.chat-pane__action-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-pane__hint {
  color: var(--text-muted);
  font-size: 0.84rem;
}

.chat-pane__prompt-actions {
  display: inline-flex;
  align-items: center;
  flex: none;
}

.chat-pane__prompt-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border: none;
}

.chat-pane__prompt-submit {
  min-width: 58px;
  height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  background: #050505;
  color: #ffffff;
  font-size: 0.92rem;
  font-weight: 600;
}

.chat-pane__prompt-submit:hover:enabled {
  background: #171717;
}

.chat-pane__prompt-submit:disabled {
  opacity: 1;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .chat-pane__header,
  .chat-pane__actions,
  .chat-pane__action-group {
    flex-direction: column;
    align-items: stretch;
  }

  .chat-pane__header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .chat-pane__hint {
    text-align: center;
  }

  .chat-pane__composer-shell--prompt-bar {
    width: 100%;
    gap: 10px;
    padding-left: 16px;
  }
}
</style>

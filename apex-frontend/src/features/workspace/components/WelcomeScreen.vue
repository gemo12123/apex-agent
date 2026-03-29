<script setup lang="ts">
import { ref } from 'vue'
import type { AgentSummary } from '@/types/apex'

defineProps<{
  agents: AgentSummary[]
  selectedAgentKey: string
  userId: string
  loading: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  (event: 'submit', value: string): void
  (event: 'update:selectedAgentKey', value: string): void
  (event: 'update:userId', value: string): void
}>()

const prompt = ref('')

const suggestions = [
  '概括 docs 中 Apex 的执行流程，并指出关键 SSE 事件。',
  '说明 plan-executor 和 react 两种模式的差异。',
  '根据当前工程结构给出一个前端改造建议。',
]

function submitPrompt(): void {
  const value = prompt.value.trim()
  if (!value) {
    return
  }

  emit('submit', value)
  prompt.value = ''
}

function useSuggestion(value: string): void {
  prompt.value = value
}
</script>

<template>
  <section class="welcome-screen">
    <div class="welcome-screen__frame">
      <div class="welcome-screen__intro">
        <div class="welcome-screen__eyebrow">Apex 工作台</div>
        <h1 class="welcome-screen__title">把任务、执行过程和产物放到同一个页面里</h1>
        <p class="welcome-screen__subtitle">
          在一个页面里发起任务、跟踪计划、查看产物，并处理需要人工确认的步骤。
        </p>

        <div class="welcome-screen__highlights">
          <span>流式输出</span>
          <span>计划追踪</span>
          <span>人工确认</span>
        </div>
      </div>

      <div class="welcome-screen__editor">
        <label class="sr-only" for="welcome-prompt">任务输入</label>
        <textarea
          id="welcome-prompt"
          v-model="prompt"
          class="welcome-screen__textarea"
          placeholder="例如：总结 docs 中的执行协议，并说明 plan-executor 与 react 模式的区别。"
          rows="6"
          @keydown.enter.exact.prevent="submitPrompt"
        />

        <div class="welcome-screen__controls">
          <label class="welcome-screen__field">
            <span>代理</span>
            <select
              :value="selectedAgentKey"
              class="welcome-screen__select"
              @change="emit('update:selectedAgentKey', ($event.target as HTMLSelectElement).value)"
            >
              <option v-for="agent in agents" :key="agent.agentKey" :value="agent.agentKey">
                {{ agent.name }}
              </option>
            </select>
          </label>

          <label class="welcome-screen__field">
            <span>用户 ID</span>
            <input
              :value="userId"
              class="welcome-screen__input"
              type="text"
              placeholder="demo-user"
              @input="emit('update:userId', ($event.target as HTMLInputElement).value)"
            />
          </label>

          <button
            class="welcome-screen__submit accent-button"
            type="button"
            :disabled="loading || !prompt.trim()"
            @click="submitPrompt"
          >
            {{ loading ? '加载中...' : '进入工作台' }}
          </button>
        </div>
      </div>

      <p v-if="errorMessage" class="welcome-screen__error">{{ errorMessage }}</p>

      <div class="welcome-screen__suggestions">
        <button
          v-for="suggestion in suggestions"
          :key="suggestion"
          class="welcome-screen__suggestion"
          type="button"
          @click="useSuggestion(suggestion)"
        >
          {{ suggestion }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.welcome-screen {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 18px;
}

.welcome-screen__frame {
  width: min(980px, 100%);
  padding: 32px;
  border: 1px solid var(--border-strong);
  border-radius: 24px;
  background: var(--surface);
  box-shadow: var(--shadow-panel);
}

.welcome-screen__intro {
  display: grid;
  gap: 14px;
}

.welcome-screen__eyebrow {
  width: fit-content;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--surface-muted);
  color: var(--text-muted);
  font-size: 0.86rem;
  font-weight: 600;
}

.welcome-screen__title {
  max-width: 16ch;
  font-size: clamp(2rem, 5vw, 3.4rem);
  line-height: 1.12;
  letter-spacing: -0.04em;
}

.welcome-screen__subtitle {
  max-width: 58ch;
  color: var(--text-soft);
  line-height: 1.75;
}

.welcome-screen__highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.welcome-screen__highlights span,
.welcome-screen__suggestion {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface-subtle);
  color: var(--text-soft);
}

.welcome-screen__editor {
  margin-top: 28px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface-subtle);
}

.welcome-screen__textarea,
.welcome-screen__select,
.welcome-screen__input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  color: var(--text-strong);
  font: inherit;
}

.welcome-screen__textarea {
  min-height: 164px;
  padding: 16px;
  resize: vertical;
}

.welcome-screen__controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.9fr) auto;
  gap: 12px;
  margin-top: 14px;
}

.welcome-screen__field {
  display: grid;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.welcome-screen__select,
.welcome-screen__input,
.welcome-screen__submit {
  min-height: 46px;
}

.welcome-screen__select,
.welcome-screen__input {
  padding: 0 14px;
}

.welcome-screen__submit {
  align-self: end;
  padding: 0 18px;
}

.welcome-screen__error {
  margin-top: 14px;
  color: var(--danger);
}

.welcome-screen__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.welcome-screen__suggestion {
  text-align: left;
}

@media (max-width: 900px) {
  .welcome-screen__frame {
    padding: 22px;
  }

  .welcome-screen__controls {
    grid-template-columns: 1fr;
  }
}
</style>

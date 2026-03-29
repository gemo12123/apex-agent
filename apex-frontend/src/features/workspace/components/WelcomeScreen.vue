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
  '总结这个项目的执行架构并指出关键消息类型。',
  '设计一个新的 AI 工作台首页视觉方案。',
  '说明 plan-executor 和 react 模式的区别。',
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
      <div class="welcome-screen__eyebrow">Apex Frontend V1</div>
      <h1 class="welcome-screen__title">
        Bring the agent runtime into
        <span>one deliberate workspace.</span>
      </h1>
      <p class="welcome-screen__subtitle">
        Stream SSE responses, inspect execution plans, review artifacts, and resume human-in-the-loop
        tasks from a single technical console.
      </p>

      <div class="welcome-screen__editor">
        <label class="sr-only" for="welcome-prompt">Task prompt</label>
        <textarea
          id="welcome-prompt"
          v-model="prompt"
          class="welcome-screen__textarea"
          placeholder="Ask Apex to analyze the runtime, execute a task, or explain a stage..."
          rows="6"
          @keydown.enter.exact.prevent="submitPrompt"
        />

        <div class="welcome-screen__controls">
          <label class="welcome-screen__field">
            <span>Agent</span>
            <select
              class="welcome-screen__select"
              @change="emit('update:selectedAgentKey', ($event.target as HTMLSelectElement).value)"
            >
              <option
                v-for="agent in agents"
                :key="agent.agentKey"
                :value="agent.agentKey"
                :selected="agent.agentKey === selectedAgentKey"
              >
                {{ agent.name }}
              </option>
            </select>
          </label>

          <label class="welcome-screen__field">
            <span>User ID</span>
            <input
              :value="userId"
              class="welcome-screen__input"
              type="text"
              placeholder="demo-user"
              @input="emit('update:userId', ($event.target as HTMLInputElement).value)"
            />
          </label>

          <button
            class="welcome-screen__submit"
            type="button"
            :disabled="loading || !prompt.trim()"
            @click="submitPrompt"
          >
            Launch Workspace
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
  padding: 40px 20px;
}

.welcome-screen__frame {
  width: min(1100px, 100%);
  padding: 48px;
  border: 1px solid var(--border-strong);
  border-radius: 32px;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.16), transparent 32%),
    radial-gradient(circle at bottom right, rgba(14, 165, 233, 0.12), transparent 28%),
    var(--surface);
  box-shadow: var(--shadow-panel);
}

.welcome-screen__eyebrow {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--border-strong);
  background: rgba(248, 250, 252, 0.9);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.welcome-screen__title {
  max-width: 12ch;
  margin: 22px 0 0;
  font-size: clamp(3rem, 8vw, 5.8rem);
  line-height: 0.96;
  letter-spacing: -0.06em;
}

.welcome-screen__title span {
  display: block;
  color: var(--accent-strong);
}

.welcome-screen__subtitle {
  max-width: 66ch;
  margin-top: 22px;
  color: var(--text-soft);
  font-size: 1.02rem;
  line-height: 1.75;
}

.welcome-screen__editor {
  margin-top: 34px;
  padding: 20px;
  border: 1px solid var(--border-strong);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.84);
}

.welcome-screen__textarea,
.welcome-screen__select,
.welcome-screen__input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface-2);
  color: var(--text-strong);
  font: inherit;
}

.welcome-screen__textarea {
  min-height: 170px;
  padding: 20px;
  resize: vertical;
}

.welcome-screen__textarea:focus,
.welcome-screen__select:focus,
.welcome-screen__input:focus {
  outline: 2px solid var(--accent-soft);
  outline-offset: 2px;
}

.welcome-screen__controls {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr) auto;
  gap: 14px;
  margin-top: 18px;
}

.welcome-screen__field {
  display: grid;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.88rem;
}

.welcome-screen__select,
.welcome-screen__input {
  min-height: 48px;
  padding: 0 14px;
}

.welcome-screen__submit {
  align-self: end;
  min-height: 48px;
  padding: 0 20px;
  border: none;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--accent-strong), var(--accent));
  color: white;
  font-family: var(--font-mono);
  font-size: 0.88rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  transition: transform 180ms ease, box-shadow 180ms ease, opacity 180ms ease;
}

.welcome-screen__submit:hover:enabled {
  transform: translateY(-1px);
  box-shadow: 0 20px 30px -22px rgba(37, 99, 235, 0.9);
}

.welcome-screen__submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.welcome-screen__error {
  margin-top: 16px;
  color: var(--danger);
}

.welcome-screen__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.welcome-screen__suggestion {
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--text-soft);
  cursor: pointer;
  transition: border-color 180ms ease, transform 180ms ease, color 180ms ease;
}

.welcome-screen__suggestion:hover {
  border-color: var(--accent-soft);
  color: var(--accent-strong);
  transform: translateY(-1px);
}

@media (max-width: 960px) {
  .welcome-screen__frame {
    padding: 28px;
    border-radius: 24px;
  }

  .welcome-screen__controls {
    grid-template-columns: 1fr;
  }
}
</style>

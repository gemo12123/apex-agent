<script setup lang="ts">
import { ref } from 'vue'

defineOptions({
  inheritAttrs: false,
})

const emit = defineEmits<{
  (event: 'submit', value: string): void
}>()

const prompt = ref('')

const suggestions = [
  '总结当前仓库里的前端结构，并指出最适合先改的入口文件。',
  '阅读 session store 和 SSE reducer，解释消息流是如何驱动界面的。',
  '给这个工作台补一版更接近 ChatGPT 的交互和视觉方案。',
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
    <div class="welcome-screen__copy">
      <p class="welcome-screen__eyebrow">Apex Workspace</p>
      <h1 class="welcome-screen__title">今天想让 Apex 做什么？</h1>
      <p class="welcome-screen__subtitle">
        从左侧新建会话、选择 Agent，然后在这里发起任务、补充上下文，或者继续把一个复杂问题拆解到底。
      </p>
    </div>

    <div class="welcome-screen__suggestions">
      <button
        v-for="(suggestion, index) in suggestions"
        :key="suggestion"
        :data-testid="`welcome-suggestion-${index}`"
        class="welcome-screen__suggestion"
        type="button"
        @click="useSuggestion(suggestion)"
      >
        {{ suggestion }}
      </button>
    </div>

    <div class="welcome-screen__composer">
      <label class="sr-only" for="welcome-prompt">输入你的任务</label>
      <textarea
        id="welcome-prompt"
        v-model="prompt"
        class="welcome-screen__textarea"
        placeholder="例如：检查当前工作台实现，给出一版接近 ChatGPT 首页的布局改造方案。"
        rows="4"
        @keydown.enter.exact.prevent="submitPrompt"
      />

      <div class="welcome-screen__actions">
        <span class="welcome-screen__hint">Enter 发送，Shift + Enter 换行</span>
        <button
          class="welcome-screen__submit accent-button"
          type="button"
          :disabled="!prompt.trim()"
          @click="submitPrompt"
        >
          开始对话
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.welcome-screen {
  display: grid;
  gap: 20px;
  width: min(100%, 760px);
  margin: 0 auto;
}

.welcome-screen__copy {
  display: grid;
  gap: 10px;
  text-align: center;
}

.welcome-screen__eyebrow {
  margin: 0 auto;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.welcome-screen__title {
  margin: 0;
  font-size: clamp(2rem, 4vw, 3.2rem);
  line-height: 1.04;
  letter-spacing: -0.045em;
}

.welcome-screen__subtitle {
  max-width: 58ch;
  margin: 0 auto;
  color: var(--text-soft);
  line-height: 1.7;
}

.welcome-screen__suggestions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.welcome-screen__suggestion {
  min-height: 46px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
  background: var(--surface-subtle);
  color: var(--text-soft);
  text-align: left;
  transition:
    border-color 0.18s ease,
    transform 0.18s ease,
    background 0.18s ease;
}

.welcome-screen__suggestion:hover {
  border-color: var(--border-strong);
  background: var(--surface);
  transform: translateY(-1px);
}

.welcome-screen__composer {
  padding: 14px 16px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-panel);
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}

.welcome-screen__textarea {
  width: 100%;
  min-height: 112px;
  padding: 10px 0 12px;
  border: none;
  background: transparent;
  color: var(--text-strong);
  font: inherit;
  line-height: 1.6;
  resize: none;
  box-sizing: border-box;
}

.welcome-screen__textarea:focus {
  outline: none;
}

.welcome-screen__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.welcome-screen__hint {
  color: var(--text-muted);
  font-size: 0.84rem;
}

.welcome-screen__submit {
  white-space: nowrap;
}

@media (max-width: 720px) {
  .welcome-screen__suggestions {
    grid-template-columns: 1fr;
  }

  .welcome-screen__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .welcome-screen__hint {
    text-align: center;
  }

  .welcome-screen__submit {
    width: 100%;
  }
}
</style>

<script setup lang="ts">
defineOptions({
  inheritAttrs: false,
})

const emit = defineEmits<{
  (event: 'fill-draft', value: string): void
}>()

const suggestions = [
  '总结当前仓库里的前端结构，并指出最适合先改的入口文件。',
  '阅读 session store 和 SSE reducer，解释消息流是如何驱动界面的。',
  '给这套工作台整理一版更接近 ChatGPT 首页的中间主列方案。',
]
</script>

<template>
  <section class="welcome-screen">
    <div class="welcome-screen__copy">
      <p class="welcome-screen__eyebrow">Apex Workspace</p>
      <h1 class="welcome-screen__title">今天想让 Apex 做什么？</h1>
      <p class="welcome-screen__subtitle">
        在同一条工作主列里发起任务、补充上下文，或继续推进上一轮执行结果。
      </p>
    </div>

    <div class="welcome-screen__suggestions">
      <button
        v-for="(suggestion, index) in suggestions"
        :key="suggestion"
        :data-testid="`welcome-suggestion-${index}`"
        class="welcome-screen__suggestion"
        type="button"
        @click="emit('fill-draft', suggestion)"
      >
        {{ suggestion }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.welcome-screen {
  display: grid;
  gap: 18px;
  width: 100%;
}

.welcome-screen__copy {
  display: grid;
  gap: 10px;
  text-align: center;
}

.welcome-screen__eyebrow {
  margin: 0 auto;
  color: var(--text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.welcome-screen__title {
  margin: 0;
  font-size: clamp(2rem, 4vw, 2.8rem);
  line-height: 1.05;
  letter-spacing: -0.04em;
}

.welcome-screen__subtitle {
  max-width: 56ch;
  margin: 0 auto;
  color: var(--text-soft);
  line-height: 1.7;
}

.welcome-screen__suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.welcome-screen__suggestion {
  min-height: 40px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-soft);
  text-align: left;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;
}

.welcome-screen__suggestion:hover {
  border-color: var(--border-strong);
  background: var(--surface-subtle);
  transform: translateY(-1px);
}

@media (max-width: 720px) {
  .welcome-screen__suggestions {
    justify-content: stretch;
  }

  .welcome-screen__suggestion {
    width: 100%;
  }
}
</style>

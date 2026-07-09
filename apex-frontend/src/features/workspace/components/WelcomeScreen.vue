<script setup lang="ts">
defineOptions({
  inheritAttrs: false,
})

const emit = defineEmits<{
  (event: 'fill-draft', value: string): void
}>()

const suggestions = [
  {
    label: '生成图片',
    iconPath:
      'M5 5.5A2.5 2.5 0 0 1 7.5 3h9A2.5 2.5 0 0 1 19 5.5v9a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 14.5v-9Zm3.25 8.5h7.5l-2.45-3.1-1.95 2.35-1.3-1.55L8.25 14Zm7-6.6a1.35 1.35 0 1 0 0-2.7 1.35 1.35 0 0 0 0 2.7Z',
  },
  {
    label: '撰写或编辑',
    iconPath:
      'M5 15.25V18h2.75L16.1 9.65 13.35 6.9 5 15.25Zm12.75-7.25a1.1 1.1 0 0 0 0-1.56l-1.19-1.19a1.1 1.1 0 0 0-1.56 0l-.78.78 2.75 2.75.78-.78Z',
  },
  {
    label: '查找资料',
    iconPath:
      'M10 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16Zm0-1.4c.75-.82 1.35-2 1.63-3.35H8.37c.28 1.35.88 2.53 1.63 3.35Zm-1.88-4.75h3.76c.08-.58.12-1.2.12-1.85s-.04-1.27-.12-1.85H8.12C8.04 8.73 8 9.35 8 10s.04 1.27.12 1.85Zm-4.6 0h3.18A14 14 0 0 1 6.6 10c0-.63.04-1.25.11-1.85H3.52A6.67 6.67 0 0 0 3.2 10c0 .64.11 1.26.32 1.85Zm9.78 0h3.18c.21-.59.32-1.21.32-1.85 0-.64-.11-1.26-.32-1.85H13.3c.07.6.1 1.22.1 1.85s-.03 1.25-.1 1.85Zm-.06-5.1h2.56a6.65 6.65 0 0 0-3.53-2.94c.42.8.75 1.8.97 2.94Zm-4.87 0h3.26C11.35 5.4 10.75 4.22 10 3.4c-.75.82-1.35 2-1.63 3.35Zm-4.17 0h2.56c.22-1.14.55-2.14.97-2.94A6.65 6.65 0 0 0 4.2 6.75Zm8.07 9.44a6.65 6.65 0 0 0 3.53-2.94h-2.56a10.5 10.5 0 0 1-.97 2.94Zm-4.54 0a10.5 10.5 0 0 1-.97-2.94H4.2a6.65 6.65 0 0 0 3.53 2.94Z',
  },
]
</script>

<template>
  <section class="welcome-screen">
    <div class="welcome-screen__copy">
      <h1 class="welcome-screen__title">我们先从哪里开始呢？</h1>
    </div>

    <div class="welcome-screen__suggestions welcome-screen__suggestions--single-row">
      <button
        v-for="(suggestion, index) in suggestions"
        :key="suggestion.label"
        :data-testid="`welcome-suggestion-${index}`"
        class="welcome-screen__suggestion"
        type="button"
        @click="emit('fill-draft', suggestion.label)"
      >
        <svg
          class="welcome-screen__suggestion-icon"
          viewBox="0 0 20 20"
          aria-hidden="true"
          focusable="false"
        >
          <path :d="suggestion.iconPath" />
        </svg>
        <span>{{ suggestion.label }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.welcome-screen {
  display: grid;
  gap: 22px;
  width: 100%;
}

.welcome-screen__copy {
  display: grid;
  gap: 10px;
  text-align: center;
}

.welcome-screen__title {
  margin: 0;
  color: #050505;
  font-size: clamp(1.45rem, 3vw, 1.82rem);
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: 0;
}

.welcome-screen__suggestions {
  display: flex;
  flex-wrap: nowrap;
  justify-content: center;
  gap: 12px;
}

.welcome-screen__suggestion {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 15px;
  border: 1px solid rgba(15, 23, 42, 0.11);
  border-radius: 999px;
  background: #ffffff;
  color: #6b7280;
  font-size: 0.92rem;
  font-weight: 500;
  text-align: center;
  white-space: nowrap;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;
}

.welcome-screen__suggestion:hover {
  border-color: rgba(15, 23, 42, 0.18);
  background: #fbfbfc;
  color: #374151;
}

.welcome-screen__suggestion-icon {
  width: 17px;
  height: 17px;
  flex: none;
  fill: currentColor;
}

@media (max-width: 720px) {
  .welcome-screen__suggestions {
    gap: 8px;
  }
}
</style>

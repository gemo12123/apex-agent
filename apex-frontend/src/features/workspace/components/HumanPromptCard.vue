<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatPromptInputType } from '@/features/workspace/presentation'
import type { HumanPromptRecord } from '@/types/apex'

const props = defineProps<{
  prompt: HumanPromptRecord
}>()

const emit = defineEmits<{
  (event: 'submit', value: string | string[]): void
}>()

const singleValue = ref('')
const customValue = ref('')
const multiValues = ref<string[]>([])

const canSubmit = computed(() => {
  if (props.prompt.inputType === 'MULTI_SELECT') {
    return multiValues.value.length > 0 || customValue.value.trim().length > 0
  }

  if (props.prompt.inputType === 'CONFIRM') {
    return true
  }

  return singleValue.value.length > 0 || customValue.value.trim().length > 0
})

function chooseSingle(value: string): void {
  singleValue.value = value
  customValue.value = ''
}

function toggleMulti(value: string): void {
  if (multiValues.value.includes(value)) {
    multiValues.value = multiValues.value.filter((item) => item !== value)
    return
  }

  multiValues.value = [...multiValues.value, value]
}

function submit(): void {
  if (props.prompt.inputType === 'MULTI_SELECT') {
    const values = [...multiValues.value]
    if (customValue.value.trim()) {
      values.push(customValue.value.trim())
    }
    emit('submit', values)
    return
  }

  emit('submit', customValue.value.trim() || singleValue.value || '确认')
}
</script>

<template>
  <article class="human-prompt-card">
    <header class="human-prompt-card__header">
      <p class="human-prompt-card__eyebrow">{{ formatPromptInputType(prompt.inputType) }}</p>
      <h3 class="human-prompt-card__title">{{ prompt.question }}</h3>
      <p v-if="prompt.description" class="human-prompt-card__description">{{ prompt.description }}</p>
    </header>

    <div class="human-prompt-card__body">
      <template v-if="prompt.inputType === 'CONFIRM'">
        <div class="human-prompt-card__actions">
          <button class="ghost-button" type="button" @click="emit('submit', '取消')">取消</button>
          <button class="accent-button" type="button" @click="emit('submit', '确认')">确认</button>
        </div>
      </template>

      <template v-else>
        <div v-if="prompt.options.length" class="human-prompt-card__options">
          <button
            v-for="option in prompt.options"
            :key="option.label"
            class="option-chip"
            :class="{
              'option-chip--active':
                prompt.inputType === 'MULTI_SELECT'
                  ? multiValues.includes(option.label)
                  : singleValue === option.label,
            }"
            type="button"
            @click="
              prompt.inputType === 'MULTI_SELECT'
                ? toggleMulti(option.label)
                : chooseSingle(option.label)
            "
          >
            <strong>{{ option.label }}</strong>
            <span v-if="option.description">{{ option.description }}</span>
          </button>
        </div>

        <label class="sr-only" :for="prompt.id">输入回答</label>
        <textarea
          :id="prompt.id"
          v-model="customValue"
          class="human-prompt-card__textarea"
          :placeholder="
            prompt.inputType === 'MULTI_SELECT'
              ? '可补充一个自定义选项...'
              : '如果需要，也可以直接输入补充说明。'
          "
          rows="3"
        />

        <div class="human-prompt-card__actions">
          <button class="accent-button" type="button" :disabled="!canSubmit" @click="submit">提交</button>
        </div>
      </template>
    </div>
  </article>
</template>

<style scoped>
.human-prompt-card {
  border: 1px solid var(--border-strong);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}

.human-prompt-card__header {
  padding: 16px 16px 0;
}

.human-prompt-card__eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 0.84rem;
  font-weight: 600;
}

.human-prompt-card__title {
  font-size: 1rem;
}

.human-prompt-card__description {
  margin: 8px 0 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.human-prompt-card__body {
  padding: 14px 16px 16px;
}

.human-prompt-card__options {
  display: grid;
  gap: 10px;
}

.option-chip {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-subtle);
  color: var(--text-strong);
  text-align: left;
}

.option-chip:hover,
.option-chip--active {
  border-color: var(--accent);
  background: var(--accent-subtle);
}

.option-chip span {
  color: var(--text-soft);
  font-size: 0.92rem;
}

.human-prompt-card__textarea {
  width: 100%;
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-subtle);
  color: var(--text-strong);
  resize: vertical;
  font: inherit;
  box-sizing: border-box;
}

.human-prompt-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}
</style>

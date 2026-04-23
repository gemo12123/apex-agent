<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  formatConfirmationRiskLevel,
  formatEditableFieldType,
} from '@/features/workspace/presentation'
import type {
  ToolConfirmationDisplayField,
  ToolConfirmationEditableField,
  ToolConfirmationRecord,
} from '@/types/apex'

const props = defineProps<{
  confirmation: ToolConfirmationRecord
}>()

const emit = defineEmits<{
  (
    event: 'submit',
    payload: { decision: 'APPROVE' | 'DENY'; updatedArgs?: Record<string, unknown> },
  ): void
}>()

const editing = ref(false)
const formState = reactive<Record<string, any>>(
  Object.fromEntries(props.confirmation.editableFields.map((field) => [field.key, field.value ?? ''])),
)

const hasEditableFields = computed(
  () => props.confirmation.editable && props.confirmation.editableFields.length > 0,
)

const canApprove = computed(() =>
  props.confirmation.editableFields.every((field) => isFieldSatisfied(field, formState[field.key])),
)

function toggleEditing(): void {
  editing.value = !editing.value
}

function approve(): void {
  emit('submit', {
    decision: 'APPROVE',
    updatedArgs: editing.value ? { ...formState } : {},
  })
}

function deny(): void {
  emit('submit', { decision: 'DENY' })
}

function isFieldSatisfied(
  field: ToolConfirmationEditableField,
  value: string | number | boolean | null | undefined,
): boolean {
  if (!field.required) {
    return true
  }

  if (field.input_type === 'confirm') {
    return value === true
  }

  return String(value ?? '').trim().length > 0
}

function formatDisplayValue(field: ToolConfirmationDisplayField): string {
  if (field.value === null || field.value === undefined || field.value === '') {
    return '未设置'
  }
  return String(field.value)
}
</script>

<template>
  <article class="tool-confirmation-card">
    <header class="tool-confirmation-card__header">
      <div class="tool-confirmation-card__heading">
        <p class="tool-confirmation-card__eyebrow">{{ confirmation.toolDisplayName }}</p>
        <h3 class="tool-confirmation-card__title">{{ confirmation.title }}</h3>
        <p v-if="confirmation.description" class="tool-confirmation-card__description">
          {{ confirmation.description }}
        </p>
      </div>

      <span class="tool-confirmation-card__risk">
        {{ formatConfirmationRiskLevel(confirmation.riskLevel) }}
      </span>
    </header>

    <dl v-if="confirmation.displayFields.length" class="tool-confirmation-card__summary">
      <div
        v-for="field in confirmation.displayFields"
        :key="field.key"
        class="tool-confirmation-card__summary-item"
      >
        <dt>{{ field.label }}</dt>
        <dd>{{ formatDisplayValue(field) }}</dd>
      </div>
    </dl>

    <button
      v-if="hasEditableFields"
      data-testid="edit-button"
      class="ghost-button tool-confirmation-card__edit-button"
      type="button"
      @click="toggleEditing"
    >
      {{ editing ? '收起编辑' : '编辑参数' }}
    </button>

    <div v-if="editing" class="tool-confirmation-card__form">
      <label
        v-for="field in confirmation.editableFields"
        :key="field.key"
        class="tool-confirmation-card__field"
      >
        <div class="tool-confirmation-card__field-header">
          <span>{{ field.label }}</span>
          <small>{{ formatEditableFieldType(field.input_type) }}</small>
        </div>

        <select
          v-if="field.input_type === 'single-select'"
          v-model="formState[field.key]"
          class="tool-confirmation-card__control"
        >
          <option
            v-for="option in field.options ?? []"
            :key="option.label"
            :value="option.label"
          >
            {{ option.label }}
          </option>
        </select>

        <textarea
          v-else-if="field.input_type === 'textarea'"
          v-model="formState[field.key]"
          class="tool-confirmation-card__control tool-confirmation-card__textarea"
          rows="3"
        />

        <input
          v-else-if="field.input_type === 'date'"
          v-model="formState[field.key]"
          class="tool-confirmation-card__control"
          type="date"
        />

        <input
          v-else-if="field.input_type === 'datetime'"
          v-model="formState[field.key]"
          class="tool-confirmation-card__control"
          type="datetime-local"
        />

        <label
          v-else-if="field.input_type === 'confirm'"
          class="tool-confirmation-card__checkbox"
        >
          <input v-model="formState[field.key]" type="checkbox" />
          <span>已确认</span>
        </label>

        <input
          v-else
          v-model="formState[field.key]"
          class="tool-confirmation-card__control"
          type="text"
        />
      </label>
    </div>

    <footer class="tool-confirmation-card__actions">
      <button class="ghost-button" type="button" @click="deny">
        {{ confirmation.denyLabel }}
      </button>
      <button
        data-testid="approve-button"
        class="accent-button"
        type="button"
        :disabled="editing && !canApprove"
        @click="approve"
      >
        {{ confirmation.confirmLabel }}
      </button>
    </footer>
  </article>
</template>

<style scoped>
.tool-confirmation-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(192, 132, 16, 0.22);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 248, 235, 0.98), rgba(255, 252, 245, 0.98));
  box-shadow: var(--shadow-soft);
}

.tool-confirmation-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.tool-confirmation-card__heading {
  display: grid;
  gap: 6px;
}

.tool-confirmation-card__eyebrow {
  margin: 0;
  color: #b45309;
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.tool-confirmation-card__title {
  margin: 0;
  font-size: 1rem;
}

.tool-confirmation-card__description {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.tool-confirmation-card__risk {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.12);
  color: #92400e;
  font-size: 0.78rem;
  font-weight: 700;
  white-space: nowrap;
}

.tool-confirmation-card__summary {
  display: grid;
  gap: 10px;
  margin: 0;
}

.tool-confirmation-card__summary-item {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.tool-confirmation-card__summary-item dt {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.tool-confirmation-card__summary-item dd {
  margin: 0;
  color: var(--text-strong);
  font-weight: 600;
}

.tool-confirmation-card__edit-button {
  justify-self: flex-start;
}

.tool-confirmation-card__form {
  display: grid;
  gap: 12px;
}

.tool-confirmation-card__field {
  display: grid;
  gap: 8px;
}

.tool-confirmation-card__field-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-strong);
  font-weight: 600;
}

.tool-confirmation-card__field-header small {
  color: var(--text-muted);
  font-weight: 500;
}

.tool-confirmation-card__control {
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.84);
  color: var(--text-strong);
  font: inherit;
  box-sizing: border-box;
}

.tool-confirmation-card__textarea {
  min-height: 96px;
  padding: 12px;
  resize: vertical;
}

.tool-confirmation-card__checkbox {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text-strong);
}

.tool-confirmation-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 720px) {
  .tool-confirmation-card__header {
    flex-direction: column;
  }

  .tool-confirmation-card__actions {
    flex-direction: column-reverse;
  }
}
</style>

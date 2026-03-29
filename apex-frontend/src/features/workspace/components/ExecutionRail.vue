<script setup lang="ts">
import type { ArtifactRecord, StageRecord } from '@/types/apex'

defineProps<{
  stages: StageRecord[]
  globalArtifacts: ArtifactRecord[]
  selectedInvocationId: string | null
  selectedArtifactId: string | null
}>()

const emit = defineEmits<{
  (event: 'select-invocation', payload: { stageId: string; invocationId: string }): void
  (event: 'select-artifact', payload: { scope: 'STAGE' | 'GLOBAL'; artifactId: string; stageId?: string }): void
}>()

function badgeTone(status: string): string {
  const normalized = status.toUpperCase()
  if (normalized.includes('COMPLETE')) return 'success'
  if (normalized.includes('FAIL')) return 'danger'
  if (normalized.includes('PENDING')) return 'warning'
  return 'active'
}
</script>

<template>
  <section class="execution-rail">
    <header class="execution-rail__header">
      <p class="execution-rail__eyebrow">Execution</p>
      <h2 class="execution-rail__title">Plan & outputs</h2>
    </header>

    <div v-if="globalArtifacts.length" class="execution-rail__section">
      <div class="execution-rail__section-title">Global artifacts</div>
      <button
        v-for="artifact in globalArtifacts"
        :key="artifact.id"
        class="rail-button"
        :class="{ 'rail-button--active': selectedArtifactId === artifact.id }"
        type="button"
        @click="emit('select-artifact', { scope: 'GLOBAL', artifactId: artifact.id })"
      >
        {{ artifact.artifactName }}
      </button>
    </div>

    <div v-if="stages.length" class="execution-rail__stages">
      <article v-for="stage in stages" :key="stage.id" class="stage-card">
        <header class="stage-card__header">
          <div>
            <p class="stage-card__name">{{ stage.name }}</p>
            <p class="stage-card__description">{{ stage.description || 'No stage description yet.' }}</p>
          </div>
          <span class="status-pill" :class="`status-pill--${badgeTone(stage.status)}`">
            {{ stage.status }}
          </span>
        </header>

        <div v-if="stage.invocations.length" class="stage-card__group">
          <p class="stage-card__label">Invocations</p>
          <button
            v-for="invocation in stage.invocations"
            :key="invocation.id"
            class="rail-button"
            :class="{ 'rail-button--active': selectedInvocationId === invocation.id }"
            type="button"
            @click="emit('select-invocation', { stageId: stage.id, invocationId: invocation.id })"
          >
            <strong>{{ invocation.name }}</strong>
            <span>{{ invocation.invocationType }}</span>
          </button>
        </div>

        <div v-if="stage.artifacts.length" class="stage-card__group">
          <p class="stage-card__label">Artifacts</p>
          <button
            v-for="artifact in stage.artifacts"
            :key="artifact.id"
            class="rail-button"
            :class="{ 'rail-button--active': selectedArtifactId === artifact.id }"
            type="button"
            @click="emit('select-artifact', { scope: 'STAGE', artifactId: artifact.id, stageId: stage.id })"
          >
            <strong>{{ artifact.artifactName }}</strong>
            <span>{{ artifact.dataType }}</span>
          </button>
        </div>
      </article>
    </div>

    <p v-else class="execution-rail__empty">
      Plans, invocations, and artifacts will appear here as the SSE stream arrives.
    </p>
  </section>
</template>

<style scoped>
.execution-rail {
  display: grid;
  gap: 18px;
  min-height: 0;
}

.execution-rail__header {
  padding: 18px 20px 0;
}

.execution-rail__eyebrow,
.execution-rail__section-title,
.stage-card__label {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 0.74rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.execution-rail__title {
  margin: 0;
  font-size: 1.08rem;
}

.execution-rail__section,
.stage-card {
  border: 1px solid var(--border-strong);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-soft);
}

.execution-rail__section {
  padding: 16px;
}

.execution-rail__stages {
  display: grid;
  gap: 14px;
}

.stage-card {
  padding: 18px;
}

.stage-card__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.stage-card__name {
  margin: 0;
  color: var(--text-strong);
  font-weight: 700;
}

.stage-card__description {
  margin: 8px 0 0;
  color: var(--text-soft);
  line-height: 1.55;
}

.stage-card__group {
  margin-top: 16px;
}

.rail-button {
  display: grid;
  gap: 4px;
  width: 100%;
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface-2);
  color: var(--text-strong);
  text-align: left;
  cursor: pointer;
  transition: border-color 180ms ease, transform 180ms ease, box-shadow 180ms ease;
}

.rail-button strong {
  font-size: 0.94rem;
}

.rail-button span {
  color: var(--text-soft);
  font-size: 0.9rem;
}

.rail-button:hover,
.rail-button--active {
  border-color: var(--accent-soft);
  transform: translateY(-1px);
  box-shadow: var(--shadow-soft);
}

.execution-rail__empty {
  margin: 0;
  padding: 20px;
  border: 1px dashed var(--border);
  border-radius: 20px;
  color: var(--text-soft);
  background: rgba(255, 255, 255, 0.7);
}
</style>

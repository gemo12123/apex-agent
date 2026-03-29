<script setup lang="ts">
import { formatRuntimeStatus, toneFromStatus } from '@/features/workspace/presentation'
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
</script>

<template>
  <section class="execution-rail">
    <header class="execution-rail__header">
      <p class="execution-rail__eyebrow">执行轨</p>
      <h2 class="execution-rail__title">计划与产物</h2>
    </header>

    <div v-if="globalArtifacts.length" class="execution-rail__section">
      <div class="execution-rail__section-title">全局产物</div>
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
            <p class="stage-card__description">{{ stage.description || '暂时没有阶段说明。' }}</p>
          </div>
          <span class="status-pill" :class="`status-pill--${toneFromStatus(stage.status)}`">
            {{ formatRuntimeStatus(stage.status) }}
          </span>
        </header>

        <div v-if="stage.invocations.length" class="stage-card__group">
          <p class="stage-card__label">调用</p>
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
          <p class="stage-card__label">产物</p>
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

    <p v-else class="execution-rail__empty">SSE 开始返回后，这里会显示计划、调用和产物。</p>
  </section>
</template>

<style scoped>
.execution-rail {
  display: grid;
  gap: 14px;
  min-height: 0;
}

.execution-rail__header {
  padding: 16px 18px 0;
}

.execution-rail__eyebrow,
.execution-rail__section-title,
.stage-card__label {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-size: 0.84rem;
}

.execution-rail__title {
  font-size: 1.08rem;
}

.execution-rail__section,
.stage-card {
  border: 1px solid var(--border-strong);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}

.execution-rail__section {
  padding: 14px;
}

.execution-rail__stages {
  display: grid;
  gap: 12px;
}

.stage-card {
  padding: 16px;
}

.stage-card__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.stage-card__name {
  color: var(--text-strong);
  font-weight: 700;
}

.stage-card__description {
  margin: 6px 0 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.stage-card__group {
  margin-top: 14px;
}

.rail-button {
  display: grid;
  gap: 4px;
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-subtle);
  color: var(--text-strong);
  text-align: left;
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
  border-color: var(--accent);
  background: var(--accent-subtle);
}

.execution-rail__empty {
  margin: 0;
  padding: 18px;
  border: 1px dashed var(--border);
  border-radius: 18px;
  color: var(--text-soft);
  background: var(--surface-subtle);
}
</style>

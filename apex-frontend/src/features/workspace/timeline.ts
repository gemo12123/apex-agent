import { formatRuntimeStatus, formatSessionStatus, toneFromStatus } from '@/features/workspace/presentation'
import type { SessionViewModel } from '@/types/apex'

export interface TimelineEntry {
  id: string
  kind: 'stage' | 'invocation' | 'artifact' | 'prompt' | 'confirmation' | 'session'
  title: string
  subtitle: string
  tone: ReturnType<typeof toneFromStatus>
  body?: string
  badge?: string
  exportFileName?: string
  defaultExpanded: boolean
}

export function buildTimelineEntries(session: SessionViewModel): TimelineEntry[] {
  const entries: TimelineEntry[] = []

  session.stages.forEach((stage) => {
    entries.push({
      id: `stage:${stage.id}`,
      kind: 'stage',
      title: stage.name,
      subtitle: stage.description || formatRuntimeStatus(stage.status),
      tone: toneFromStatus(stage.status),
      body: stage.description,
      defaultExpanded: false,
    })

    stage.invocations.forEach((invocation) => {
      entries.push({
        id: `invocation:${invocation.id}`,
        kind: 'invocation',
        title: invocation.name,
        subtitle: `${invocation.invocationType} · ${formatRuntimeStatus(invocation.status)}`,
        tone: toneFromStatus(invocation.status),
        body: invocation.content,
        defaultExpanded: false,
      })
    })

    stage.artifacts.forEach((artifact) => {
      entries.push({
        id: `artifact:${artifact.id}`,
        kind: 'artifact',
        title: artifact.artifactName,
        subtitle: artifact.dataType,
        tone: artifact.complete ? 'success' : 'active',
        body: artifact.content,
        exportFileName: `${artifact.artifactName}.md`,
        defaultExpanded: false,
      })
    })
  })

  if (session.pendingPrompts.length > 0) {
    entries.push({
      id: 'prompt:pending',
      kind: 'prompt',
      title: '等待人工确认',
      subtitle: `${session.pendingPrompts.length} 个问题待答复`,
      tone: 'warning',
      body: session.pendingPrompts.map((prompt) => prompt.question).join('\n'),
      defaultExpanded: false,
    })
  }

  if (session.pendingConfirmations.length > 0) {
    entries.push({
      id: 'confirmation:pending',
      kind: 'confirmation',
      title: '等待工具确认',
      subtitle: `${session.pendingConfirmations.length} 个确认待处理`,
      tone: 'warning',
      body: session.pendingConfirmations.map((item) => item.title).join('\n'),
      defaultExpanded: false,
    })
  }

  entries.push({
    id: `session:${session.status}`,
    kind: 'session',
    title: '当前会话状态',
    subtitle: formatSessionStatus(session.status),
    tone: session.status === 'completed' ? 'success' : session.status === 'error' ? 'danger' : 'idle',
    defaultExpanded: true,
  })

  return entries
}

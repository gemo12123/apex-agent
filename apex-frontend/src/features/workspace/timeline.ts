import { formatRuntimeStatus, formatSessionStatus, toneFromStatus } from '@/features/workspace/presentation'
import type { InvocationRecord, SessionViewModel } from '@/types/apex'

export interface TimelineEntry {
  id: string
  kind: 'stage' | 'invocation' | 'artifact' | 'intervention' | 'session'
  title: string
  subtitle: string
  tone: ReturnType<typeof toneFromStatus>
  body?: string
  badge?: string
  exportFileName?: string
  defaultExpanded: boolean
  depth: number
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
      depth: 0,
    })

    nestedInvocations(stage.invocations).forEach(({ invocation, depth }) => {
      entries.push({
        id: `invocation:${invocation.id}`,
        kind: 'invocation',
        title: invocation.name,
        subtitle: `${invocation.invocationType} / ${formatRuntimeStatus(invocation.status)}`,
        tone: toneFromStatus(invocation.status),
        body: invocation.content,
        defaultExpanded: false,
        depth,
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
        depth: 1,
      })
    })
  })

  session.globalArtifacts.forEach((artifact) => {
    entries.push({
      id: `artifact:global:${artifact.id}`,
      kind: 'artifact',
      title: artifact.artifactName,
      subtitle: `全局产物 / ${artifact.dataType}`,
      tone: artifact.complete ? 'success' : 'active',
      body: artifact.content,
      exportFileName: `${artifact.artifactName}.md`,
      defaultExpanded: false,
      depth: 0,
    })
  })

  if (session.pendingInterventions.length > 0) {
    entries.push({
      id: 'intervention:pending',
      kind: 'intervention',
      title: '等待人工介入',
      subtitle: `${session.pendingInterventions.length} 张卡片待处理`,
      tone: 'warning',
      body: session.pendingInterventions
        .map((item) => item.kind === 'question' ? item.question : item.title)
        .join('\n'),
      defaultExpanded: false,
      depth: 0,
    })
  }

  entries.push({
    id: `session:${session.status}`,
    kind: 'session',
    title: '当前会话状态',
    subtitle: formatSessionStatus(session.status),
    tone: session.status === 'completed' ? 'success' : session.status === 'error' ? 'danger' : 'idle',
    defaultExpanded: true,
    depth: 0,
  })

  return entries
}

function nestedInvocations(
  invocations: InvocationRecord[],
): Array<{ invocation: InvocationRecord; depth: number }> {
  const byId = new Map(invocations.map((invocation) => [invocation.id, invocation]))
  const children = new Map<string, InvocationRecord[]>()
  const roots: InvocationRecord[] = []

  invocations.forEach((invocation) => {
    const parentId = invocation.parentInvocationId
    if (!parentId || parentId === invocation.id || !byId.has(parentId)) {
      roots.push(invocation)
      return
    }
    const siblings = children.get(parentId) ?? []
    siblings.push(invocation)
    children.set(parentId, siblings)
  })

  const ordered: Array<{ invocation: InvocationRecord; depth: number }> = []
  const visited = new Set<string>()
  const visit = (invocation: InvocationRecord, depth: number): void => {
    if (visited.has(invocation.id)) {
      return
    }
    visited.add(invocation.id)
    ordered.push({ invocation, depth })
    children.get(invocation.id)?.forEach((child) => visit(child, depth + 1))
  }

  roots.forEach((root) => visit(root, 1))
  invocations.forEach((invocation) => visit(invocation, 1))
  return ordered
}

import { describe, expect, it } from 'vitest'
import { createSessionViewModel } from '@/stores/session/reducer'
import { buildTimelineEntries } from '@/features/workspace/timeline'

describe('buildTimelineEntries', () => {
  it('flattens stage, invocation and artifact records into one ordered timeline', () => {
    const session = createSessionViewModel()
    session.status = 'completed'
    session.stages = [
      {
        id: 'stage-1',
        name: '收集上下文',
        description: '读取 docs',
        status: 'COMPLETE',
        invocations: [
          {
            id: 'invoke-1',
            stageId: 'stage-1',
            name: '搜索联系人',
            invocationType: 'search',
            status: 'COMPLETE',
            renderType: 'markdown',
            content: '找到 2 条记录',
          },
        ],
        artifacts: [
          {
            id: 'artifact-1',
            stageId: 'stage-1',
            scope: 'STAGE',
            artifactName: '报告草稿',
            artifactType: 'document',
            dataType: 'markdown',
            content: '# Draft',
            complete: true,
          },
        ],
      },
    ]

    const entries = buildTimelineEntries(session)

    expect(entries.map((entry) => entry.id)).toEqual([
      'stage:stage-1',
      'invocation:invoke-1',
      'artifact:artifact-1',
      'session:completed',
    ])
    expect(entries.at(-1)?.defaultExpanded).toBe(true)
  })
})

import { describe, expect, it } from 'vitest'
import { fromHistory } from '@/features/conversation/conversation'

describe('conversation history mapper', () => {
  it('restores turns, iterations and resolved tool arguments from the history response', () => {
    const view = fromHistory({
      sessionId: 'session-1',
      agentKey: 'default_agent',
      executionStatus: 'COMPLETED',
      turns: [{
        no: 1,
        question: '查询天气',
        iterations: [{
          no: 1,
          blocks: [
            { type: 'content', id: null, content: '正在查询。', toolName: null, arguments: null, resolvedArguments: null, result: null },
            { type: 'tool', id: 'call-1', content: null, toolName: 'query_weather', arguments: { city: '北京' }, resolvedArguments: { city: '北京市' }, result: '晴天' },
          ],
        }],
      }],
    })

    expect(view.status).toBe('completed')
    expect(view.turns[0]?.question).toBe('查询天气')
    expect(view.turns[0]?.iterations[0]?.blocks).toEqual([
      expect.objectContaining({ type: 'content', content: '正在查询。' }),
      expect.objectContaining({ type: 'tool', arguments: { city: '北京' }, resolvedArguments: { city: '北京市' }, result: '晴天', status: 'COMPLETE' }),
    ])
  })
})

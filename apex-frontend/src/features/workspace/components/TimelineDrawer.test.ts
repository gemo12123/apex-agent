import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TimelineDrawer from '@/features/workspace/components/TimelineDrawer.vue'
import { downloadTextFile } from '@/utils/download'
import type { TimelineEntry } from '@/features/workspace/timeline'

vi.mock('@/utils/download', () => ({
  downloadTextFile: vi.fn(),
}))

const timelineEntries: TimelineEntry[] = [
  {
    id: 'invocation:1',
    kind: 'invocation',
    title: '搜索联系人',
    subtitle: 'search 路径已完成',
    tone: 'success',
    body: '找到 2 条记录。',
    defaultExpanded: false,
  },
  {
    id: 'artifact:1',
    kind: 'artifact',
    title: '报告草稿',
    subtitle: 'markdown',
    tone: 'success',
    body: '# Draft',
    exportFileName: '报告草稿.md',
    defaultExpanded: false,
  },
]

describe('TimelineDrawer', () => {
  beforeEach(() => {
    vi.mocked(downloadTextFile).mockReset()
  })

  it('shows an empty state when no timeline entries exist', () => {
    const wrapper = mount(TimelineDrawer, {
      props: {
        open: true,
        entries: [],
      },
    })

    expect(wrapper.get('[data-testid="timeline-drawer"]').classes()).toContain('timeline-drawer')
    expect(wrapper.text()).toContain('执行开始后，这里会显示计划、调用和产物时间线。')
  })

  it('expands only one entry at a time', async () => {
    const wrapper = mount(TimelineDrawer, {
      props: {
        open: true,
        entries: timelineEntries,
      },
    })

    await wrapper.get('[data-testid="timeline-entry-invocation:1"]').trigger('click')
    expect(wrapper.text()).toContain('找到 2 条记录。')

    await wrapper.get('[data-testid="timeline-entry-artifact:1"]').trigger('click')
    expect(wrapper.text()).toContain('Draft')
    expect(wrapper.text()).not.toContain('找到 2 条记录。')
  })

  it('exports artifact entries', async () => {
    const wrapper = mount(TimelineDrawer, {
      props: {
        open: true,
        entries: timelineEntries,
      },
    })

    await wrapper.get('[data-testid="timeline-entry-artifact:1"]').trigger('click')
    await wrapper.get('[data-testid="timeline-export-artifact:1"]').trigger('click')

    expect(downloadTextFile).toHaveBeenCalledWith('报告草稿.md', '# Draft')
  })
})

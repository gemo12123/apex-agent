import { mount } from '@vue/test-utils'
import ToolConfirmationCard from '@/features/workspace/components/ToolConfirmationCard.vue'

describe('ToolConfirmationCard', () => {
  it('shows summary first, opens edit mode, and submits edited args', async () => {
    const wrapper = mount(ToolConfirmationCard, {
      props: {
        confirmation: {
          id: 'call-1:confirm-1',
          confirmationId: 'confirm-1',
          toolCallId: 'call-1',
          invocationId: 'invocation-1',
          toolName: 'meeting_tool',
          toolDisplayName: '会议室助手',
          title: '预订会议室前确认',
          description: '请确认会议信息。',
          riskLevel: 'MEDIUM',
          editable: true,
          confirmLabel: '确认执行',
          denyLabel: '取消',
          displayFields: [{ key: 'room', label: '会议室', value: 'A1001', type: 'text' }],
          editableFields: [
            {
              key: 'room',
              label: '会议室',
              input_type: 'single-select',
              value: 'A1001',
              required: true,
              options: [{ label: 'A1001' }, { label: 'B2001' }],
            },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('预订会议室前确认')
    expect(wrapper.text()).toContain('会议室助手')
    expect(wrapper.find('select').exists()).toBe(false)

    await wrapper.get('[data-testid="edit-button"]').trigger('click')
    await wrapper.get('select').setValue('B2001')
    await wrapper.get('[data-testid="approve-button"]').trigger('click')

    expect(wrapper.emitted('submit')?.[0]).toEqual([
      { decision: 'APPROVE', updatedArgs: { room: 'B2001' } },
    ])
  })
})

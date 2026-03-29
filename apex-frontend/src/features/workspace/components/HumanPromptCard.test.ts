import { mount } from '@vue/test-utils'
import HumanPromptCard from '@/features/workspace/components/HumanPromptCard.vue'

describe('HumanPromptCard', () => {
  it('submits selected single-select answers', async () => {
    const wrapper = mount(HumanPromptCard, {
      props: {
        prompt: {
          id: 'prompt-1',
          index: 0,
          inputType: 'SINGLE_SELECT',
          question: 'Choose a mode',
          description: 'This affects the next run',
          options: [
            { label: 'react', description: 'Fast iteration' },
            { label: 'plan-executor', description: 'Plan first' },
          ],
          toolCallId: 'tool-call-1',
          answered: false,
        },
      },
    })

    await wrapper.get('.option-chip').trigger('click')
    await wrapper.get('.accent-button').trigger('click')

    expect(wrapper.emitted('submit')?.[0]).toEqual(['react'])
  })

  it('submits multi-select answers with custom values', async () => {
    const wrapper = mount(HumanPromptCard, {
      props: {
        prompt: {
          id: 'prompt-2',
          index: 1,
          inputType: 'MULTI_SELECT',
          question: 'Pick artifacts',
          options: [{ label: 'plan.md' }, { label: 'notes.md' }],
          toolCallId: 'tool-call-1',
          answered: false,
        },
      },
    })

    const options = wrapper.findAll('.option-chip')
    await options[0].trigger('click')
    await wrapper.get('textarea').setValue('summary.md')
    await wrapper.get('.accent-button').trigger('click')

    expect(wrapper.emitted('submit')?.[0]).toEqual([['plan.md', 'summary.md']])
  })
})

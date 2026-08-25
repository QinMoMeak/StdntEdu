import { mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { describe, expect, it, vi } from 'vitest'

import type { Wrong } from '@/api/generated'
import { ReviewResult, WrongSource, WrongStatus } from '@/api/generated'
import WrongQuestionDetailDrawer from './WrongQuestionDetailDrawer.vue'

const wrong: Wrong = {
  id: '9007199254740993', studentId: '9007199254740994', subjectId: '7', sourceType: WrongSource.Practice,
  questionType: 'ESSAY', questionText: '<b>不能执行</b> 长题目', studentAnswer: '学生答案', correctAnswer: '移项求解',
  analysisText: '解题解析', errorType: 'CARELESS', knowledgePoints: [{ knowledgeId: '11', primary: true, confidence: 0.8 }],
  status: WrongStatus.Reviewing, version: 2,
}

function render(options: { reviewEnabled?: boolean; error?: string; loading?: boolean } = {}) {
  return mount(WrongQuestionDetailDrawer, {
    props: {
      modelValue: true, wrong, loading: options.loading ?? false, error: options.error ?? '',
      subjects: [{ id: '7', code: 'MATH', name: '数学', enabled: true }],
      questionTypes: [{ id: '1', dictType: 'question_type', code: 'ESSAY', name: '作文题', enabled: false }],
      errorTypes: [{ id: '2', dictType: 'wrong_question_error_type', code: 'CARELESS', name: '粗心', enabled: true }],
      knowledgeNames: { '11': '一次方程' }, reviewEnabled: options.reviewEnabled ?? true, reviewSaving: false,
    },
    global: {
      plugins: [ElementPlus],
      stubs: { ElDrawer: { props: ['modelValue'], template: '<div v-if="modelValue"><slot/></div>' } },
    },
  })
}

function button(wrapper: ReturnType<typeof render>, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`Button not found: ${text}`)
  return found
}

describe('WrongQuestionDetailDrawer', () => {
  it('renders plain text, historical disabled labels and knowledge names', () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('<b>不能执行</b> 长题目')
    expect(wrapper.html()).not.toContain('<b>不能执行</b> 长题目</b>')
    expect(wrapper.text()).toContain('作文题 · ESSAY')
    expect(wrapper.text()).toContain('一次方程 · 主要 · 80%')
  })

  it('keeps the answer hidden until explicitly revealed', async () => {
    const wrapper = render()
    expect(wrapper.text()).not.toContain('移项求解')
    expect(wrapper.text()).not.toContain('解题解析')
    await button(wrapper, '显示答案').trigger('click')
    expect(wrapper.text()).toContain('移项求解')
    expect(wrapper.text()).toContain('解题解析')
  })

  it('offers all generated review results and emits the real ReviewCreate shape', async () => {
    const wrapper = render()
    await button(wrapper, '开始复习').trigger('click')
    expect(wrapper.text()).toContain('正确')
    expect(wrapper.text()).toContain('部分正确')
    expect(wrapper.text()).toContain('错误')
    expect(wrapper.text()).toContain('未判断')
    const vm = wrapper.vm as unknown as { reviewResult: ReviewResult; submitReview: () => void }
    vm.reviewResult = ReviewResult.Partial
    vm.submitReview()
    expect(wrapper.emitted('review')?.[0]?.[0]).toMatchObject({ result: ReviewResult.Partial })
    expect((wrapper.emitted('review')?.[0]?.[0] as { reviewTime: unknown }).reviewTime).toBeInstanceOf(Date)
  })

  it('keeps review open when no result is selected', async () => {
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = render()
    await button(wrapper, '开始复习').trigger('click')
    ;(wrapper.vm as unknown as { submitReview: () => void }).submitReview()
    expect(wrapper.emitted('review')).toBeUndefined()
    expect(wrapper.text()).toContain('提交复习结果')
  })

  it('does not expose review actions outside the due workflow and supports detail retry', async () => {
    const wrapper = render({ reviewEnabled: false })
    expect(wrapper.text()).not.toContain('开始复习')
    const failed = render({ error: '连接失败' })
    await button(failed, '重试').trigger('click')
    expect(failed.emitted('retry')).toHaveLength(1)
  })
})

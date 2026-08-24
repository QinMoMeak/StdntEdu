import { defineComponent } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Student, StudentCreate, StudentUpdate } from '@/api/generated'
import { AppApiError } from '@/api/errors'
import { studentService } from '@/api/services/studentService'
import { useStudentContextStore } from '@/stores/studentContext'
import StudentView from './StudentView.vue'

vi.mock('@/api/services/studentService', () => ({
  studentService: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    listStages: vi.fn(),
    listGrades: vi.fn(),
  },
}))

const student: Student = {
  id: '9007199254740993',
  studentCode: 'STU0001',
  name: '小明',
  school: '示例学校',
  currentStageId: '1',
  currentGradeId: '2',
  version: 4,
}

const DialogStub = defineComponent({
  name: 'StudentFormDialog',
  props: ['modelValue', 'student', 'stages', 'grades', 'saving', 'conflict'],
  emits: ['update:modelValue', 'submit', 'reload'],
  template: '<div data-test="student-dialog" v-if="modelValue">学生表单</div>',
})

function mountView(): VueWrapper {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(StudentView, {
    global: { plugins: [pinia, ElementPlus], stubs: { StudentFormDialog: DialogStub } },
  })
}

function button(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`Button not found: ${text}`)
  return found
}

describe('StudentView', () => {
  beforeEach(() => {
    vi.mocked(studentService.list).mockResolvedValue([student])
    vi.mocked(studentService.get).mockResolvedValue(student)
    vi.mocked(studentService.create).mockResolvedValue(student)
    vi.mocked(studentService.update).mockResolvedValue(student)
    vi.mocked(studentService.listStages).mockResolvedValue([{ id: '1', code: 'PRIMARY', name: '小学', enabled: true }])
    vi.mocked(studentService.listGrades).mockResolvedValue([
      { id: '2', stageId: '1', code: 'P1', name: '一年级', enabled: true },
    ])
  })

  it('renders the real Student[] response, reference names and no delete/archive UI', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('小明')
    expect(wrapper.text()).toContain('STU0001')
    expect(wrapper.text()).toContain('小学 / 一年级')
    expect(wrapper.text()).not.toMatch(/删除|归档/)
  })

  it('shows table-local loading while the list request is pending', async () => {
    vi.mocked(studentService.list).mockReturnValue(new Promise(() => undefined))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('.el-loading-mask').exists()).toBe(true)
  })

  it('shows the first-use empty state and opens create dialog', async () => {
    vi.mocked(studentService.list).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('尚未创建学生档案')
    await button(wrapper, '创建学生').trigger('click')
    expect(wrapper.find('[data-test="student-dialog"]').exists()).toBe(true)
  })

  it('creates without studentCode and selects the first created student', async () => {
    const wrapper = mountView()
    await flushPromises()
    await button(wrapper, '创建学生').trigger('click')
    const body: StudentCreate = { name: '小明', currentStageId: '1', currentGradeId: '2' }
    wrapper.findComponent(DialogStub).vm.$emit('submit', body)
    await flushPromises()
    expect(studentService.create).toHaveBeenCalledWith(body)
    expect(body).not.toHaveProperty('studentCode')
    expect(useStudentContextStore().currentStudentId).toBe(student.id)
  })

  it('sets a listed student explicitly and displays the current marker', async () => {
    const wrapper = mountView()
    await flushPromises()
    await button(wrapper, '设为当前').trigger('click')
    await flushPromises()
    expect(useStudentContextStore().currentStudentId).toBe(student.id)
    expect(wrapper.text()).toContain('当前学生')
  })

  it('loads latest detail and submits its optimistic-lock version', async () => {
    const wrapper = mountView()
    await flushPromises()
    await button(wrapper, '编辑').trigger('click')
    await flushPromises()
    const body: StudentUpdate = { name: '小明', currentStageId: '1', currentGradeId: '2', version: 4 }
    wrapper.findComponent(DialogStub).vm.$emit('submit', body)
    await flushPromises()
    expect(studentService.get).toHaveBeenCalledWith(student.id)
    expect(studentService.update).toHaveBeenCalledWith(student.id, expect.objectContaining({ version: 4 }))
  })

  it('keeps the form open and exposes reload on DATA_VERSION_CONFLICT', async () => {
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.mocked(studentService.update).mockRejectedValue(
      new AppApiError({ status: 409, errorCode: 'DATA_VERSION_CONFLICT', message: 'conflict' }),
    )
    const wrapper = mountView()
    await flushPromises()
    await button(wrapper, '编辑').trigger('click')
    await flushPromises()
    wrapper.findComponent(DialogStub).vm.$emit('submit', {
      name: '小明',
      currentStageId: '1',
      currentGradeId: '2',
      version: 4,
    } satisfies StudentUpdate)
    await flushPromises()
    expect(wrapper.findComponent(DialogStub).props('conflict')).toBe(true)
    expect(ElMessage.error).toHaveBeenCalledWith('学生档案已被其他操作更新，请重新加载后再修改。')
  })

  it('keeps list API failures visible with a retry action', async () => {
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.mocked(studentService.list).mockRejectedValue(
      new AppApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: '无法连接本地后端服务' }),
    )
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('无法连接本地后端服务')
    expect(wrapper.findAll('button').some((item) => item.text().includes('重试'))).toBe(true)
  })
})

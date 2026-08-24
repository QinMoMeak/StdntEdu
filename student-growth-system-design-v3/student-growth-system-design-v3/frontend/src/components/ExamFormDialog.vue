<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Delete, Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import type { AcademicTermDto, Exam, ExamCreate, ExamType, ExamUpdate, SubjectDto } from '@/api/generated'
import { scoreService } from '@/api/services/scoreService'
import {
  buildExamRequest,
  emptyExamForm,
  examToForm,
  flattenKnowledge,
  newKnowledgeRow,
  newSubjectRow,
  validateExamForm,
  type ExamFormModel,
  type SubjectScoreFormRow,
} from '@/features/score/examForm'
import { handleApiError } from '@/api/notifications'

const props = defineProps<{
  modelValue: boolean
  exam: Exam | null
  currentStudentId: string
  terms: AcademicTermDto[]
  subjects: SubjectDto[]
  examTypes: Array<{ value: ExamType; label: string }>
  saving: boolean
  conflict: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [value: ExamCreate | ExamUpdate]
  reload: []
}>()

const form = ref<ExamFormModel>(emptyExamForm())
const knowledgeOptions = reactive<Record<string, ReturnType<typeof flattenKnowledge>>>({})
const knowledgeLoading = reactive<Record<string, boolean>>({})

const title = computed(() => (props.exam ? '编辑考试' : '新增考试'))
const preview = computed(() => {
  const validRows = form.value.subjects.filter((row) => row.score != null && row.fullScore != null)
  const score = validRows.reduce((sum, row) => sum + (row.score ?? 0), 0)
  const fullScore = validRows.reduce((sum, row) => sum + (row.fullScore ?? 0), 0)
  return { score, fullScore, rate: fullScore > 0 ? score / fullScore : null }
})

function termLabel(term: AcademicTermDto): string {
  const semester = term.semester === 'FIRST' ? '第一学期' : term.semester === 'SECOND' ? '第二学期' : '学年'
  return `${term.academicYear} ${semester}${term.current ? '（当前）' : ''}`
}

async function loadKnowledge(subjectId: string): Promise<void> {
  if (!subjectId || knowledgeOptions[subjectId] || knowledgeLoading[subjectId]) return
  knowledgeLoading[subjectId] = true
  try {
    knowledgeOptions[subjectId] = flattenKnowledge(await scoreService.listKnowledge(subjectId))
  } catch (error) {
    await handleApiError(error)
  } finally {
    knowledgeLoading[subjectId] = false
  }
}

function changeSubject(row: SubjectScoreFormRow): void {
  if (row.knowledgeScores.length) {
    row.knowledgeScores = []
    ElMessage.info('切换学科后已清空原知识点成绩')
  }
  void loadKnowledge(row.subjectId)
}

function addSubject(): void {
  form.value.subjects.push(newSubjectRow())
}

function removeSubject(index: number): void {
  form.value.subjects.splice(index, 1)
}

function addKnowledge(row: SubjectScoreFormRow): void {
  row.knowledgeScores.push(newKnowledgeRow())
}

function submit(): void {
  const message = validateExamForm(form.value)
  if (message) {
    ElMessage.warning(message)
    return
  }
  emit('submit', buildExamRequest(form.value, props.currentStudentId))
}

watch(
  [() => props.modelValue, () => props.exam],
  ([open]) => {
    if (!open) return
    form.value = props.exam ? examToForm(props.exam) : emptyExamForm()
    for (const row of form.value.subjects) void loadKnowledge(row.subjectId)
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(1120px, 94vw)"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="conflict" type="warning" :closable="false" show-icon class="dialog-alert">
      <template #title>考试已被其他操作更新，请重新加载后再提交。</template>
      <el-button link type="primary" :icon="RefreshRight" @click="emit('reload')">重新加载</el-button>
    </el-alert>

    <el-form label-position="top" @submit.prevent="submit">
      <div class="exam-fields">
        <el-form-item label="考试名称" required>
          <el-input v-model="form.examName" maxlength="100" />
        </el-form-item>
        <el-form-item label="考试类型" required>
          <el-select v-model="form.examType">
            <el-option v-for="option in examTypes" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试日期" required>
          <el-date-picker v-model="form.examDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="学年学期">
          <el-select v-model="form.academicTermId" clearable placeholder="可不关联学期">
            <el-option v-for="term in terms" :key="term.id" :label="termLabel(term)" :value="term.id" />
          </el-select>
          <small v-if="!terms.length" class="field-note">当前学生暂无学期，可暂不关联。</small>
        </el-form-item>
      </div>

      <div class="section-heading">
        <div><h3>科目成绩</h3><p>总分由服务端保存；下方仅提供表单预览。</p></div>
        <el-button :icon="Plus" @click="addSubject">添加科目</el-button>
      </div>

      <div class="subject-editor">
        <section v-for="(row, index) in form.subjects" :key="row.key" class="subject-block">
          <div class="subject-row">
            <el-form-item label="学科" required>
              <el-select v-model="row.subjectId" placeholder="选择学科" @change="changeSubject(row)">
                <el-option
                  v-for="subject in subjects"
                  :key="subject.id"
                  :label="subject.name"
                  :value="subject.id"
                  :disabled="form.subjects.some((other) => other !== row && other.subjectId === subject.id)"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="成绩" required><el-input-number v-model="row.score" :min="0" :step="0.5" /></el-form-item>
            <el-form-item label="满分" required><el-input-number v-model="row.fullScore" :min="0.5" :step="0.5" /></el-form-item>
            <el-form-item label="班级排名"><el-input-number v-model="row.classRank" :min="1" :step="1" /></el-form-item>
            <el-form-item label="班级人数"><el-input-number v-model="row.classSize" :min="1" :step="1" /></el-form-item>
            <el-form-item label="年级排名"><el-input-number v-model="row.gradeRank" :min="1" :step="1" /></el-form-item>
            <el-form-item label="年级人数"><el-input-number v-model="row.gradeSize" :min="1" :step="1" /></el-form-item>
            <el-button :icon="Delete" circle title="删除科目" :disabled="form.subjects.length === 1" @click="removeSubject(index)" />
          </div>

          <div class="knowledge-heading">
            <strong>知识点成绩</strong>
            <el-button link type="primary" :icon="Plus" :disabled="!row.subjectId" @click="addKnowledge(row)">添加知识点</el-button>
          </div>
          <p v-if="row.subjectId && !knowledgeLoading[row.subjectId] && !(knowledgeOptions[row.subjectId]?.length)" class="knowledge-empty">
            当前科目尚未维护知识点，可暂不填写知识点成绩。
          </p>
          <div v-for="(knowledge, knowledgeIndex) in row.knowledgeScores" :key="knowledge.key" class="knowledge-row">
            <el-select
              v-model="knowledge.knowledgeId"
              placeholder="选择当前学科知识点"
              :loading="knowledgeLoading[row.subjectId]"
              class="knowledge-select"
            >
              <el-option
                v-for="option in knowledgeOptions[row.subjectId] ?? []"
                :key="option.id"
                :label="option.label"
                :value="option.id"
                :disabled="row.knowledgeScores.some((other) => other !== knowledge && other.knowledgeId === option.id)"
              />
            </el-select>
            <el-input-number v-model="knowledge.score" :min="0" :step="0.5" placeholder="得分" />
            <el-input-number v-model="knowledge.fullScore" :min="0.5" :step="0.5" placeholder="满分" />
            <el-input-number v-model="knowledge.questionCount" :min="0" :step="1" placeholder="题数" />
            <el-input-number v-model="knowledge.correctCount" :min="0" :step="1" placeholder="正确数" />
            <el-button :icon="Delete" circle title="删除知识点成绩" @click="row.knowledgeScores.splice(knowledgeIndex, 1)" />
          </div>
        </section>
      </div>

      <div class="score-preview">
        表单预览：{{ preview.score }} / {{ preview.fullScore }}
        <strong>{{ preview.rate == null ? '-' : `${(preview.rate * 100).toFixed(1)}%` }}</strong>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存考试</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-alert { margin-bottom: 16px; }
.exam-fields { display: grid; grid-template-columns: 2fr 1fr 1fr 1.5fr; gap: 14px; }
.exam-fields :deep(.el-select), .exam-fields :deep(.el-date-editor) { width: 100%; }
.field-note, .section-heading p, .knowledge-empty { color: var(--color-text-muted); font-size: 12px; }
.section-heading, .knowledge-heading, .score-preview { display: flex; align-items: center; justify-content: space-between; }
.section-heading { margin: 4px 0 12px; }
.section-heading h3, .section-heading p { margin: 0; }
.section-heading h3 { font-size: 16px; }
.subject-editor { overflow-x: auto; }
.subject-block { min-width: 980px; padding: 15px 0; border-top: 1px solid var(--color-border); }
.subject-row { display: grid; grid-template-columns: 160px repeat(6, 120px) 34px; gap: 10px; align-items: end; }
.subject-row :deep(.el-input-number), .subject-row :deep(.el-select) { width: 100%; }
.knowledge-heading { margin: 2px 0 8px 170px; }
.knowledge-empty { margin: 8px 0 4px 170px; }
.knowledge-row { display: grid; grid-template-columns: 260px repeat(4, 120px) 34px; gap: 10px; margin: 8px 0 0 170px; }
.knowledge-row :deep(.el-input-number) { width: 100%; }
.score-preview { justify-content: flex-end; gap: 16px; padding: 14px 0 0; border-top: 1px solid var(--color-border); }
@media (max-width: 1000px) { .exam-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>

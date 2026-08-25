<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Delete, Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import type { DictionaryItemDto, SubjectDto, Wrong, WrongCreate, WrongUpdate } from '@/api/generated'
import { WrongSource, WrongStatus } from '@/api/generated'
import { handleApiError } from '@/api/notifications'
import { wrongQuestionService } from '@/api/services/wrongQuestionService'
import { flattenKnowledge } from '@/features/score/examForm'
import {
  buildWrongQuestionRequest,
  emptyWrongQuestionForm,
  newKnowledgeLink,
  validateWrongQuestionForm,
  wrongQuestionToForm,
  type KnowledgeLinkFormRow,
  type WrongQuestionFormModel,
} from '@/features/wrongQuestion/wrongQuestionForm'

const props = defineProps<{
  modelValue: boolean
  wrong: Wrong | null
  currentStudentId: string
  subjects: SubjectDto[]
  questionTypes: DictionaryItemDto[]
  errorTypes: DictionaryItemDto[]
  saving: boolean
  conflict: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [value: WrongCreate | WrongUpdate]
  reload: []
}>()

const form = ref<WrongQuestionFormModel>(emptyWrongQuestionForm())
const knowledgeOptions = reactive<Record<string, ReturnType<typeof flattenKnowledge>>>({})
const knowledgeLoading = reactive<Record<string, boolean>>({})
const title = computed(() => (props.wrong ? '编辑错题' : '新增错题'))
const enabledQuestionTypes = computed(() => new Set(props.questionTypes.filter((item) => item.enabled).map((item) => item.code)))
const enabledErrorTypes = computed(() => new Set(props.errorTypes.filter((item) => item.enabled).map((item) => item.code)))
const sourceOptions = [
  { value: WrongSource.Practice, label: '练习' },
  { value: WrongSource.Exam, label: '考试' },
]
const statusOptions = [
  { value: WrongStatus.New, label: '新建' },
  { value: WrongStatus.Reviewing, label: '复习中' },
  { value: WrongStatus.Mastered, label: '已掌握' },
  { value: WrongStatus.Archived, label: '已归档' },
]

async function loadKnowledge(subjectId: string): Promise<void> {
  if (!subjectId || knowledgeOptions[subjectId] || knowledgeLoading[subjectId]) return
  knowledgeLoading[subjectId] = true
  try {
    knowledgeOptions[subjectId] = flattenKnowledge(await wrongQuestionService.listKnowledge(subjectId))
  } catch (error) {
    await handleApiError(error)
  } finally {
    knowledgeLoading[subjectId] = false
  }
}

function changeSubject(): void {
  if (form.value.knowledgePoints.length) {
    form.value.knowledgePoints = []
    ElMessage.info('切换学科后已清空原知识点关联')
  }
  void loadKnowledge(form.value.subjectId)
}

function setPrimary(row: KnowledgeLinkFormRow): void {
  if (!row.primary) return
  for (const item of form.value.knowledgePoints) if (item !== row) item.primary = false
}

function submit(): void {
  const message = validateWrongQuestionForm(form.value, enabledQuestionTypes.value, enabledErrorTypes.value)
  if (message) {
    ElMessage.warning(message)
    return
  }
  emit('submit', buildWrongQuestionRequest(form.value, props.currentStudentId))
}

watch(
  [() => props.modelValue, () => props.wrong],
  ([open]) => {
    if (!open) return
    form.value = props.wrong ? wrongQuestionToForm(props.wrong) : emptyWrongQuestionForm()
    void loadKnowledge(form.value.subjectId)
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(900px, 94vw)"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="conflict" type="warning" :closable="false" show-icon class="dialog-alert">
      <template #title>错题已被其他操作更新，请重新加载后再提交。</template>
      <el-button link type="primary" :icon="RefreshRight" @click="emit('reload')">重新加载</el-button>
    </el-alert>

    <el-form label-position="top" @submit.prevent="submit">
      <div class="form-grid">
        <el-form-item label="学科" required>
          <el-select v-model="form.subjectId" placeholder="选择学科" @change="changeSubject">
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.name" :value="subject.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" required>
          <el-segmented v-model="form.sourceType" :options="sourceOptions" />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="form.questionType" clearable placeholder="未分类">
            <el-option v-for="item in questionTypes" :key="item.id" :label="`${item.name} · ${item.code}`" :value="item.code" :disabled="!item.enabled" />
          </el-select>
          <small v-if="!questionTypes.length" class="field-note">当前没有题型字典，可保持未分类。</small>
        </el-form-item>
        <el-form-item label="错误类型">
          <el-select v-model="form.errorType" clearable placeholder="未分类">
            <el-option v-for="item in errorTypes" :key="item.id" :label="`${item.name} · ${item.code}`" :value="item.code" :disabled="!item.enabled" />
          </el-select>
          <small v-if="!errorTypes.length" class="field-note">当前没有错误类型字典，可保持未分类。</small>
        </el-form-item>
        <el-form-item label="难度">
          <el-input-number v-model="form.difficulty" :min="1" :max="5" :step="1" />
        </el-form-item>
        <el-form-item v-if="wrong" label="状态">
          <el-select v-model="form.status">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="题目" required>
        <el-input v-model="form.questionText" type="textarea" :rows="4" maxlength="5000" show-word-limit />
      </el-form-item>
      <div class="answer-grid">
        <el-form-item label="学生答案"><el-input v-model="form.studentAnswer" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="正确答案"><el-input v-model="form.correctAnswer" type="textarea" :rows="3" /></el-form-item>
      </div>
      <el-form-item label="解析"><el-input v-model="form.analysisText" type="textarea" :rows="3" /></el-form-item>

      <div class="section-heading">
        <div><h3>知识点</h3><p>只显示当前学科启用的知识点；最多一个主要知识点。</p></div>
        <el-button :icon="Plus" :disabled="!form.subjectId" @click="form.knowledgePoints.push(newKnowledgeLink())">添加知识点</el-button>
      </div>
      <p v-if="form.subjectId && !knowledgeLoading[form.subjectId] && !(knowledgeOptions[form.subjectId]?.length)" class="knowledge-empty">
        当前学科没有可关联的知识点。
      </p>
      <div v-for="(row, index) in form.knowledgePoints" :key="row.key" class="knowledge-row">
        <el-select v-model="row.knowledgeId" placeholder="选择知识点" :loading="knowledgeLoading[form.subjectId]">
          <el-option
            v-for="option in knowledgeOptions[form.subjectId] ?? []"
            :key="option.id"
            :label="option.label"
            :value="option.id"
            :disabled="form.knowledgePoints.some((other) => other !== row && other.knowledgeId === option.id)"
          />
        </el-select>
        <el-checkbox v-model="row.primary" @change="setPrimary(row)">主要</el-checkbox>
        <el-input-number v-model="row.confidence" :min="0" :max="1" :step="0.05" placeholder="置信度" />
        <el-button :icon="Delete" circle title="删除知识点" @click="form.knowledgePoints.splice(index, 1)" />
      </div>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存错题</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-alert { margin-bottom: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 14px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-input-number), .form-grid :deep(.el-segmented) { width: 100%; }
.answer-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.field-note, .section-heading p, .knowledge-empty { color: var(--color-text-muted); font-size: 12px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin: 4px 0 12px; }
.section-heading h3, .section-heading p { margin: 0; }
.section-heading h3 { font-size: 16px; }
.knowledge-empty { padding: 18px; text-align: center; border: 1px dashed var(--color-border); }
.knowledge-row { display: grid; grid-template-columns: minmax(260px, 1fr) 90px 150px 34px; gap: 12px; align-items: center; margin-top: 10px; }
.knowledge-row :deep(.el-select), .knowledge-row :deep(.el-input-number) { width: 100%; }
@media (max-width: 1000px) { .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>

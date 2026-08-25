<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Edit, RefreshRight, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import type { DictionaryItemDto, ReviewCreate, ReviewResult as ReviewResultType, SubjectDto, Wrong } from '@/api/generated'
import { ReviewResult, WrongStatus } from '@/api/generated'

const props = defineProps<{
  modelValue: boolean
  wrong: Wrong | null
  loading: boolean
  error: string
  subjects: SubjectDto[]
  questionTypes: DictionaryItemDto[]
  errorTypes: DictionaryItemDto[]
  knowledgeNames: Record<string, string>
  reviewEnabled: boolean
  reviewSaving: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: []
  remove: []
  retry: []
  review: [value: ReviewCreate]
}>()

const answerVisible = ref(false)
const reviewOpen = ref(false)
const reviewResult = ref<ReviewResultType | ''>('')
const durationSeconds = ref<number | null>(null)
const reviewAnswer = ref('')
const remark = ref('')
const subjectNames = computed(() => new Map(props.subjects.map((item) => [item.id, item.name])))
const questionTypeNames = computed(() => new Map(props.questionTypes.map((item) => [item.code, item.name])))
const errorTypeNames = computed(() => new Map(props.errorTypes.map((item) => [item.code, item.name])))
const resultOptions = [
  { value: ReviewResult.Correct, label: '正确' },
  { value: ReviewResult.Partial, label: '部分正确' },
  { value: ReviewResult.Wrong, label: '错误' },
  { value: ReviewResult.Unknown, label: '未判断' },
]

function label(value: string | null | undefined, names: Map<string, string>): string {
  return value ? `${names.get(value) ?? value}${names.has(value) ? ` · ${value}` : ''}` : '未分类'
}

function statusLabel(status: Wrong['status']): string {
  return { NEW: '新建', REVIEWING: '复习中', MASTERED: '已掌握', ARCHIVED: '已归档' }[status]
}

function sourceLabel(source: Wrong['sourceType']): string {
  return source === 'EXAM' ? '考试' : '练习'
}

function submitReview(): void {
  if (!reviewResult.value) {
    ElMessage.warning('请选择复习结果')
    return
  }
  emit('review', {
    reviewTime: new Date(),
    result: reviewResult.value,
    durationSeconds: durationSeconds.value,
    studentAnswer: reviewAnswer.value.trim() || null,
    remark: remark.value.trim() || null,
  })
}

function completeReview(): void {
  reviewOpen.value = false
  reviewResult.value = ''
  durationSeconds.value = null
  reviewAnswer.value = ''
  remark.value = ''
}

defineExpose({ completeReview })

watch(
  [() => props.modelValue, () => props.wrong?.id],
  ([open]) => {
    if (!open) return
    answerVisible.value = false
    completeReview()
  },
)
</script>

<template>
  <el-drawer :model-value="modelValue" title="错题详情" size="min(760px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <el-skeleton v-if="loading" :rows="10" animated />
    <el-result v-else-if="error" icon="error" title="详情加载失败" :sub-title="error">
      <template #extra>
        <el-button :icon="RefreshRight" @click="emit('retry')">重试</el-button>
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </template>
    </el-result>
    <template v-else-if="wrong">
      <div class="detail-heading">
        <div><h3>{{ subjectNames.get(wrong.subjectId) ?? wrong.subjectId }}</h3><p>{{ sourceLabel(wrong.sourceType) }} · {{ statusLabel(wrong.status) }}</p></div>
        <div class="detail-actions">
          <el-button :icon="Edit" @click="emit('edit')">编辑</el-button>
          <el-button type="danger" plain :icon="Delete" @click="emit('remove')">删除</el-button>
        </div>
      </div>

      <dl class="meta-grid">
        <div><dt>题型</dt><dd>{{ label(wrong.questionType, questionTypeNames) }}</dd></div>
        <div><dt>错误类型</dt><dd>{{ label(wrong.errorType, errorTypeNames) }}</dd></div>
        <div><dt>难度</dt><dd>{{ wrong.difficulty ?? '未设置' }}</dd></div>
        <div><dt>版本</dt><dd>{{ wrong.version }}</dd></div>
      </dl>

      <section class="text-section"><h4>题目</h4><p>{{ wrong.questionText }}</p></section>
      <section class="text-section"><h4>学生答案</h4><p>{{ wrong.studentAnswer || '未记录学生答案' }}</p></section>
      <section class="answer-section">
        <div class="section-title"><h4>正确答案与解析</h4><el-button v-if="!answerVisible" link type="primary" :icon="View" @click="answerVisible = true">显示答案</el-button></div>
        <template v-if="answerVisible">
          <p><strong>正确答案</strong><br />{{ wrong.correctAnswer || '未记录正确答案' }}</p>
          <p><strong>解析</strong><br />{{ wrong.analysisText || '未记录解析' }}</p>
        </template>
        <p v-else class="muted">答案默认隐藏。</p>
      </section>

      <section class="knowledge-section">
        <h4>知识点</h4>
        <div v-if="wrong.knowledgePoints?.length" class="knowledge-list">
          <el-tag v-for="link in wrong.knowledgePoints" :key="link.knowledgeId" :type="link.primary ? 'primary' : 'info'" effect="plain">
            {{ knowledgeNames[link.knowledgeId] ?? link.knowledgeId }}{{ link.primary ? ' · 主要' : '' }}{{ link.confidence == null ? '' : ` · ${Math.round(link.confidence * 100)}%` }}
          </el-tag>
        </div>
        <p v-else class="muted">未关联知识点。</p>
      </section>

      <section v-if="reviewEnabled && wrong.status !== WrongStatus.Archived" class="review-section">
        <div class="section-title"><div><h4>到期复习</h4><p>提交结果后由后端计算状态、掌握度和下次复习时间。</p></div><el-button v-if="!reviewOpen" type="primary" @click="reviewOpen = true">开始复习</el-button></div>
        <el-form v-if="reviewOpen" label-position="top" @submit.prevent="submitReview">
          <el-form-item label="复习结果" required><el-segmented v-model="reviewResult" :options="resultOptions" /></el-form-item>
          <div class="review-grid">
            <el-form-item label="本次答案"><el-input v-model="reviewAnswer" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="备注"><el-input v-model="remark" type="textarea" :rows="3" /></el-form-item>
          </div>
          <el-form-item label="用时（秒）"><el-input-number v-model="durationSeconds" :min="0" :step="10" /></el-form-item>
          <el-button @click="reviewOpen = false">取消</el-button>
          <el-button type="primary" :loading="reviewSaving" @click="submitReview">提交复习结果</el-button>
        </el-form>
      </section>
    </template>
  </el-drawer>
</template>

<style scoped>
.detail-heading, .detail-actions, .section-title, .knowledge-list { display: flex; align-items: center; }
.detail-heading { justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.detail-heading h3, .detail-heading p, .section-title h4, .section-title p { margin: 0; }
.detail-heading h3 { font-size: 20px; }
.detail-heading p, .muted, .section-title p { color: var(--color-text-muted); }
.detail-heading p { margin-top: 5px; }
.detail-actions { gap: 8px; }
.meta-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0 0 20px; border: 1px solid var(--color-border); }
.meta-grid div { min-width: 0; padding: 13px; }
.meta-grid div + div { border-left: 1px solid var(--color-border); }
.meta-grid dt { color: var(--color-text-muted); font-size: 12px; }
.meta-grid dd { overflow-wrap: anywhere; margin: 5px 0 0; font-weight: 600; }
.text-section, .answer-section, .knowledge-section, .review-section { padding: 16px 0; border-top: 1px solid var(--color-border); }
.text-section h4, .answer-section h4, .knowledge-section h4, .review-section h4 { margin: 0 0 9px; font-size: 15px; }
.text-section p, .answer-section p { margin: 0; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.answer-section p + p { margin-top: 14px; }
.section-title { justify-content: space-between; gap: 16px; }
.section-title p { margin-top: 4px; font-size: 12px; }
.knowledge-list { flex-wrap: wrap; gap: 8px; }
.review-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.review-section :deep(.el-segmented) { width: 100%; }
</style>

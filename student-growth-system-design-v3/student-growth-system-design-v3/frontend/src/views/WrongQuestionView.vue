<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Edit, Plus, RefreshRight, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'

import type { AppApiError } from '@/api/errors'
import { errorDisplayMessage, normalizeApiError } from '@/api/errors'
import type { DictionaryItemDto, ReviewCreate, SubjectDto, Wrong, WrongCreate, WrongUpdate } from '@/api/generated'
import { handleApiError } from '@/api/notifications'
import { reviewService } from '@/api/services/reviewService'
import { wrongQuestionService } from '@/api/services/wrongQuestionService'
import WrongQuestionDetailDrawer from '@/components/WrongQuestionDetailDrawer.vue'
import WrongQuestionFormDialog from '@/components/WrongQuestionFormDialog.vue'
import { flattenKnowledge } from '@/features/score/examForm'
import { useStudentContextStore } from '@/stores/studentContext'

const router = useRouter()
const studentStore = useStudentContextStore()
const { currentStudentId, currentStudent, loading: studentsLoading, initialized } = storeToRefs(studentStore)
const items = ref<Wrong[]>([])
const dueItems = ref<Wrong[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const duePage = ref(1)
const duePageSize = ref(10)
const dueTotal = ref(0)
const loading = ref(false)
const dueLoading = ref(false)
const loadError = ref<AppApiError | null>(null)
const dueError = ref<AppApiError | null>(null)
const subjects = ref<SubjectDto[]>([])
const questionTypes = ref<DictionaryItemDto[]>([])
const errorTypes = ref<DictionaryItemDto[]>([])
const knowledgeNames = reactive<Record<string, string>>({})
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailWrong = ref<Wrong | null>(null)
const detailId = ref('')
const detailReviewEnabled = ref(false)
const formOpen = ref(false)
const formWrong = ref<Wrong | null>(null)
const saving = ref(false)
const conflict = ref(false)
const reviewSaving = ref(false)
const detailRef = ref<InstanceType<typeof WrongQuestionDetailDrawer> | null>(null)
let listSequence = 0
let dueSequence = 0
let detailSequence = 0
let referencesLoaded = false

const subjectNames = computed(() => new Map(subjects.value.map((item) => [item.id, item.name])))
const questionTypeNames = computed(() => new Map(questionTypes.value.map((item) => [item.code, item.name])))
const errorTypeNames = computed(() => new Map(errorTypes.value.map((item) => [item.code, item.name])))

function categoryLabel(code: string | null | undefined, names: Map<string, string>): string {
  return code ? names.get(code) ?? code : '未分类'
}

function sourceLabel(source: Wrong['sourceType']): string {
  return source === 'EXAM' ? '考试' : '练习'
}

function statusLabel(status: Wrong['status']): string {
  return { NEW: '新建', REVIEWING: '复习中', MASTERED: '已掌握', ARCHIVED: '已归档' }[status]
}

function statusType(status: Wrong['status']): 'info' | 'warning' | 'success' | 'primary' {
  return { NEW: 'info', REVIEWING: 'warning', MASTERED: 'success', ARCHIVED: 'primary' }[status] as 'info' | 'warning' | 'success' | 'primary'
}

async function loadReferences(): Promise<void> {
  if (referencesLoaded) return
  try {
    const [subjectRows, questionRows, errorRows] = await Promise.all([
      wrongQuestionService.listSubjects(),
      wrongQuestionService.listDictionary('question_type'),
      wrongQuestionService.listDictionary('wrong_question_error_type'),
    ])
    subjects.value = subjectRows
    questionTypes.value = questionRows
    errorTypes.value = errorRows
    referencesLoaded = true
  } catch (error) {
    await handleApiError(error)
  }
}

async function loadList(): Promise<void> {
  const studentId = currentStudentId.value
  if (!studentId) return
  const sequence = ++listSequence
  loading.value = true
  loadError.value = null
  try {
    const result = await wrongQuestionService.list(studentId, page.value, pageSize.value)
    if (sequence !== listSequence || studentId !== currentStudentId.value) return
    items.value = result.items ?? []
    total.value = result.total
  } catch (error) {
    if (sequence === listSequence) loadError.value = await normalizeApiError(error)
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}

async function loadDue(): Promise<void> {
  const studentId = currentStudentId.value
  if (!studentId) return
  const sequence = ++dueSequence
  dueLoading.value = true
  dueError.value = null
  try {
    const result = await reviewService.listDue(studentId, duePage.value, duePageSize.value)
    if (sequence !== dueSequence || studentId !== currentStudentId.value) return
    dueItems.value = result.items ?? []
    dueTotal.value = result.total
  } catch (error) {
    if (sequence === dueSequence) dueError.value = await normalizeApiError(error)
  } finally {
    if (sequence === dueSequence) dueLoading.value = false
  }
}

async function loadKnowledgeNames(subjectId: string): Promise<void> {
  try {
    for (const node of flattenKnowledge(await wrongQuestionService.listKnowledge(subjectId))) knowledgeNames[node.id] = node.name
  } catch (error) {
    await handleApiError(error)
  }
}

async function openDetail(wrongQuestionId: string, reviewEnabled = false): Promise<void> {
  const sequence = ++detailSequence
  detailOpen.value = true
  detailId.value = wrongQuestionId
  detailLoading.value = true
  detailError.value = ''
  detailWrong.value = null
  detailReviewEnabled.value = reviewEnabled
  try {
    const wrong = await wrongQuestionService.get(wrongQuestionId)
    if (sequence !== detailSequence || !detailOpen.value) return
    detailWrong.value = wrong
    void loadKnowledgeNames(wrong.subjectId)
  } catch (error) {
    if (sequence === detailSequence) detailError.value = errorDisplayMessage(await normalizeApiError(error))
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}

function retryDetail(): void {
  if (detailId.value) void openDetail(detailId.value, detailReviewEnabled.value)
}

function refreshAll(): void {
  void Promise.all([loadList(), loadDue()])
}

function openCreate(): void {
  formWrong.value = null
  conflict.value = false
  formOpen.value = true
}

function openEdit(): void {
  if (!detailWrong.value) return
  formWrong.value = detailWrong.value
  conflict.value = false
  formOpen.value = true
}

async function saveWrong(request: WrongCreate | WrongUpdate): Promise<void> {
  saving.value = true
  conflict.value = false
  try {
    const editing = formWrong.value
    const saved = editing
      ? await wrongQuestionService.update(editing.id, request as WrongUpdate)
      : await wrongQuestionService.create(request as WrongCreate)
    formOpen.value = false
    ElMessage.success(editing ? '错题已更新' : '错题已创建')
    await Promise.all([loadList(), loadDue()])
    if (editing) {
      detailWrong.value = saved
      detailOpen.value = true
      void loadKnowledgeNames(saved.subjectId)
    }
  } catch (error) {
    const normalized = await normalizeApiError(error)
    if (normalized.errorCode === 'DATA_VERSION_CONFLICT') {
      conflict.value = true
      ElMessage.error('错题已被其他操作更新，请重新加载后再修改。')
    } else await handleApiError(normalized)
  } finally {
    saving.value = false
  }
}

async function reloadFormWrong(): Promise<void> {
  if (!formWrong.value) return
  try {
    formWrong.value = await wrongQuestionService.get(formWrong.value.id)
    detailWrong.value = formWrong.value
    conflict.value = false
  } catch (error) {
    await handleApiError(error)
  }
}

async function removeWrong(): Promise<void> {
  if (!detailWrong.value) return
  try {
    await ElMessageBox.confirm('确认删除当前错题？', '删除错题', { type: 'warning' })
    await wrongQuestionService.remove(detailWrong.value.id)
    detailOpen.value = false
    detailWrong.value = null
    ElMessage.success('错题已删除')
    await Promise.all([loadList(), loadDue()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    await handleApiError(error)
  }
}

async function submitReview(request: ReviewCreate): Promise<void> {
  if (!detailWrong.value) return
  reviewSaving.value = true
  try {
    const id = detailWrong.value.id
    await reviewService.submit(id, request)
    ElMessage.success('复习结果已提交')
    detailRef.value?.completeReview()
    await Promise.all([openDetail(id, true), loadList(), loadDue()])
  } catch (error) {
    await handleApiError(error)
  } finally {
    reviewSaving.value = false
  }
}

watch(
  currentStudentId,
  (studentId) => {
    page.value = 1
    duePage.value = 1
    items.value = []
    dueItems.value = []
    total.value = 0
    dueTotal.value = 0
    detailOpen.value = false
    formOpen.value = false
    ++detailSequence
    if (studentId) {
      void loadReferences()
      void loadList()
      void loadDue()
    } else {
      ++listSequence
      ++dueSequence
      loading.value = false
      dueLoading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="wrong-view" aria-labelledby="wrong-title">
    <el-skeleton v-if="studentsLoading || (!initialized && !currentStudentId)" :rows="8" animated />
    <el-empty v-else-if="!currentStudentId" description="请先选择学生">
      <el-button type="primary" :icon="User" @click="router.push('/students')">前往学生档案</el-button>
    </el-empty>

    <template v-else>
      <div class="page-toolbar">
        <div><p>当前学生 · {{ currentStudent?.name ?? currentStudentId }}</p><h2 id="wrong-title">错题与复习</h2></div>
        <div class="toolbar-actions">
          <el-button :icon="RefreshRight" :loading="loading || dueLoading" @click="refreshAll">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增错题</el-button>
        </div>
      </div>

      <el-tabs class="wrong-tabs">
        <el-tab-pane label="全部错题">
          <el-alert v-if="loadError" type="error" :closable="false" show-icon class="page-alert">
            <template #title>{{ errorDisplayMessage(loadError) }}</template>
            <el-button link type="primary" @click="loadList">重试</el-button>
          </el-alert>
          <el-table v-loading="loading" :data="items" row-key="id" class="wrong-table">
            <el-table-column label="学科" min-width="105"><template #default="{ row }">{{ subjectNames.get(row.subjectId) ?? row.subjectId }}</template></el-table-column>
            <el-table-column label="题目" min-width="260" show-overflow-tooltip><template #default="{ row }"><span class="question-summary">{{ row.questionText }}</span></template></el-table-column>
            <el-table-column label="来源" width="80"><template #default="{ row }">{{ sourceLabel(row.sourceType) }}</template></el-table-column>
            <el-table-column label="状态" width="95"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="题型" min-width="120"><template #default="{ row }">{{ categoryLabel(row.questionType, questionTypeNames) }}</template></el-table-column>
            <el-table-column label="错误类型" min-width="130"><template #default="{ row }">{{ categoryLabel(row.errorType, errorTypeNames) }}</template></el-table-column>
            <el-table-column label="知识点" width="90"><template #default="{ row }">{{ row.knowledgePoints?.length ?? 0 }} 项</template></el-table-column>
            <el-table-column label="操作" width="145" fixed="right" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
                <el-button link type="primary" :icon="Edit" @click="openDetail(row.id).then(openEdit)">编辑</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="当前学生尚未记录错题"><el-button type="primary" @click="openCreate">新增错题</el-button></el-empty></template>
          </el-table>
          <div v-if="total > pageSize" class="pagination">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" layout="total, sizes, prev, pager, next" :total="total" :page-sizes="[10, 20, 50]" @change="loadList" />
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`待复习 ${dueTotal ? `(${dueTotal})` : ''}`">
          <el-alert v-if="dueError" type="error" :closable="false" show-icon class="page-alert">
            <template #title>{{ errorDisplayMessage(dueError) }}</template>
            <el-button link type="primary" @click="loadDue">重试</el-button>
          </el-alert>
          <el-table v-loading="dueLoading" :data="dueItems" row-key="id" class="wrong-table due-table">
            <el-table-column label="学科" min-width="120"><template #default="{ row }">{{ subjectNames.get(row.subjectId) ?? row.subjectId }}</template></el-table-column>
            <el-table-column label="到期题目" min-width="380" show-overflow-tooltip prop="questionText" />
            <el-table-column label="题型" min-width="140"><template #default="{ row }">{{ categoryLabel(row.questionType, questionTypeNames) }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
            <el-table-column label="操作" width="130" fixed="right" align="right"><template #default="{ row }"><el-button type="primary" link @click="openDetail(row.id, true)">开始复习</el-button></template></el-table-column>
            <template #empty><el-empty description="当前没有到期复习内容" /></template>
          </el-table>
          <div v-if="dueTotal > duePageSize" class="pagination">
            <el-pagination v-model:current-page="duePage" v-model:page-size="duePageSize" layout="total, sizes, prev, pager, next" :total="dueTotal" :page-sizes="[10, 20, 50]" @change="loadDue" />
          </div>
        </el-tab-pane>
      </el-tabs>

      <WrongQuestionFormDialog
        v-model="formOpen" :wrong="formWrong" :current-student-id="currentStudentId" :subjects="subjects"
        :question-types="questionTypes" :error-types="errorTypes" :saving="saving" :conflict="conflict"
        @submit="saveWrong" @reload="reloadFormWrong"
      />
      <WrongQuestionDetailDrawer
        ref="detailRef" v-model="detailOpen" :wrong="detailWrong" :loading="detailLoading" :error="detailError"
        :subjects="subjects" :question-types="questionTypes" :error-types="errorTypes" :knowledge-names="knowledgeNames"
        :review-enabled="detailReviewEnabled" :review-saving="reviewSaving" @edit="openEdit" @remove="removeWrong"
        @retry="retryDetail" @review="submitReview"
      />
    </template>
  </section>
</template>

<style scoped>
.wrong-view { width: 100%; max-width: 1440px; margin: 0 auto; }
.page-toolbar, .toolbar-actions, .pagination { display: flex; align-items: center; }
.page-toolbar { justify-content: space-between; margin-bottom: 14px; }
.page-toolbar h2, .page-toolbar p { margin: 0; }
.page-toolbar h2 { font-size: 22px; }
.page-toolbar p { margin-bottom: 5px; color: var(--color-text-muted); font-size: 12px; }
.toolbar-actions { gap: 8px; }
.wrong-tabs { padding: 0 14px 14px; background: var(--color-surface); border: 1px solid var(--color-border); }
.page-alert { margin: 4px 0 14px; }
.wrong-table { width: 100%; border: 1px solid var(--color-border); }
.question-summary { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>

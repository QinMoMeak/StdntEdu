<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Edit, Plus, RefreshRight, Search, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'

import ExamDetailDrawer from '@/components/ExamDetailDrawer.vue'
import ExamFormDialog from '@/components/ExamFormDialog.vue'
import type { AcademicTermDto, Exam, ExamCreate, ExamType, ExamUpdate, ScoreListItemDto, SubjectDto } from '@/api/generated'
import { ExamType as ExamTypeValues } from '@/api/generated'
import { AppApiError, normalizeApiError } from '@/api/errors'
import { handleApiError } from '@/api/notifications'
import { scoreService } from '@/api/services/scoreService'
import { useStudentContextStore } from '@/stores/studentContext'
import { businessDateToApiDate, formatDate, formatPercent } from '@/utils/dateTime'

const router = useRouter()
const studentStore = useStudentContextStore()
const { currentStudentId, currentStudent, loading: studentsLoading, initialized } = storeToRefs(studentStore)

const examTypes: Array<{ value: ExamType; label: string }> = [
  { value: ExamTypeValues.DailyTest, label: '日常测验' },
  { value: ExamTypeValues.UnitTest, label: '单元测验' },
  { value: ExamTypeValues.WeeklyTest, label: '周测' },
  { value: ExamTypeValues.MonthlyExam, label: '月考' },
  { value: ExamTypeValues.Midterm, label: '期中考试' },
  { value: ExamTypeValues.Final, label: '期末考试' },
  { value: ExamTypeValues.MockExam, label: '模拟考试' },
  { value: ExamTypeValues.Competition, label: '竞赛' },
  { value: ExamTypeValues.Other, label: '其他' },
]

const filters = reactive({ academicTermId: '', subjectId: '', examType: '' as ExamType | '', startDate: '', endDate: '', keyword: '' })
const scores = ref<ScoreListItemDto[]>([])
const terms = ref<AcademicTermDto[]>([])
const subjects = ref<SubjectDto[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const loadError = ref<AppApiError | null>(null)
const formOpen = ref(false)
const formExam = ref<Exam | null>(null)
const saving = ref(false)
const conflict = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailExam = ref<Exam | null>(null)
let listSequence = 0
let referenceSequence = 0
let detailSequence = 0

const subjectNames = computed(() => new Map(subjects.value.map((item) => [item.id, item.name])))
const filtersActive = computed(() => Object.values(filters).some(Boolean))

function examTypeLabel(value: ExamType): string {
  return examTypes.find((item) => item.value === value)?.label ?? value
}

function termLabel(term: AcademicTermDto): string {
  const semester = term.semester === 'FIRST' ? '第一学期' : term.semester === 'SECOND' ? '第二学期' : '学年'
  return `${term.academicYear} ${semester}`
}

async function loadReferences(studentId: string): Promise<void> {
  const sequence = ++referenceSequence
  try {
    const [termList, subjectList] = await Promise.all([scoreService.listTerms(studentId), scoreService.listSubjects()])
    if (sequence !== referenceSequence || studentId !== currentStudentId.value) return
    terms.value = termList
    subjects.value = subjectList
  } catch (error) {
    if (sequence === referenceSequence) await handleApiError(error)
  }
}

async function loadScores(): Promise<void> {
  const studentId = currentStudentId.value
  const sequence = ++listSequence
  loadError.value = null
  if (!studentId) {
    scores.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await scoreService.list(studentId, {
      academicTermId: filters.academicTermId || undefined,
      subjectId: filters.subjectId || undefined,
      examType: filters.examType || undefined,
      startDate: filters.startDate ? businessDateToApiDate(filters.startDate) : undefined,
      endDate: filters.endDate ? businessDateToApiDate(filters.endDate) : undefined,
      keyword: filters.keyword.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (sequence !== listSequence || studentId !== currentStudentId.value) return
    scores.value = result.items ?? []
    total.value = result.total
  } catch (error) {
    if (sequence !== listSequence || studentId !== currentStudentId.value) return
    loadError.value = await normalizeApiError(error)
    await handleApiError(loadError.value)
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}

function search(): void {
  if (filters.startDate && filters.endDate && filters.endDate < filters.startDate) {
    ElMessage.warning('结束日期不能早于开始日期')
    return
  }
  page.value = 1
  void loadScores()
}

function clearFilters(): void {
  Object.assign(filters, { academicTermId: '', subjectId: '', examType: '', startDate: '', endDate: '', keyword: '' })
  page.value = 1
  void loadScores()
}

function openCreate(): void {
  formExam.value = null
  conflict.value = false
  formOpen.value = true
}

async function openDetail(examId: string): Promise<void> {
  const sequence = ++detailSequence
  detailOpen.value = true
  detailLoading.value = true
  detailExam.value = null
  try {
    const exam = await scoreService.get(examId)
    if (sequence === detailSequence && detailOpen.value) detailExam.value = exam
  } catch (error) {
    if (sequence === detailSequence) {
      detailOpen.value = false
      await handleApiError(error)
    }
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}

function openEdit(): void {
  if (!detailExam.value) return
  formExam.value = detailExam.value
  conflict.value = false
  formOpen.value = true
}

async function saveExam(request: ExamCreate | ExamUpdate): Promise<void> {
  saving.value = true
  conflict.value = false
  try {
    const saved = formExam.value
      ? await scoreService.update(formExam.value.id, request as ExamUpdate)
      : await scoreService.create(request as ExamCreate)
    formOpen.value = false
    ElMessage.success(formExam.value ? '考试已更新' : '考试已创建')
    await loadScores()
    if (detailOpen.value || formExam.value) {
      detailExam.value = saved
      detailOpen.value = true
    }
  } catch (error) {
    const normalized = await normalizeApiError(error)
    if (normalized.errorCode === 'DATA_VERSION_CONFLICT') {
      conflict.value = true
      ElMessage.error('考试已被其他操作更新，请重新加载后再修改。')
    } else await handleApiError(normalized)
  } finally {
    saving.value = false
  }
}

async function reloadFormExam(): Promise<void> {
  if (!formExam.value) return
  try {
    formExam.value = await scoreService.get(formExam.value.id)
    detailExam.value = formExam.value
    conflict.value = false
  } catch (error) {
    await handleApiError(error)
  }
}

async function removeExam(): Promise<void> {
  if (!detailExam.value) return
  try {
    await ElMessageBox.confirm(`确认删除考试“${detailExam.value.examName}”？`, '删除考试', { type: 'warning' })
    await scoreService.remove(detailExam.value.id)
    detailOpen.value = false
    detailExam.value = null
    ElMessage.success('考试已删除')
    await loadScores()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    await handleApiError(error)
  }
}

watch(
  currentStudentId,
  (studentId) => {
    page.value = 1
    Object.assign(filters, { academicTermId: '', subjectId: '', examType: '', startDate: '', endDate: '', keyword: '' })
    scores.value = []
    terms.value = []
    detailOpen.value = false
    formOpen.value = false
    ++detailSequence
    if (studentId) {
      void loadReferences(studentId)
      void loadScores()
    } else {
      ++listSequence
      loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="score-view" aria-labelledby="scores-title">
    <el-skeleton v-if="studentsLoading || (!initialized && !currentStudentId)" :rows="7" animated />

    <el-empty v-else-if="!currentStudentId" description="请先选择学生">
      <el-button type="primary" :icon="User" @click="router.push('/students')">前往学生档案</el-button>
    </el-empty>

    <template v-else>
      <div class="page-toolbar">
        <div><p>当前学生 · {{ currentStudent?.name ?? currentStudentId }}</p><h2 id="scores-title">成绩与考试</h2></div>
        <div class="toolbar-actions">
          <el-button :icon="RefreshRight" :loading="loading" @click="loadScores">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增考试</el-button>
        </div>
      </div>

      <div class="filters" aria-label="成绩筛选">
        <el-input v-model="filters.keyword" clearable placeholder="考试名称" @keyup.enter="search" />
        <el-select v-model="filters.academicTermId" clearable placeholder="全部学期">
          <el-option v-for="term in terms" :key="term.id" :label="termLabel(term)" :value="term.id" />
        </el-select>
        <el-select v-model="filters.subjectId" clearable placeholder="全部学科">
          <el-option v-for="subject in subjects" :key="subject.id" :label="subject.name" :value="subject.id" />
        </el-select>
        <el-select v-model="filters.examType" clearable placeholder="全部类型">
          <el-option v-for="option in examTypes" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-date-picker v-model="filters.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
        <el-date-picker v-model="filters.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button v-if="filtersActive" @click="clearFilters">清空</el-button>
      </div>

      <el-alert v-if="loadError" type="error" :closable="false" show-icon class="page-alert">
        <template #title>{{ loadError.message }}</template>
        <el-button link type="primary" @click="loadScores">重试</el-button>
      </el-alert>

      <el-table v-loading="loading" :data="scores" row-key="id" class="score-table">
        <el-table-column prop="examName" label="考试" min-width="170" />
        <el-table-column label="日期" width="118"><template #default="{ row }">{{ formatDate(row.examDate) }}</template></el-table-column>
        <el-table-column label="类型" width="110"><template #default="{ row }">{{ examTypeLabel(row.examType) }}</template></el-table-column>
        <el-table-column label="学科" min-width="120"><template #default="{ row }">{{ row.subjectName || subjectNames.get(row.subjectId) || row.subjectId }}</template></el-table-column>
        <el-table-column label="成绩" width="120"><template #default="{ row }"><strong>{{ row.score }}</strong> / {{ row.fullScore }}</template></el-table-column>
        <el-table-column label="得分率" width="100"><template #default="{ row }">{{ formatPercent(row.scoreRate) }}</template></el-table-column>
        <el-table-column label="班级排名" width="105"><template #default="{ row }">{{ row.classRank ?? '-' }}</template></el-table-column>
        <el-table-column label="年级排名" width="105"><template #default="{ row }">{{ row.gradeRank ?? '-' }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.examId)">详情</el-button>
            <el-button link type="primary" :icon="Edit" @click="openDetail(row.examId).then(openEdit)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="filtersActive ? '当前条件下无成绩' : '尚未记录考试成绩'">
            <el-button v-if="!filtersActive" type="primary" @click="openCreate">新增考试</el-button>
          </el-empty>
        </template>
      </el-table>

      <div v-if="total > pageSize" class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50]"
          @change="loadScores"
        />
      </div>

      <ExamFormDialog
        v-model="formOpen"
        :exam="formExam"
        :current-student-id="currentStudentId"
        :terms="terms"
        :subjects="subjects"
        :exam-types="examTypes"
        :saving="saving"
        :conflict="conflict"
        @submit="saveExam"
        @reload="reloadFormExam"
      />
      <ExamDetailDrawer
        v-model="detailOpen"
        :exam="detailExam"
        :loading="detailLoading"
        :subjects="subjects"
        :terms="terms"
        :exam-type-label="examTypeLabel"
        @edit="openEdit"
        @remove="removeExam"
      />
    </template>
  </section>
</template>

<style scoped>
.score-view { width: 100%; max-width: 1440px; margin: 0 auto; }
.page-toolbar, .toolbar-actions, .filters, .pagination { display: flex; align-items: center; }
.page-toolbar { justify-content: space-between; margin-bottom: 18px; }
.page-toolbar h2, .page-toolbar p { margin: 0; }
.page-toolbar h2 { font-size: 22px; }
.page-toolbar p { margin-bottom: 5px; color: var(--color-text-muted); font-size: 12px; }
.toolbar-actions { gap: 8px; }
.filters { flex-wrap: wrap; gap: 10px; padding: 12px; margin-bottom: 16px; background: var(--color-surface); border: 1px solid var(--color-border); }
.filters :deep(.el-input) { width: 165px; }
.filters :deep(.el-select) { width: 145px; }
.filters :deep(.el-date-editor) { width: 142px; }
.page-alert { margin-bottom: 16px; }
.score-table { width: 100%; border: 1px solid var(--color-border); }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>

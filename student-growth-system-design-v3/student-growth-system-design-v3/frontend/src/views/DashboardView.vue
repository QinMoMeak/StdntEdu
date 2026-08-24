<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, RefreshRight, User } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'

import type { DashboardDto } from '@/api/generated'
import { AppApiError, normalizeApiError } from '@/api/errors'
import { handleApiError } from '@/api/notifications'
import { dashboardService } from '@/api/services/dashboardService'
import { useStudentContextStore } from '@/stores/studentContext'
import { formatDate, formatDateTime, formatPercent } from '@/utils/dateTime'

const router = useRouter()
const studentStore = useStudentContextStore()
const { currentStudentId, currentStudent, students, loading: studentsLoading, initialized } = storeToRefs(studentStore)
const dashboard = ref<DashboardDto | null>(null)
const loading = ref(false)
const error = ref<AppApiError | null>(null)
let requestSequence = 0

const hasBusinessData = computed(() => {
  const value = dashboard.value
  if (!value) return false
  return Boolean(
    value.latestExam ||
      value.scoreTrends.length ||
      value.weakKnowledge.length ||
      value.dueReviews.length ||
      value.waitingResources.length ||
      value.recentStudyLogs.length ||
      value.today.studyDurationSeconds ||
      value.today.totalTaskCount,
  )
})

const metrics = computed(() => {
  const today = dashboard.value?.today
  if (!today) return []
  return [
    { label: '今日学习', value: formatDuration(today.studyDurationSeconds), detail: '已记录学习时长' },
    { label: '今日任务', value: `${today.completedTaskCount} / ${today.totalTaskCount}`, detail: '已完成 / 全部任务' },
    { label: '待复习', value: String(today.dueReviewCount), detail: `其中逾期 ${today.overdueReviewCount} 项` },
    { label: '学习资源', value: String(today.waitingResourceCount + today.learningResourceCount), detail: '待学与学习中资源' },
  ]
})

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds} 秒`
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return hours ? `${hours} 小时 ${minutes} 分` : `${minutes} 分钟`
}

async function loadDashboard(): Promise<void> {
  const studentId = currentStudentId.value
  const sequence = ++requestSequence
  dashboard.value = null
  error.value = null
  if (!studentId) return
  loading.value = true
  try {
    const result = await dashboardService.get(studentId)
    if (sequence === requestSequence && studentId === currentStudentId.value) dashboard.value = result
  } catch (caught) {
    if (sequence !== requestSequence || studentId !== currentStudentId.value) return
    error.value = await normalizeApiError(caught)
    await handleApiError(error.value)
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

watch(currentStudentId, loadDashboard, { immediate: true })
</script>

<template>
  <section class="dashboard-view" aria-labelledby="dashboard-title">
    <el-skeleton v-if="studentsLoading || (!initialized && !currentStudentId)" :rows="6" animated />

    <el-empty v-else-if="students.length === 0" description="尚未创建学生档案">
      <el-button type="primary" :icon="User" @click="router.push('/students')">创建学生</el-button>
    </el-empty>

    <el-empty v-else-if="!currentStudentId" description="请选择学生">
      <el-button type="primary" :icon="ArrowRight" @click="router.push('/students')">前往学生档案</el-button>
    </el-empty>

    <template v-else>
      <div class="dashboard-heading">
        <div>
          <p>当前学生</p>
          <h2 id="dashboard-title">{{ currentStudent?.name ?? `学生 ${currentStudentId}` }}的学习概览</h2>
        </div>
        <el-button :icon="RefreshRight" :loading="loading" @click="loadDashboard">刷新</el-button>
      </div>

      <el-skeleton v-if="loading" :rows="8" animated />
      <el-result v-else-if="error" icon="error" title="概览加载失败" :sub-title="error.message">
        <template #extra><el-button type="primary" @click="loadDashboard">重试</el-button></template>
      </el-result>

      <template v-else-if="dashboard">
        <div class="metric-grid">
          <article v-for="metric in metrics" :key="metric.label" class="metric-card">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.detail }}</small>
          </article>
        </div>

        <div class="period-line">
          统计周期：{{ formatDate(dashboard.statisticsPeriod.startDate) }} 至
          {{ formatDate(dashboard.statisticsPeriod.endDate) }}
        </div>

        <el-empty v-if="!hasBusinessData" description="暂无成绩、错题等学习数据" />

        <div v-else class="summary-grid">
          <section class="summary-section">
            <h3>最近考试</h3>
            <div v-if="dashboard.latestExam" class="latest-exam">
              <strong>{{ dashboard.latestExam.examName }}</strong>
              <span>{{ formatDate(dashboard.latestExam.examDate) }}</span>
              <b>
                {{ dashboard.latestExam.totalScore ?? '-' }} / {{ dashboard.latestExam.totalFullScore ?? '-' }}
                · {{ formatPercent(dashboard.latestExam.scoreRate) }}
              </b>
            </div>
            <el-empty v-else description="暂无考试数据" :image-size="64" />
          </section>

          <section class="summary-section">
            <h3>薄弱知识点</h3>
            <ul v-if="dashboard.weakKnowledge.length" class="summary-list">
              <li v-for="item in dashboard.weakKnowledge" :key="item.knowledgeId">
                <span>{{ item.knowledgeName }}<small>{{ item.subjectName || item.knowledgeCode }}</small></span>
                <strong>{{ item.masteryScore.toFixed(1) }}</strong>
              </li>
            </ul>
            <el-empty v-else description="暂无掌握度数据" :image-size="64" />
          </section>

          <section class="summary-section">
            <h3>到期复习</h3>
            <ul v-if="dashboard.dueReviews.length" class="summary-list">
              <li v-for="item in dashboard.dueReviews" :key="item.id">
                <span>{{ item.questionText }}<small>{{ item.subjectName }}</small></span>
                <time>{{ formatDateTime(item.nextReviewTime) }}</time>
              </li>
            </ul>
            <el-empty v-else description="暂无到期复习" :image-size="64" />
          </section>

          <section class="summary-section">
            <h3>待学资源</h3>
            <ul v-if="dashboard.waitingResources.length" class="summary-list">
              <li v-for="item in dashboard.waitingResources" :key="item.assignmentId">
                <span>{{ item.title }}<small>{{ item.resourceType }}</small></span>
                <strong>{{ item.latestProgressPercent == null ? '未开始' : `${item.latestProgressPercent}%` }}</strong>
              </li>
            </ul>
            <el-empty v-else description="暂无待学资源" :image-size="64" />
          </section>

          <section class="summary-section wide-section">
            <h3>最近学习记录</h3>
            <el-table v-if="dashboard.recentStudyLogs.length" :data="dashboard.recentStudyLogs" size="small">
              <el-table-column label="日期" width="120">
                <template #default="{ row }">{{ formatDate(row.studyDate) }}</template>
              </el-table-column>
              <el-table-column prop="subjectName" label="学科" width="120" />
              <el-table-column prop="content" label="内容" min-width="220" />
              <el-table-column label="时长" width="120">
                <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无学习日志" :image-size="64" />
          </section>
        </div>
      </template>
    </template>
  </section>
</template>

<style scoped>
.dashboard-view {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
}

.dashboard-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.dashboard-heading p,
.dashboard-heading h2 {
  margin: 0;
}

.dashboard-heading p,
.period-line,
.metric-card span,
.metric-card small,
.summary-list small {
  color: var(--color-text-muted);
}

.dashboard-heading p {
  margin-bottom: 5px;
  font-size: 12px;
}

.dashboard-heading h2 {
  font-size: 22px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 14px;
}

.metric-card {
  display: grid;
  min-height: 126px;
  padding: 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

.metric-card strong {
  align-self: center;
  font-size: 25px;
}

.metric-card small {
  align-self: end;
}

.period-line {
  margin: 14px 2px 24px;
  font-size: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 28px;
}

.summary-section {
  min-width: 0;
  padding: 20px 0 24px;
  border-top: 1px solid var(--color-border);
}

.summary-section h3 {
  margin: 0 0 16px;
  font-size: 15px;
}

.wide-section {
  grid-column: 1 / -1;
}

.latest-exam {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 7px 16px;
}

.latest-exam b {
  grid-column: 1 / -1;
}

.summary-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.summary-list li {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  border-bottom: 1px solid #edf0f2;
}

.summary-list span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-list small {
  display: block;
  margin-top: 3px;
}

.summary-list time,
.summary-list strong {
  flex: none;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(160px, 1fr));
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { Delete, Edit } from '@element-plus/icons-vue'

import type { AcademicTermDto, Exam, SubjectDto } from '@/api/generated'
import { formatDate, formatPercent } from '@/utils/dateTime'

const props = defineProps<{
  modelValue: boolean
  exam: Exam | null
  loading: boolean
  subjects: SubjectDto[]
  terms: AcademicTermDto[]
  examTypeLabel: (value: Exam['examType']) => string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: []
  remove: []
}>()

const subjectNames = computed(() => new Map(props.subjects.map((item) => [item.id, item.name])))
const termNames = computed(() =>
  new Map(props.terms.map((term) => [term.id, `${term.academicYear} ${term.semester === 'FIRST' ? '第一学期' : term.semester === 'SECOND' ? '第二学期' : '学年'}`])),
)
</script>

<template>
  <el-drawer :model-value="modelValue" title="考试详情" size="min(760px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="exam">
      <div class="detail-heading">
        <div><h3>{{ exam.examName }}</h3><p>{{ examTypeLabel(exam.examType) }} · {{ formatDate(exam.examDate) }}</p></div>
        <div class="detail-actions">
          <el-button :icon="Edit" @click="emit('edit')">编辑</el-button>
          <el-button type="danger" plain :icon="Delete" @click="emit('remove')">删除</el-button>
        </div>
      </div>
      <dl class="exam-meta">
        <div><dt>学年学期</dt><dd>{{ exam.academicTermId ? termNames.get(exam.academicTermId) ?? exam.academicTermId : '未关联' }}</dd></div>
        <div><dt>总分</dt><dd>{{ exam.totalScore ?? '-' }} / {{ exam.totalFullScore ?? '-' }}</dd></div>
        <div><dt>总得分率</dt><dd>{{ formatPercent(exam.totalScoreRate) }}</dd></div>
        <div><dt>版本</dt><dd>{{ exam.version }}</dd></div>
      </dl>

      <section v-for="subject in exam.subjects" :key="subject.subjectId" class="subject-detail">
        <div class="subject-summary">
          <h4>{{ subjectNames.get(subject.subjectId) ?? subject.subjectId }}</h4>
          <strong>{{ subject.score }} / {{ subject.fullScore }}</strong>
          <span>班级 {{ subject.classRank ?? '-' }} / {{ subject.classSize ?? '-' }}</span>
          <span>年级 {{ subject.gradeRank ?? '-' }} / {{ subject.gradeSize ?? '-' }}</span>
        </div>
        <el-table v-if="subject.knowledgeScores?.length" :data="subject.knowledgeScores" size="small">
          <el-table-column prop="knowledgeName" label="知识点" min-width="180" />
          <el-table-column label="得分" width="110"><template #default="{ row }">{{ row.score }} / {{ row.fullScore }}</template></el-table-column>
          <el-table-column label="得分率" width="100"><template #default="{ row }">{{ formatPercent(row.scoreRate) }}</template></el-table-column>
          <el-table-column label="答题" width="110"><template #default="{ row }">{{ row.correctCount }} / {{ row.questionCount }}</template></el-table-column>
          <el-table-column label="正确率" width="100"><template #default="{ row }">{{ formatPercent(row.correctRate) }}</template></el-table-column>
        </el-table>
        <el-empty v-else description="暂无知识点成绩" :image-size="52" />
      </section>
    </template>
  </el-drawer>
</template>

<style scoped>
.detail-heading, .detail-actions, .subject-summary { display: flex; align-items: center; }
.detail-heading { justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.detail-heading h3, .detail-heading p { margin: 0; }
.detail-heading h3 { font-size: 20px; }
.detail-heading p { margin-top: 5px; color: var(--color-text-muted); }
.detail-actions { gap: 8px; }
.exam-meta { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0 0 24px; border: 1px solid var(--color-border); }
.exam-meta div { padding: 14px; }
.exam-meta div + div { border-left: 1px solid var(--color-border); }
.exam-meta dt { color: var(--color-text-muted); font-size: 12px; }
.exam-meta dd { margin: 6px 0 0; font-weight: 600; }
.subject-detail { padding: 18px 0; border-top: 1px solid var(--color-border); }
.subject-summary { gap: 20px; margin-bottom: 12px; }
.subject-summary h4 { min-width: 100px; margin: 0; }
.subject-summary span { color: var(--color-text-muted); font-size: 12px; }
</style>

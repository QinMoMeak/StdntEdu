<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Edit, Plus, RefreshRight, Select } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'

import StudentFormDialog from '@/components/StudentFormDialog.vue'
import type { GradeDto, StageDto, Student, StudentCreate, StudentUpdate } from '@/api/generated'
import { AppApiError, normalizeApiError } from '@/api/errors'
import { handleApiError } from '@/api/notifications'
import { studentService } from '@/api/services/studentService'
import { useStudentContextStore } from '@/stores/studentContext'
import { formatDate } from '@/utils/dateTime'

const studentStore = useStudentContextStore()
const { students, currentStudentId, loading } = storeToRefs(studentStore)
const stages = ref<StageDto[]>([])
const grades = ref<GradeDto[]>([])
const loadError = ref<AppApiError | null>(null)
const dialogOpen = ref(false)
const editingStudent = ref<Student | null>(null)
const saving = ref(false)
const conflict = ref(false)

const stageNames = computed(() => new Map(stages.value.map((item) => [item.id, item.name])))
const gradeNames = computed(() => new Map(grades.value.map((item) => [item.id, item.name])))

async function refresh(): Promise<void> {
  loadError.value = null
  try {
    const [, stageList, gradeList] = await Promise.all([
      studentStore.loadStudents(),
      studentService.listStages(),
      studentService.listGrades(),
    ])
    stages.value = stageList
    grades.value = gradeList
  } catch (error) {
    loadError.value = await normalizeApiError(error)
    await handleApiError(loadError.value)
  }
}

function openCreate(): void {
  editingStudent.value = null
  conflict.value = false
  dialogOpen.value = true
}

async function openEdit(student: Student): Promise<void> {
  try {
    editingStudent.value = await studentService.get(student.id)
    conflict.value = false
    dialogOpen.value = true
  } catch (error) {
    await handleApiError(error)
  }
}

async function saveStudent(value: StudentCreate | StudentUpdate): Promise<void> {
  saving.value = true
  conflict.value = false
  try {
    const saved = editingStudent.value
      ? await studentService.update(editingStudent.value.id, value as StudentUpdate)
      : await studentService.create(value as StudentCreate)
    studentStore.replaceStudent(saved)
    if (!currentStudentId.value) studentStore.selectStudent(saved)
    await studentStore.loadStudents()
    dialogOpen.value = false
    ElMessage.success(editingStudent.value ? '学生档案已更新' : '学生档案已创建')
  } catch (error) {
    const normalized = await normalizeApiError(error)
    if (normalized.errorCode === 'DATA_VERSION_CONFLICT') {
      conflict.value = true
      ElMessage.error('学生档案已被其他操作更新，请重新加载后再修改。')
    } else await handleApiError(normalized)
  } finally {
    saving.value = false
  }
}

async function reloadEditingStudent(): Promise<void> {
  if (!editingStudent.value) return
  try {
    editingStudent.value = await studentService.get(editingStudent.value.id)
    conflict.value = false
  } catch (error) {
    await handleApiError(error)
  }
}

onMounted(refresh)
</script>

<template>
  <section class="page-view" aria-labelledby="students-title">
    <div class="page-toolbar">
      <div>
        <h2 id="students-title">学生档案</h2>
        <p>管理学习档案主体，并明确选择当前查看的学生。</p>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="RefreshRight" :loading="loading" @click="refresh">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">创建学生</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon class="page-alert">
      <template #title>{{ loadError.message }}</template>
      <el-button link type="primary" @click="refresh">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="students" row-key="id" class="student-table">
      <el-table-column label="学生" min-width="180">
        <template #default="{ row }: { row: Student }">
          <div class="student-name">
            <strong>{{ row.name }}</strong>
            <el-tag v-if="row.id === currentStudentId" type="success" size="small">当前学生</el-tag>
          </div>
          <small>{{ row.studentCode }}</small>
        </template>
      </el-table-column>
      <el-table-column label="学段 / 年级" min-width="150">
        <template #default="{ row }: { row: Student }">
          {{ stageNames.get(row.currentStageId) ?? row.currentStageId }} /
          {{ gradeNames.get(row.currentGradeId) ?? row.currentGradeId }}
        </template>
      </el-table-column>
      <el-table-column prop="school" label="学校" min-width="160">
        <template #default="{ row }: { row: Student }">{{ row.school || '-' }}</template>
      </el-table-column>
      <el-table-column label="生日" width="125">
        <template #default="{ row }: { row: Student }">{{ formatDate(row.birthday) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" align="right">
        <template #default="{ row }: { row: Student }">
          <el-button :icon="Edit" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.id !== currentStudentId"
            :icon="Select"
            link
            type="primary"
            @click="studentStore.selectStudent(row)"
          >
            设为当前
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="尚未创建学生档案">
          <el-button type="primary" @click="openCreate">创建学生</el-button>
        </el-empty>
      </template>
    </el-table>

    <StudentFormDialog
      v-model="dialogOpen"
      :student="editingStudent"
      :stages="stages"
      :grades="grades"
      :saving="saving"
      :conflict="conflict"
      @submit="saveStudent"
      @reload="reloadEditingStudent"
    />
  </section>
</template>

<style scoped>
.page-view {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
}

.page-toolbar,
.toolbar-actions,
.student-name {
  display: flex;
  align-items: center;
}

.page-toolbar {
  justify-content: space-between;
  margin-bottom: 22px;
}

.page-toolbar h2 {
  margin: 0;
  font-size: 22px;
}

.page-toolbar p {
  margin: 6px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.toolbar-actions,
.student-name {
  gap: 8px;
}

.student-table {
  width: 100%;
  border: 1px solid var(--color-border);
}

.student-name strong {
  font-size: 14px;
}

.student-table small {
  color: var(--color-text-muted);
}

.page-alert {
  margin-bottom: 18px;
}
</style>

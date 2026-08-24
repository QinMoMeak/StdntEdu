<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type { GradeDto, StageDto, Student, StudentCreate, StudentUpdate } from '@/api/generated'
import { businessDateToApiDate, formatDate } from '@/utils/dateTime'

interface StudentFormModel {
  name: string
  birthday: string
  school: string
  currentStageId: string
  currentGradeId: string
  remark: string
}

const props = defineProps<{
  modelValue: boolean
  student: Student | null
  stages: StageDto[]
  grades: GradeDto[]
  saving: boolean
  conflict: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [value: StudentCreate | StudentUpdate]
  reload: []
}>()

const formRef = ref<FormInstance>()
const form = reactive<StudentFormModel>({
  name: '',
  birthday: '',
  school: '',
  currentStageId: '',
  currentGradeId: '',
  remark: '',
})
const rules: FormRules<StudentFormModel> = {
  name: [{ required: true, message: '请输入学生姓名', trigger: 'blur' }],
  currentStageId: [{ required: true, message: '请选择学段', trigger: 'change' }],
  currentGradeId: [{ required: true, message: '请选择年级', trigger: 'change' }],
}
const filteredGrades = computed(() => props.grades.filter((grade) => grade.stageId === form.currentStageId))

watch(
  () => [props.modelValue, props.student] as const,
  ([open, student]) => {
    if (!open) return
    Object.assign(form, {
      name: student?.name ?? '',
      birthday: student?.birthday ? formatDate(student.birthday) : '',
      school: student?.school ?? '',
      currentStageId: student?.currentStageId ?? '',
      currentGradeId: student?.currentGradeId ?? '',
      remark: student?.remark ?? '',
    })
    formRef.value?.clearValidate()
  },
  { immediate: true },
)

function changeStage(): void {
  if (!filteredGrades.value.some((grade) => grade.id === form.currentGradeId)) form.currentGradeId = ''
}

async function submit(): Promise<void> {
  if (!(await formRef.value?.validate())) return
  const common = {
    name: form.name.trim(),
    birthday: form.birthday ? businessDateToApiDate(form.birthday) : undefined,
    school: form.school.trim() || undefined,
    currentStageId: form.currentStageId,
    currentGradeId: form.currentGradeId,
    remark: form.remark.trim() || undefined,
  }
  emit('submit', props.student ? { ...common, version: props.student.version } : common)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="student ? '编辑学生档案' : '创建学生档案'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="conflict" type="warning" :closable="false" show-icon class="conflict-alert">
      <template #title>学生档案已被其他操作更新，请重新加载后再修改。</template>
      <el-button link type="primary" @click="emit('reload')">重新加载</el-button>
    </el-alert>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="生日" prop="birthday">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="学段" prop="currentStageId">
          <el-select v-model="form.currentStageId" placeholder="选择学段" @change="changeStage">
            <el-option v-for="stage in stages" :key="stage.id" :label="stage.name" :value="stage.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级" prop="currentGradeId">
          <el-select v-model="form.currentGradeId" placeholder="选择年级" :disabled="!form.currentStageId">
            <el-option v-for="grade in filteredGrades" :key="grade.id" :label="grade.name" :value="grade.id" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="学校" prop="school">
        <el-input v-model="form.school" maxlength="128" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.form-grid :deep(.el-date-editor),
.form-grid :deep(.el-select) {
  width: 100%;
}

.conflict-alert {
  margin-bottom: 18px;
}
</style>

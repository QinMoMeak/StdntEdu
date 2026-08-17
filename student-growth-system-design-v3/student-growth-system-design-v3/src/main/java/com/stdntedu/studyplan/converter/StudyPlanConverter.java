package com.stdntedu.studyplan.converter;

import java.time.ZoneId;
import java.util.List;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.StudyPlanDto;
import com.stdntedu.generated.model.StudyPlanTaskDto;
import com.stdntedu.generated.model.StudyPlanTaskStatus;
import com.stdntedu.studyplan.entity.StudyPlanEntity;
import com.stdntedu.studyplan.entity.StudyPlanTaskEntity;
import org.springframework.stereotype.Component;

@Component
public class StudyPlanConverter {
    private final IdConverter ids;

    public StudyPlanConverter(IdConverter ids) {
        this.ids = ids;
    }

    public StudyPlanDto toDto(StudyPlanEntity plan, List<StudyPlanTaskEntity> tasks, ZoneId zone) {
        List<StudyPlanTaskDto> taskDtos = tasks.stream().map(task -> toDto(task, zone)).toList();
        int completed = (int) tasks.stream()
                .filter(task -> task.getStatus() == StudyPlanTaskStatus.COMPLETED).count();
        return new StudyPlanDto()
                .id(ids.toString(plan.getId()))
                .studentId(ids.toString(plan.getStudentId()))
                .title(plan.getTitle())
                .planType(plan.getPlanType())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .sourceAnalysisId(ids.toString(plan.getSourceAnalysisId()))
                .dailyAvailableMinutes(plan.getDailyAvailableMinutes())
                .description(plan.getDescription())
                .tasks(taskDtos)
                .totalTaskCount(tasks.size())
                .completedTaskCount(completed)
                .version(plan.getVersion())
                .createdAt(plan.getCreateTime().atZone(zone).toOffsetDateTime())
                .updatedAt(plan.getUpdateTime().atZone(zone).toOffsetDateTime());
    }

    public StudyPlanTaskDto toDto(StudyPlanTaskEntity task, ZoneId zone) {
        return new StudyPlanTaskDto()
                .id(ids.toString(task.getId()))
                .studyPlanId(ids.toString(task.getStudyPlanId()))
                .taskDate(task.getTaskDate())
                .taskType(task.getTaskType())
                .title(task.getTitle())
                .resourceId(ids.toString(task.getResourceId()))
                .wrongQuestionId(ids.toString(task.getWrongQuestionId()))
                .knowledgeId(ids.toString(task.getKnowledgeId()))
                .examId(ids.toString(task.getExamId()))
                .expectedDurationSeconds(task.getExpectedDurationSeconds())
                .actualDurationSeconds(task.getActualDurationSeconds())
                .status(task.getStatus())
                .completedTime(task.getCompletedTime() == null ? null
                        : task.getCompletedTime().atZone(zone).toOffsetDateTime())
                .sortOrder(task.getSortOrder())
                .remark(task.getRemark())
                .version(task.getVersion())
                .createdAt(task.getCreateTime().atZone(zone).toOffsetDateTime())
                .updatedAt(task.getUpdateTime().atZone(zone).toOffsetDateTime());
    }
}

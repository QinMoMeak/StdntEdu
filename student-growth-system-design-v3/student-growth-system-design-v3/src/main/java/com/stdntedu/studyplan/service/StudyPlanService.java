package com.stdntedu.studyplan.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.CompleteStudyPlanTaskRequest;
import com.stdntedu.generated.model.SkipStudyPlanTaskRequest;
import com.stdntedu.generated.model.StudyPlanCreateRequest;
import com.stdntedu.generated.model.StudyPlanDto;
import com.stdntedu.generated.model.StudyPlanPageResponseAllOfData;
import com.stdntedu.generated.model.StudyPlanStatus;
import com.stdntedu.generated.model.StudyPlanStatusChangeRequest;
import com.stdntedu.generated.model.StudyPlanTaskCreateRequest;
import com.stdntedu.generated.model.StudyPlanTaskDto;
import com.stdntedu.generated.model.StudyPlanTaskPageResponseAllOfData;
import com.stdntedu.generated.model.StudyPlanTaskStatus;
import com.stdntedu.generated.model.StudyPlanTaskType;
import com.stdntedu.generated.model.StudyPlanTaskUpdateRequest;
import com.stdntedu.generated.model.StudyPlanUpdateRequest;
import com.stdntedu.resource.entity.LearningResourceEntity;
import com.stdntedu.resource.mapper.LearningResourceMapper;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.score.entity.ExamEntity;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.mapper.ExamMapper;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import com.stdntedu.student.mapper.StudentMapper;
import com.stdntedu.studyplan.converter.StudyPlanConverter;
import com.stdntedu.studyplan.entity.StudyPlanActionHistoryEntity;
import com.stdntedu.studyplan.entity.StudyPlanEntity;
import com.stdntedu.studyplan.entity.StudyPlanTaskEntity;
import com.stdntedu.studyplan.mapper.StudyPlanActionHistoryMapper;
import com.stdntedu.studyplan.mapper.StudyPlanMapper;
import com.stdntedu.studyplan.mapper.StudyPlanQueryMapper;
import com.stdntedu.studyplan.mapper.StudyPlanTaskMapper;
import com.stdntedu.wrongquestion.entity.WrongQuestionEntity;
import com.stdntedu.wrongquestion.mapper.WrongQuestionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanService {
    private static final EnumSet<StudyPlanStatus> WRITABLE_PLAN_STATUSES = EnumSet.of(
            StudyPlanStatus.DRAFT, StudyPlanStatus.ACTIVE, StudyPlanStatus.PAUSED);
    private static final EnumSet<StudyPlanTaskStatus> WRITABLE_TASK_STATUSES = EnumSet.of(
            StudyPlanTaskStatus.TODO, StudyPlanTaskStatus.IN_PROGRESS);

    private final StudyPlanMapper plans;
    private final StudyPlanTaskMapper tasks;
    private final StudyPlanActionHistoryMapper histories;
    private final StudyPlanQueryMapper queries;
    private final StudentMapper students;
    private final WrongQuestionMapper wrongQuestions;
    private final LearningResourceMapper resources;
    private final KnowledgeNodeReferenceMapper knowledgeNodes;
    private final ExamMapper exams;
    private final StudyPlanConverter converter;
    private final SystemTimezoneProvider timezone;
    private final IdConverter ids;

    public StudyPlanService(StudyPlanMapper plans, StudyPlanTaskMapper tasks,
            StudyPlanActionHistoryMapper histories, StudyPlanQueryMapper queries, StudentMapper students,
            WrongQuestionMapper wrongQuestions, LearningResourceMapper resources,
            KnowledgeNodeReferenceMapper knowledgeNodes, ExamMapper exams, StudyPlanConverter converter,
            SystemTimezoneProvider timezone, IdConverter ids) {
        this.plans = plans;
        this.tasks = tasks;
        this.histories = histories;
        this.queries = queries;
        this.students = students;
        this.wrongQuestions = wrongQuestions;
        this.resources = resources;
        this.knowledgeNodes = knowledgeNodes;
        this.exams = exams;
        this.converter = converter;
        this.timezone = timezone;
        this.ids = ids;
    }

    @Transactional
    public StudyPlanDto create(StudyPlanCreateRequest request) {
        Long studentId = ids.toLong(request.getStudentId());
        requireStudent(studentId);
        validateDateRange(request.getStartDate(), request.getEndDate());

        StudyPlanEntity plan = new StudyPlanEntity();
        plan.setStudentId(studentId);
        plan.setTitle(request.getTitle());
        plan.setPlanType(request.getPlanType());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setStatus(StudyPlanStatus.DRAFT);
        plan.setDailyAvailableMinutes(request.getDailyAvailableMinutes());
        plan.setDescription(request.getDescription());
        plan.setDeleted(false);
        plan.setVersion(1);
        plans.insert(plan);

        for (StudyPlanTaskCreateRequest item : safe(request.getTasks())) {
            StudyPlanTaskEntity task = newTask(plan, item);
            tasks.insert(task);
        }
        return getById(plan.getId());
    }

    @Transactional(readOnly = true)
    public StudyPlanDto get(String planId) {
        return getById(ids.toLong(planId));
    }

    @Transactional(readOnly = true)
    public StudyPlanPageResponseAllOfData list(String studentId, StudyPlanStatus status, String planType,
            LocalDate startDate, LocalDate endDate, int page, int pageSize) {
        Long studentKey = ids.toLong(studentId);
        requireStudent(studentKey);
        validateOptionalDateRange(startDate, endDate);
        long total = queries.countPlans(studentKey, status, planType, startDate, endDate);
        List<StudyPlanEntity> pageRows = queries.selectPlanPage(studentKey, status, planType, startDate, endDate,
                (long) (page - 1) * pageSize, pageSize);
        Map<Long, List<StudyPlanTaskEntity>> grouped = groupTasks(pageRows);
        ZoneId zone = timezone.get();
        List<StudyPlanDto> items = pageRows.stream()
                .map(plan -> converter.toDto(plan, grouped.getOrDefault(plan.getId(), List.of()), zone)).toList();
        return new StudyPlanPageResponseAllOfData().page(page).pageSize(pageSize).total(total)
                .totalPages(totalPages(total, pageSize)).items(items);
    }

    @Transactional
    public StudyPlanDto update(String planId, StudyPlanUpdateRequest request) {
        Long id = ids.toLong(planId);
        StudyPlanEntity current = requirePlan(id);
        requireVersion(current.getVersion(), request.getVersion(), "study plan");
        validateDateRange(request.getStartDate(), request.getEndDate());
        if (queries.countTasksOutsideRange(id, request.getStartDate(), request.getEndDate()) > 0) {
            throw rule("study plan date range excludes an existing task");
        }
        int changed = plans.updateMetadataWithVersion(id, request.getTitle(), request.getPlanType(),
                request.getStartDate(), request.getEndDate(), request.getDailyAvailableMinutes(),
                request.getDescription(), request.getVersion());
        if (changed == 0) throwAfterPlanWriteFailure(id);
        return getById(id);
    }

    @Transactional
    public void delete(String planId) {
        Long id = ids.toLong(planId);
        requirePlan(id);
        if (plans.deleteById(id) == 0) throw planNotFound();
    }

    @Transactional
    public StudyPlanDto activate(String planId, StudyPlanStatusChangeRequest request) {
        return transitionPlan(planId, request, StudyPlanStatus.ACTIVE, "PLAN_ACTIVATE");
    }

    @Transactional
    public StudyPlanDto pause(String planId, StudyPlanStatusChangeRequest request) {
        return transitionPlan(planId, request, StudyPlanStatus.PAUSED, "PLAN_PAUSE");
    }

    @Transactional
    public StudyPlanDto complete(String planId, StudyPlanStatusChangeRequest request) {
        return transitionPlan(planId, request, StudyPlanStatus.COMPLETED, "PLAN_COMPLETE");
    }

    @Transactional
    public StudyPlanDto cancel(String planId, StudyPlanStatusChangeRequest request) {
        return transitionPlan(planId, request, StudyPlanStatus.CANCELLED, "PLAN_CANCEL");
    }

    @Transactional(readOnly = true)
    public StudyPlanTaskPageResponseAllOfData listTasks(String planId, LocalDate taskDate,
            StudyPlanTaskStatus status, StudyPlanTaskType taskType, int page, int pageSize) {
        Long planKey = ids.toLong(planId);
        requirePlan(planKey);
        long total = queries.countTasks(planKey, taskDate, status, taskType);
        ZoneId zone = timezone.get();
        List<StudyPlanTaskDto> items = queries.selectTaskPage(planKey, taskDate, status, taskType,
                (long) (page - 1) * pageSize, pageSize).stream().map(task -> converter.toDto(task, zone)).toList();
        return new StudyPlanTaskPageResponseAllOfData().page(page).pageSize(pageSize).total(total)
                .totalPages(totalPages(total, pageSize)).items(items);
    }

    @Transactional
    public StudyPlanTaskDto createTask(String planId, StudyPlanTaskCreateRequest request) {
        StudyPlanEntity plan = requireWritablePlan(ids.toLong(planId));
        StudyPlanTaskEntity task = newTask(plan, request);
        tasks.insert(task);
        return taskDto(task.getId(), plan.getId());
    }

    @Transactional
    public StudyPlanTaskDto updateTask(String planId, String taskId, StudyPlanTaskUpdateRequest request) {
        StudyPlanEntity plan = requireWritablePlan(ids.toLong(planId));
        Long taskKey = ids.toLong(taskId);
        StudyPlanTaskEntity current = requireTask(taskKey, plan.getId());
        requireVersion(current.getVersion(), request.getVersion(), "study plan task");
        if (!WRITABLE_TASK_STATUSES.contains(current.getStatus())) throw rule("terminal task cannot be updated");
        StudyPlanTaskStatus target = request.getStatus() == null ? current.getStatus() : request.getStatus();
        if (!isNormalTaskTransition(current.getStatus(), target)) {
            throw rule("invalid task status transition");
        }
        validateTaskDate(plan, request.getTaskDate());
        TaskLinks links = validateLinks(plan, request.getTaskType(), request.getResourceId(),
                request.getWrongQuestionId(), request.getKnowledgeId(), request.getExamId());
        int changed = tasks.updateWithVersion(taskKey, plan.getId(), request.getTaskDate(), request.getTaskType(),
                request.getTitle(), links.resourceId(), links.wrongQuestionId(), links.knowledgeId(), links.examId(),
                request.getExpectedDurationSeconds(), defaultSortOrder(request.getSortOrder()), request.getRemark(),
                current.getStatus(), target, request.getVersion());
        if (changed == 0) throwAfterTaskWriteFailure(taskKey, plan.getId(), request.getVersion());
        return taskDto(taskKey, plan.getId());
    }

    @Transactional
    public StudyPlanTaskDto completeTask(String planId, String taskId, CompleteStudyPlanTaskRequest request) {
        if (request.getActualDurationSeconds() != null && request.getActualDurationSeconds() < 0) {
            throw rule("actualDurationSeconds must be greater than or equal to zero");
        }
        return transitionTask(planId, taskId, request.getVersion(), StudyPlanTaskStatus.COMPLETED,
                request.getActualDurationSeconds(), LocalDateTime.now(timezone.get()), "TASK_COMPLETE", null,
                request.getNote());
    }

    @Transactional
    public StudyPlanTaskDto skipTask(String planId, String taskId, SkipStudyPlanTaskRequest request) {
        return transitionTask(planId, taskId, request.getVersion(), StudyPlanTaskStatus.SKIPPED,
                null, null, "TASK_SKIP", request.getReason(), null);
    }

    private StudyPlanDto transitionPlan(String planId, StudyPlanStatusChangeRequest request,
            StudyPlanStatus target, String actionType) {
        Long id = ids.toLong(planId);
        StudyPlanEntity current = requirePlan(id);
        requireVersion(current.getVersion(), request.getVersion(), "study plan");
        if (!isPlanTransitionAllowed(current.getStatus(), target)) throw rule("invalid study plan status transition");
        if (plans.transitionWithVersion(id, request.getVersion(), current.getStatus(), target) == 0) {
            throwAfterPlanWriteFailure(id);
        }
        insertHistory(id, null, actionType, current.getStatus().getValue(), target.getValue(), request.getReason(),
                null, request.getVersion(), request.getVersion() + 1);
        return getById(id);
    }

    private StudyPlanTaskDto transitionTask(String planId, String taskId, Integer version,
            StudyPlanTaskStatus target, Integer actualDurationSeconds, LocalDateTime completedTime,
            String actionType, String reason, String note) {
        StudyPlanEntity plan = requireWritablePlan(ids.toLong(planId));
        Long taskKey = ids.toLong(taskId);
        StudyPlanTaskEntity current = requireTask(taskKey, plan.getId());
        requireVersion(current.getVersion(), version, "study plan task");
        if (!WRITABLE_TASK_STATUSES.contains(current.getStatus())) throw rule("terminal task cannot transition");
        if (tasks.transitionWithVersion(taskKey, plan.getId(), version, current.getStatus(), target,
                actualDurationSeconds, completedTime) == 0) {
            throwAfterTaskWriteFailure(taskKey, plan.getId(), version);
        }
        insertHistory(plan.getId(), taskKey, actionType, current.getStatus().getValue(), target.getValue(), reason,
                note, version, version + 1);
        return taskDto(taskKey, plan.getId());
    }

    private StudyPlanTaskEntity newTask(StudyPlanEntity plan, StudyPlanTaskCreateRequest request) {
        validateTaskDate(plan, request.getTaskDate());
        TaskLinks links = validateLinks(plan, request.getTaskType(), request.getResourceId(),
                request.getWrongQuestionId(), request.getKnowledgeId(), request.getExamId());
        StudyPlanTaskEntity task = new StudyPlanTaskEntity();
        task.setStudyPlanId(plan.getId());
        task.setTaskDate(request.getTaskDate());
        task.setTaskType(request.getTaskType());
        task.setTitle(request.getTitle());
        task.setResourceId(links.resourceId());
        task.setWrongQuestionId(links.wrongQuestionId());
        task.setKnowledgeId(links.knowledgeId());
        task.setExamId(links.examId());
        task.setExpectedDurationSeconds(request.getExpectedDurationSeconds());
        task.setActualDurationSeconds(null);
        task.setStatus(StudyPlanTaskStatus.TODO);
        task.setCompletedTime(null);
        task.setSortOrder(defaultSortOrder(request.getSortOrder()));
        task.setRemark(request.getRemark());
        task.setVersion(1);
        return task;
    }

    private TaskLinks validateLinks(StudyPlanEntity plan, StudyPlanTaskType type, String resourceId,
            String wrongQuestionId, String knowledgeId, String examId) {
        if (type == null) throw rule("taskType is required");
        int supplied = countPresent(resourceId, wrongQuestionId, knowledgeId, examId);
        return switch (type) {
            case WRONG_QUESTION_REVIEW -> {
                if (supplied != 1 || wrongQuestionId == null) throw rule("invalid wrong-question task links");
                Long id = ids.toLong(wrongQuestionId);
                WrongQuestionEntity item = wrongQuestions.selectById(id);
                if (item == null || !item.getStudentId().equals(plan.getStudentId())) {
                    throw rule("wrong question does not belong to the plan student");
                }
                yield new TaskLinks(null, id, null, null);
            }
            case RESOURCE_LEARNING -> {
                if (supplied != 1 || resourceId == null) throw rule("invalid resource task links");
                Long id = ids.toLong(resourceId);
                LearningResourceEntity item = resources.selectById(id);
                if (item == null) throw rule("resource does not exist");
                yield new TaskLinks(id, null, null, null);
            }
            case KNOWLEDGE_PRACTICE -> {
                if (supplied != 1 || knowledgeId == null) throw rule("invalid knowledge task links");
                Long id = ids.toLong(knowledgeId);
                KnowledgeNodeReferenceEntity item = knowledgeNodes.selectById(id);
                if (item == null || !Boolean.TRUE.equals(item.getEnabled())) throw rule("knowledge node is unavailable");
                yield new TaskLinks(null, null, id, null);
            }
            case EXAM_REVIEW -> {
                if (supplied != 1 || examId == null) throw rule("invalid exam task links");
                Long id = ids.toLong(examId);
                ExamEntity item = exams.selectById(id);
                if (item == null || !item.getStudentId().equals(plan.getStudentId())) {
                    throw rule("exam does not belong to the plan student");
                }
                yield new TaskLinks(null, null, null, id);
            }
            case READING, OTHER -> {
                if (supplied != 0) throw rule("reading and other tasks cannot reference business entities");
                yield new TaskLinks(null, null, null, null);
            }
        };
    }

    private StudyPlanDto getById(Long planId) {
        StudyPlanEntity plan = requirePlan(planId);
        List<StudyPlanTaskEntity> planTasks = queries.selectTasksForPlans(List.of(planId));
        return converter.toDto(plan, planTasks, timezone.get());
    }

    private StudyPlanTaskDto taskDto(Long taskId, Long planId) {
        return converter.toDto(requireTask(taskId, planId), timezone.get());
    }

    private Map<Long, List<StudyPlanTaskEntity>> groupTasks(List<StudyPlanEntity> pageRows) {
        Map<Long, List<StudyPlanTaskEntity>> grouped = new HashMap<>();
        if (pageRows.isEmpty()) return grouped;
        List<Long> planIds = pageRows.stream().map(StudyPlanEntity::getId).toList();
        for (StudyPlanTaskEntity task : queries.selectTasksForPlans(planIds)) {
            grouped.computeIfAbsent(task.getStudyPlanId(), ignored -> new ArrayList<>()).add(task);
        }
        return grouped;
    }

    private StudyPlanEntity requirePlan(Long id) {
        StudyPlanEntity plan = plans.selectById(id);
        if (plan == null) throw planNotFound();
        return plan;
    }

    private StudyPlanEntity requireWritablePlan(Long id) {
        StudyPlanEntity plan = requirePlan(id);
        if (!WRITABLE_PLAN_STATUSES.contains(plan.getStatus())) throw rule("terminal study plan cannot change tasks");
        return plan;
    }

    private StudyPlanTaskEntity requireTask(Long taskId, Long planId) {
        StudyPlanTaskEntity task = tasks.selectById(taskId);
        if (task == null || !task.getStudyPlanId().equals(planId)) throw taskNotFound();
        return task;
    }

    private void requireStudent(Long studentId) {
        if (students.selectById(studentId) == null) throw new ResourceNotFoundException("student not found");
    }

    private void insertHistory(Long planId, Long taskId, String actionType, String fromStatus, String toStatus,
            String reason, String note, Integer versionBefore, Integer versionAfter) {
        if (reason != null && reason.length() > 512) throw rule("reason exceeds 512 characters");
        if (note != null && note.length() > 512) throw rule("note exceeds 512 characters");
        StudyPlanActionHistoryEntity history = new StudyPlanActionHistoryEntity();
        history.setStudyPlanId(planId);
        history.setStudyPlanTaskId(taskId);
        history.setActionType(actionType);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setReason(reason);
        history.setNote(note);
        history.setVersionBefore(versionBefore);
        history.setVersionAfter(versionAfter);
        histories.insert(history);
    }

    private boolean isPlanTransitionAllowed(StudyPlanStatus current, StudyPlanStatus target) {
        return switch (current) {
            case DRAFT -> target == StudyPlanStatus.ACTIVE || target == StudyPlanStatus.CANCELLED;
            case ACTIVE -> target == StudyPlanStatus.PAUSED || target == StudyPlanStatus.COMPLETED
                    || target == StudyPlanStatus.CANCELLED;
            case PAUSED -> target == StudyPlanStatus.ACTIVE || target == StudyPlanStatus.COMPLETED
                    || target == StudyPlanStatus.CANCELLED;
            case COMPLETED, CANCELLED, EXPIRED -> false;
        };
    }

    private boolean isNormalTaskTransition(StudyPlanTaskStatus current, StudyPlanTaskStatus target) {
        if (!WRITABLE_TASK_STATUSES.contains(current)) return false;
        return target == StudyPlanTaskStatus.TODO || target == StudyPlanTaskStatus.IN_PROGRESS
                || target == StudyPlanTaskStatus.CANCELLED;
    }

    private void validateTaskDate(StudyPlanEntity plan, LocalDate taskDate) {
        if (taskDate == null || taskDate.isBefore(plan.getStartDate()) || taskDate.isAfter(plan.getEndDate())) {
            throw rule("taskDate must be within the study plan date range");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw rule("startDate must not be after endDate");
        }
    }

    private void validateOptionalDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw rule("startDate must not be after endDate");
        }
    }

    private void requireVersion(Integer actual, Integer supplied, String target) {
        if (supplied == null || !supplied.equals(actual)) throw versionConflict(target);
    }

    private void throwAfterPlanWriteFailure(Long id) {
        StudyPlanEntity latest = plans.selectById(id);
        if (latest == null) throw planNotFound();
        throw versionConflict("study plan");
    }

    private void throwAfterTaskWriteFailure(Long taskId, Long planId, Integer version) {
        StudyPlanTaskEntity latest = tasks.selectById(taskId);
        if (latest == null || !latest.getStudyPlanId().equals(planId)) throw taskNotFound();
        // All business-state checks happened before the conditional update. A zero-row result is a concurrent write.
        throw versionConflict("study plan task");
    }

    private int countPresent(String... values) {
        int count = 0;
        for (String value : values) if (value != null) count++;
        return count;
    }

    private int defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private int totalPages(long total, int pageSize) {
        return (int) ((total + pageSize - 1) / pageSize);
    }

    private List<StudyPlanTaskCreateRequest> safe(List<StudyPlanTaskCreateRequest> values) {
        return values == null ? List.of() : values;
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException versionConflict(String target) {
        return new BusinessException("DATA_VERSION_CONFLICT", target + " version conflict", HttpStatus.CONFLICT);
    }

    private ResourceNotFoundException planNotFound() {
        return new ResourceNotFoundException("study plan not found");
    }

    private ResourceNotFoundException taskNotFound() {
        return new ResourceNotFoundException("study plan task not found");
    }

    private record TaskLinks(Long resourceId, Long wrongQuestionId, Long knowledgeId, Long examId) { }
}

package com.stdntedu.stage8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.CompleteStudyPlanTaskRequest;
import com.stdntedu.generated.model.Resource;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.SkipStudyPlanTaskRequest;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudyPlanCreateRequest;
import com.stdntedu.generated.model.StudyPlanDto;
import com.stdntedu.generated.model.StudyPlanStatus;
import com.stdntedu.generated.model.StudyPlanStatusChangeRequest;
import com.stdntedu.generated.model.StudyPlanTaskCreateRequest;
import com.stdntedu.generated.model.StudyPlanTaskDto;
import com.stdntedu.generated.model.StudyPlanTaskStatus;
import com.stdntedu.generated.model.StudyPlanTaskType;
import com.stdntedu.generated.model.StudyPlanTaskUpdateRequest;
import com.stdntedu.generated.model.StudyPlanUpdateRequest;
import com.stdntedu.resource.service.LearningResourceService;
import com.stdntedu.student.service.StudentService;
import com.stdntedu.studyplan.service.StudyPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StageEightB2IntegrationTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired StudyPlanService plans;
    @Autowired StudentService students;
    @Autowired LearningResourceService resources;
    @Autowired SubjectMapper subjects;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void scenarios01_03_createPlanUsesDraftVersionOneAndStringIds() {
        StudyPlanDto plan = createPlan(student(), List.of());
        assertThat(plan.getStatus()).isEqualTo(StudyPlanStatus.DRAFT);
        assertThat(plan.getVersion()).isEqualTo(1);
        assertThat(plan.getId()).matches("[0-9]+");
        assertThat(plan.getStudentId()).matches("[0-9]+");
        assertThat(plan.getTasks()).isEmpty();
    }

    @Test void scenarios04_05_createRejectsMissingStudentAndInvalidDates() {
        assertThatThrownBy(() -> plans.create(createRequest("999999", List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
        StudyPlanCreateRequest invalid = createRequest(student(), List.of()).startDate(END).endDate(START);
        assertRule(() -> plans.create(invalid));
    }

    @Test void scenarios06_10_initialTasksAreAtomicTodoAndVersionOne() {
        String student = student();
        StudyPlanTaskCreateRequest first = task(START, StudyPlanTaskType.READING, "read");
        StudyPlanTaskCreateRequest second = task(START.plusDays(1), StudyPlanTaskType.OTHER, "other");
        StudyPlanDto created = createPlan(student, List.of(first, second));
        assertThat(created.getTasks()).hasSize(2).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(StudyPlanTaskStatus.TODO);
            assertThat(task.getVersion()).isEqualTo(1);
            assertThat(task.getCompletedTime()).isNull();
            assertThat(task.getActualDurationSeconds()).isNull();
        });
        int before = countPlans(student);
        StudyPlanTaskCreateRequest invalid = task(END.plusDays(1), StudyPlanTaskType.READING, "outside");
        assertRule(() -> createPlan(student, List.of(first, invalid)));
        assertThat(countPlans(student)).isEqualTo(before);
    }

    @Test void scenarios11_22_allTaskTypeAssociationRulesAreEnforced() {
        String student = student();
        String otherStudent = student();
        StudyPlanDto plan = createPlan(student, List.of());
        String resource = resource().getId();
        String knowledge = knowledge(true);
        String exam = exam(student);
        String wrong = wrongQuestion(student);

        assertThat(plans.createTask(plan.getId(), task(START, StudyPlanTaskType.WRONG_QUESTION_REVIEW, "wq")
                .wrongQuestionId(wrong)).getWrongQuestionId()).isEqualTo(wrong);
        assertThat(plans.createTask(plan.getId(), task(START, StudyPlanTaskType.RESOURCE_LEARNING, "resource")
                .resourceId(resource)).getResourceId()).isEqualTo(resource);
        assertThat(plans.createTask(plan.getId(), task(START, StudyPlanTaskType.KNOWLEDGE_PRACTICE, "knowledge")
                .knowledgeId(knowledge)).getKnowledgeId()).isEqualTo(knowledge);
        assertThat(plans.createTask(plan.getId(), task(START, StudyPlanTaskType.EXAM_REVIEW, "exam")
                .examId(exam)).getExamId()).isEqualTo(exam);
        assertThat(plans.createTask(plan.getId(), task(START, StudyPlanTaskType.READING, "read")).getResourceId())
                .isNull();
        assertThat(plans.createTask(plan.getId(), task(START, StudyPlanTaskType.OTHER, "other")).getExamId())
                .isNull();

        assertRule(() -> plans.createTask(plan.getId(), task(START, StudyPlanTaskType.WRONG_QUESTION_REVIEW, "x")
                .wrongQuestionId(wrongQuestion(otherStudent))));
        jdbc.update("update learning_resource set deleted=1 where id=?", Long.valueOf(resource));
        assertRule(() -> plans.createTask(plan.getId(), task(START, StudyPlanTaskType.RESOURCE_LEARNING, "x")
                .resourceId(resource)));
        assertRule(() -> plans.createTask(plan.getId(), task(START, StudyPlanTaskType.KNOWLEDGE_PRACTICE, "x")
                .knowledgeId(knowledge(false))));
        assertRule(() -> plans.createTask(plan.getId(), task(START, StudyPlanTaskType.EXAM_REVIEW, "x")
                .examId(exam(otherStudent))));
        assertRule(() -> plans.createTask(plan.getId(), task(START, StudyPlanTaskType.READING, "x")
                .resourceId(resource().getId())));
        assertRule(() -> plans.createTask(plan.getId(), task(START, StudyPlanTaskType.EXAM_REVIEW, "x")));
    }

    @Test void scenarios23_30_planQueriesAreIsolatedPagedSortedAndIncludeSortedTasks() {
        String student = student();
        String other = student();
        StudyPlanDto first = createPlan(student, List.of(task(START.plusDays(2), StudyPlanTaskType.READING, "late")
                .sortOrder(2), task(START.plusDays(1), StudyPlanTaskType.OTHER, "early").sortOrder(3),
                task(START.plusDays(1), StudyPlanTaskType.READING, "first").sortOrder(1)));
        StudyPlanDto second = createPlan(student, List.of());
        createPlan(other, List.of());

        assertThat(plans.list(student, StudyPlanStatus.DRAFT, "MANUAL", START, END, 1, 1).getItems())
                .extracting(StudyPlanDto::getId).containsExactly(second.getId());
        StudyPlanDto detail = plans.get(first.getId());
        assertThat(detail.getTasks()).extracting(StudyPlanTaskDto::getTitle)
                .containsExactly("first", "early", "late");
        assertThat(detail.getTasks()).allSatisfy(task -> {
            assertThat(task.getId()).matches("[0-9]+");
            assertThat(task.getStudyPlanId()).matches("[0-9]+");
        });
        plans.delete(second.getId());
        assertThat(plans.list(student, null, null, null, null, 1, 20).getItems())
                .extracting(StudyPlanDto::getId).doesNotContain(second.getId());
        assertThatThrownBy(() -> plans.get(second.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void scenarios31_38_planMetadataUpdateUsesOptimisticLockAndProtectsTaskRange() {
        StudyPlanDto plan = createPlan(student(), List.of(task(START.plusDays(10), StudyPlanTaskType.READING, "task")));
        StudyPlanDto updated = plans.update(plan.getId(),
                updatePlan(START.plusDays(5), END.minusDays(5), 1).title("updated"));
        assertThat(updated.getTitle()).isEqualTo("updated");
        assertThat(updated.getStartDate()).isEqualTo(START.plusDays(5));
        assertThat(updated.getEndDate()).isEqualTo(END.minusDays(5));
        assertThat(updated.getVersion()).isEqualTo(2);
        assertConflict(() -> plans.update(plan.getId(), updatePlan(START, END, 1)));
        assertRule(() -> plans.update(plan.getId(), updatePlan(START, START.plusDays(5), 2)));
        StudyPlanDto unchanged = plans.get(plan.getId());
        assertThat(unchanged.getEndDate()).isEqualTo(END.minusDays(5));
        assertThat(unchanged.getStatus()).isEqualTo(StudyPlanStatus.DRAFT);
        assertThat(unchanged.getTasks()).hasSize(1);
    }

    @Test void scenarios39_53_planStateMachinePersistsImmutableHistoryWithoutChangingTasksOrDescription() {
        StudyPlanDto draftActive = createPlan(student(), List.of(task(START, StudyPlanTaskType.READING, "task")));
        StudyPlanDto active = plans.activate(draftActive.getId(), change(1, "activate reason"));
        StudyPlanDto paused = plans.pause(active.getId(), change(2, "pause reason"));
        StudyPlanDto reactivated = plans.activate(paused.getId(), change(3, null));
        StudyPlanDto completed = plans.complete(reactivated.getId(), change(4, "done"));
        assertThat(completed.getStatus()).isEqualTo(StudyPlanStatus.COMPLETED);
        assertThat(completed.getVersion()).isEqualTo(5);
        assertThat(completed.getDescription()).isEqualTo("description");
        assertThat(completed.getTasks().getFirst().getStatus()).isEqualTo(StudyPlanTaskStatus.TODO);
        assertThat(historyActions(completed.getId())).containsExactly(
                "PLAN_ACTIVATE", "PLAN_PAUSE", "PLAN_ACTIVATE", "PLAN_COMPLETE");
        MapRow firstHistory = history(completed.getId(), null, "PLAN_ACTIVATE");
        assertThat(firstHistory).isEqualTo(new MapRow("PLAN_ACTIVATE", "activate reason", null, 1, 2));

        StudyPlanDto draftCancel = createPlan(student(), List.of());
        assertThat(plans.cancel(draftCancel.getId(), change(1, "cancel")).getStatus())
                .isEqualTo(StudyPlanStatus.CANCELLED);
        assertRule(() -> plans.pause(createPlan(student(), List.of()).getId(), change(1, null)));
        assertRule(() -> plans.activate(completed.getId(), change(5, null)));
        assertConflict(() -> plans.cancel(completed.getId(), change(4, null)));

        StudyPlanDto rollback = createPlan(student(), List.of());
        assertRule(() -> plans.activate(rollback.getId(), change(1, "x".repeat(513))));
        assertThat(plans.get(rollback.getId()).getStatus()).isEqualTo(StudyPlanStatus.DRAFT);
        assertThat(historyCount(rollback.getId())).isZero();
    }

    @Test void scenarios42_47_activeAndPausedCompleteCancelTransitionsAreAllowed() {
        StudyPlanDto activePause = activePlan();
        assertThat(plans.pause(activePause.getId(), change(2, null)).getStatus()).isEqualTo(StudyPlanStatus.PAUSED);
        StudyPlanDto activeComplete = activePlan();
        assertThat(plans.complete(activeComplete.getId(), change(2, null)).getStatus())
                .isEqualTo(StudyPlanStatus.COMPLETED);
        StudyPlanDto activeCancel = activePlan();
        assertThat(plans.cancel(activeCancel.getId(), change(2, null)).getStatus())
                .isEqualTo(StudyPlanStatus.CANCELLED);
        StudyPlanDto pausedComplete = plans.pause(activePlan().getId(), change(2, null));
        assertThat(plans.complete(pausedComplete.getId(), change(3, null)).getStatus())
                .isEqualTo(StudyPlanStatus.COMPLETED);
        StudyPlanDto pausedCancel = plans.pause(activePlan().getId(), change(2, null));
        assertThat(plans.cancel(pausedCancel.getId(), change(3, null)).getStatus())
                .isEqualTo(StudyPlanStatus.CANCELLED);
    }

    @Test void scenarios48_50_allPlanTerminalStatesRejectTransitions() {
        StudyPlanDto completed = plans.complete(activePlan().getId(), change(2, null));
        StudyPlanDto cancelled = plans.cancel(createPlan(student(), List.of()).getId(), change(1, null));
        StudyPlanDto expired = createPlan(student(), List.of());
        jdbc.update("update study_plan set status='EXPIRED' where id=?", Long.valueOf(expired.getId()));
        assertRule(() -> plans.cancel(completed.getId(), change(completed.getVersion(), null)));
        assertRule(() -> plans.activate(cancelled.getId(), change(cancelled.getVersion(), null)));
        assertRule(() -> plans.activate(expired.getId(), change(expired.getVersion(), null)));
    }

    @Test void scenarios54_60_deleteIsPlanOnlyLogicalAndHidesChildOperations() {
        StudyPlanDto plan = createPlan(student(), List.of(task(START, StudyPlanTaskType.READING, "task")));
        Long planId = Long.valueOf(plan.getId());
        Long taskId = Long.valueOf(plan.getTasks().getFirst().getId());
        plans.delete(plan.getId());
        assertThat(jdbc.queryForObject("select deleted from study_plan where id=?", Integer.class, planId)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from study_plan_task where id=?", Integer.class, taskId)).isOne();
        assertThatThrownBy(() -> plans.get(plan.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> plans.listTasks(plan.getId(), null, null, null, 1, 20))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> plans.createTask(plan.getId(), task(START, StudyPlanTaskType.OTHER, "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void scenarios61_69_taskCreateListFiltersPagingAndOrder() {
        StudyPlanDto plan = createPlan(student(), List.of());
        StudyPlanTaskDto later = plans.createTask(plan.getId(), task(START.plusDays(2), StudyPlanTaskType.READING,
                "later").sortOrder(2));
        StudyPlanTaskDto first = plans.createTask(plan.getId(), task(START, StudyPlanTaskType.OTHER,
                "first").sortOrder(1));
        assertRule(() -> plans.createTask(plan.getId(), task(END.plusDays(1), StudyPlanTaskType.OTHER, "outside")));
        assertThat(plans.listTasks(plan.getId(), START, StudyPlanTaskStatus.TODO, StudyPlanTaskType.OTHER, 1, 1)
                .getItems()).extracting(StudyPlanTaskDto::getId).containsExactly(first.getId());
        assertThat(plans.listTasks(plan.getId(), null, null, null, 1, 20).getItems())
                .extracting(StudyPlanTaskDto::getId).containsExactly(first.getId(), later.getId());
        StudyPlanDto terminal = plans.cancel(createPlan(student(), List.of()).getId(), change(1, null));
        assertRule(() -> plans.createTask(terminal.getId(), task(START, StudyPlanTaskType.OTHER, "x")));
    }

    @Test void scenarios70_81_taskUpdateUsesVersionStateMachineRangeAndRevalidatesLinks() {
        StudyPlanDto plan = createPlan(student(), List.of(task(START, StudyPlanTaskType.READING, "task")));
        StudyPlanTaskDto original = plan.getTasks().getFirst();
        StudyPlanTaskDto inProgress = plans.updateTask(plan.getId(), original.getId(),
                updateTask(START.plusDays(1), StudyPlanTaskType.OTHER, 1).title("updated")
                        .status(StudyPlanTaskStatus.IN_PROGRESS));
        assertThat(inProgress.getTitle()).isEqualTo("updated");
        assertThat(inProgress.getVersion()).isEqualTo(2);
        StudyPlanTaskDto todo = plans.updateTask(plan.getId(), original.getId(),
                updateTask(START.plusDays(1), StudyPlanTaskType.READING, 2).status(StudyPlanTaskStatus.TODO));
        assertThat(todo.getStatus()).isEqualTo(StudyPlanTaskStatus.TODO);
        assertConflict(() -> plans.updateTask(plan.getId(), original.getId(),
                updateTask(START, StudyPlanTaskType.READING, 2)));
        assertRule(() -> plans.updateTask(plan.getId(), original.getId(),
                updateTask(END.plusDays(1), StudyPlanTaskType.READING, 3)));
        assertRule(() -> plans.updateTask(plan.getId(), original.getId(),
                updateTask(START, StudyPlanTaskType.RESOURCE_LEARNING, 3)));
        assertRule(() -> plans.updateTask(plan.getId(), original.getId(),
                updateTask(START, StudyPlanTaskType.READING, 3).status(StudyPlanTaskStatus.COMPLETED)));
        StudyPlanTaskDto cancelled = plans.updateTask(plan.getId(), original.getId(),
                updateTask(START, StudyPlanTaskType.READING, 3).status(StudyPlanTaskStatus.CANCELLED));
        assertRule(() -> plans.updateTask(plan.getId(), original.getId(),
                updateTask(START, StudyPlanTaskType.READING, cancelled.getVersion())));

        StudyPlanDto second = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "second")));
        StudyPlanTaskDto secondTask = second.getTasks().getFirst();
        StudyPlanTaskDto secondProgress = plans.updateTask(second.getId(), secondTask.getId(),
                updateTask(START, StudyPlanTaskType.OTHER, 1).status(StudyPlanTaskStatus.IN_PROGRESS));
        assertThat(plans.updateTask(second.getId(), secondTask.getId(),
                updateTask(START, StudyPlanTaskType.OTHER, secondProgress.getVersion())
                        .status(StudyPlanTaskStatus.CANCELLED)).getStatus()).isEqualTo(StudyPlanTaskStatus.CANCELLED);

        StudyPlanDto third = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "third")));
        assertRule(() -> plans.updateTask(third.getId(), third.getTasks().getFirst().getId(),
                updateTask(START, StudyPlanTaskType.OTHER, 1).status(StudyPlanTaskStatus.SKIPPED)));
    }

    @Test void scenarios82_90_completeTaskPersistsServerFieldsHistoryAndKeepsRemark() {
        StudyPlanDto plan = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "task")
                .remark("original")));
        StudyPlanTaskDto task = plan.getTasks().getFirst();
        StudyPlanTaskDto done = plans.completeTask(plan.getId(), task.getId(),
                new CompleteStudyPlanTaskRequest(1).actualDurationSeconds(90).note("completion note"));
        assertThat(done.getStatus()).isEqualTo(StudyPlanTaskStatus.COMPLETED);
        assertThat(done.getCompletedTime()).isNotNull();
        assertThat(done.getActualDurationSeconds()).isEqualTo(90);
        assertThat(done.getVersion()).isEqualTo(2);
        assertThat(done.getRemark()).isEqualTo("original");
        assertHistory(plan.getId(), task.getId(), "TASK_COMPLETE", null, "completion note", 1, 2);
        assertConflict(() -> plans.completeTask(plan.getId(), task.getId(), new CompleteStudyPlanTaskRequest(1)));
        assertRule(() -> plans.completeTask(plan.getId(), task.getId(), new CompleteStudyPlanTaskRequest(2)));

        StudyPlanDto second = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "null duration")));
        StudyPlanTaskDto noDuration = plans.completeTask(second.getId(), second.getTasks().getFirst().getId(),
                new CompleteStudyPlanTaskRequest(1));
        assertThat(noDuration.getActualDurationSeconds()).isNull();
        StudyPlanDto third = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "in progress")));
        StudyPlanTaskDto progressing = plans.updateTask(third.getId(), third.getTasks().getFirst().getId(),
                updateTask(START, StudyPlanTaskType.OTHER, 1).status(StudyPlanTaskStatus.IN_PROGRESS));
        assertThat(plans.completeTask(third.getId(), progressing.getId(),
                new CompleteStudyPlanTaskRequest(progressing.getVersion())).getStatus())
                .isEqualTo(StudyPlanTaskStatus.COMPLETED);
        assertRule(() -> plans.completeTask(createPlan(student(), List.of(task(START,
                StudyPlanTaskType.OTHER, "negative"))).getId(), "999999",
                new CompleteStudyPlanTaskRequest(1).actualDurationSeconds(-1)));
    }

    @Test void scenarios91_97_skipTaskPersistsReasonHistoryWithoutCompletionOrRemarkMutation() {
        StudyPlanDto plan = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "task")
                .remark("original")));
        StudyPlanTaskDto task = plan.getTasks().getFirst();
        StudyPlanTaskDto skipped = plans.skipTask(plan.getId(), task.getId(),
                new SkipStudyPlanTaskRequest("not needed", 1));
        assertThat(skipped.getStatus()).isEqualTo(StudyPlanTaskStatus.SKIPPED);
        assertThat(skipped.getCompletedTime()).isNull();
        assertThat(skipped.getVersion()).isEqualTo(2);
        assertThat(skipped.getRemark()).isEqualTo("original");
        assertHistory(plan.getId(), task.getId(), "TASK_SKIP", "not needed", null, 1, 2);
        assertConflict(() -> plans.skipTask(plan.getId(), task.getId(),
                new SkipStudyPlanTaskRequest("stale", 1)));
        assertRule(() -> plans.skipTask(plan.getId(), task.getId(),
                new SkipStudyPlanTaskRequest("again", 2)));

        StudyPlanDto second = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "in progress")));
        StudyPlanTaskDto progressing = plans.updateTask(second.getId(), second.getTasks().getFirst().getId(),
                updateTask(START, StudyPlanTaskType.OTHER, 1).status(StudyPlanTaskStatus.IN_PROGRESS));
        assertThat(plans.skipTask(second.getId(), progressing.getId(),
                new SkipStudyPlanTaskRequest("skip active", progressing.getVersion())).getStatus())
                .isEqualTo(StudyPlanTaskStatus.SKIPPED);
    }

    @Test void scenarios98_100_concurrentPlanAndTaskTransitionsHaveExactlyOneWinnerAndOneHistory() {
        StudyPlanDto plan = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "task")));
        List<Throwable> planErrors = race(
                () -> plans.activate(plan.getId(), change(1, "activate")),
                () -> plans.cancel(plan.getId(), change(1, "cancel")));
        assertOneConflict(planErrors);
        assertThat(historyCount(plan.getId())).isEqualTo(1);

        StudyPlanDto taskPlan = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "task")));
        String taskId = taskPlan.getTasks().getFirst().getId();
        List<Throwable> taskErrors = race(
                () -> plans.completeTask(taskPlan.getId(), taskId, new CompleteStudyPlanTaskRequest(1)),
                () -> plans.skipTask(taskPlan.getId(), taskId, new SkipStudyPlanTaskRequest("skip", 1)));
        assertOneConflict(taskErrors);
        assertThat(historyCount(taskPlan.getId())).isEqualTo(1);
    }

    @Test void scenarios101_105_taskOperationsDoNotMutateResourceAssignmentHistoryOrMastery() {
        String student = student();
        Resource resource = resource();
        StudyPlanDto plan = createPlan(student, List.of(task(START, StudyPlanTaskType.RESOURCE_LEARNING, "resource")
                .resourceId(resource.getId())));
        int resourceHistory = count("resource_history");
        int masteryHistory = count("mastery_history");
        plans.completeTask(plan.getId(), plan.getTasks().getFirst().getId(), new CompleteStudyPlanTaskRequest(1));
        assertThat(resources.get(resource.getId()).getStatus()).isEqualTo(ResourceStatus.WAITING);
        assertThat(count("student_resource_assignment")).isZero();
        assertThat(count("resource_history")).isEqualTo(resourceHistory);
        assertThat(count("student_mastery")).isZero();
        assertThat(count("mastery_history")).isEqualTo(masteryHistory);
    }

    @Test void scenarios106_110_httpResponsesUseUnifiedErrorsRequestIdAndStringIds() throws Exception {
        StudyPlanDto plan = createPlan(student(), List.of(task(START, StudyPlanTaskType.OTHER, "task")));
        mvc.perform(get("/api/v1/study-plans/{planId}", plan.getId()).header("X-Request-ID", "plan-request"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "plan-request"))
                .andExpect(jsonPath("$.requestId").value("plan-request"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.studentId").isString())
                .andExpect(jsonPath("$.data.tasks[0].id").isString())
                .andExpect(jsonPath("$.data.tasks[0].studyPlanId").isString());
        mvc.perform(put("/api/v1/study-plans/{planId}", plan.getId()).contentType("application/json")
                        .content(json.writeValueAsBytes(updatePlan(START, END, 0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_VERSION_CONFLICT"));
        mvc.perform(post("/api/v1/study-plans/{planId}/pause", plan.getId()).contentType("application/json")
                        .content(json.writeValueAsBytes(change(1, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mvc.perform(get("/api/v1/study-plans/{planId}", "999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").exists());
    }

    @Test void scenarios126_127_generatedOperationsHavePathVariablesAndGenerationValidatesReferences() throws Exception {
        String generatedApi = java.nio.file.Files.readString(java.nio.file.Path.of(
                "target/generated-sources/openapi/src/main/java/com/stdntedu/generated/api/DefaultApi.java"));
        assertThat(generatedApi).contains("@PathVariable(\"planId\") String planId")
                .contains("@PathVariable(\"taskId\") String taskId");
        mvc.perform(post("/api/v1/study-plans/generate")
                        .header("Idempotency-Key", "stage8-generate-validation-001")
                        .contentType("application/json")
                        .content("{\"studentId\":\"1\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\","+
                                "\"dailyAvailableMinutes\":60,\"modelId\":\"999999999\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test void generateStudyPlanWithoutIdempotencyKeyUsesUnifiedBadRequest() throws Exception {
        mvc.perform(post("/api/v1/study-plans/generate")
                        .header("X-Request-ID", "stage8-missing-idempotency-key")
                        .contentType("application/json")
                        .content("{\"studentId\":\"1\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\","+
                                "\"dailyAvailableMinutes\":60,\"modelId\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-ID", "stage8-missing-idempotency-key"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("malformed request"))
                .andExpect(jsonPath("$.requestId").value("stage8-missing-idempotency-key"))
                .andExpect(jsonPath("$.data.fieldErrors").isArray());
    }

    private StudyPlanDto activePlan() {
        StudyPlanDto plan = createPlan(student(), List.of());
        return plans.activate(plan.getId(), change(1, null));
    }

    private StudyPlanDto createPlan(String studentId, List<StudyPlanTaskCreateRequest> tasks) {
        return plans.create(createRequest(studentId, tasks));
    }

    private StudyPlanCreateRequest createRequest(String studentId, List<StudyPlanTaskCreateRequest> tasks) {
        return new StudyPlanCreateRequest(studentId, "plan " + UUID.randomUUID(), "MANUAL", START, END)
                .dailyAvailableMinutes(60).description("description").tasks(tasks);
    }

    private StudyPlanTaskCreateRequest task(LocalDate date, StudyPlanTaskType type, String title) {
        return new StudyPlanTaskCreateRequest(date, type, title).expectedDurationSeconds(60).sortOrder(0);
    }

    private StudyPlanUpdateRequest updatePlan(LocalDate start, LocalDate end, int version) {
        return new StudyPlanUpdateRequest("updated", "MANUAL", start, end, version)
                .dailyAvailableMinutes(45).description("updated description");
    }

    private StudyPlanTaskUpdateRequest updateTask(LocalDate date, StudyPlanTaskType type, int version) {
        return new StudyPlanTaskUpdateRequest(date, type, "task updated", version)
                .expectedDurationSeconds(120).sortOrder(0).remark("updated remark");
    }

    private StudyPlanStatusChangeRequest change(int version, String reason) {
        return new StudyPlanStatusChangeRequest(version).reason(reason);
    }

    private String student() {
        return students.create(new StudentCreate().name("plan " + UUID.randomUUID())
                .currentStageId("1").currentGradeId("1")).getId();
    }

    private String subject() {
        return subjects.selectList(null).stream().filter(SubjectEntity::getEnabled).findFirst().orElseThrow()
                .getId().toString();
    }

    private Resource resource() {
        return resources.create(new ResourceCreate().title("plan resource " + UUID.randomUUID())
                .resourceType("VIDEO").sourceType("LOCAL").subjectId(subject()).status(ResourceStatus.WAITING)
                .knowledgeIds(List.of()));
    }

    private String knowledge(boolean enabled) {
        String code = "PLAN_K_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into knowledge_node(node_code,name,node_type,subject_id,enabled) values (?,?,?,?,?)",
                code, code, "POINT", Long.valueOf(subject()), enabled);
        return jdbc.queryForObject("select id from knowledge_node where node_code=?", Long.class, code).toString();
    }

    private String exam(String studentId) {
        String name = "plan exam " + UUID.randomUUID();
        jdbc.update("insert into exam(student_id,exam_name,exam_type,exam_date) values (?,?,?,?)",
                Long.valueOf(studentId), name, "OTHER", java.sql.Date.valueOf(START));
        return jdbc.queryForObject("select id from exam where exam_name=?", Long.class, name).toString();
    }

    private String wrongQuestion(String studentId) {
        String text = "plan wrong " + UUID.randomUUID();
        jdbc.update("insert into wrong_question(student_id,subject_id,source_type,question_text) values (?,?,?,?)",
                Long.valueOf(studentId), Long.valueOf(subject()), "PRACTICE", text);
        return jdbc.queryForObject("select id from wrong_question where question_text=?", Long.class, text).toString();
    }

    private void assertHistory(String planId, String taskId, String action, String reason, String note,
            int before, int after) {
        MapRow row = history(planId, taskId, action);
        assertThat(row).isEqualTo(new MapRow(action, reason, note, before, after));
    }

    private MapRow history(String planId, String taskId, String action) {
        return jdbc.queryForObject("""
                select action_type, reason, note, version_before, version_after
                  from study_plan_action_history
                 where study_plan_id=? and action_type=?
                   and ((? is null and study_plan_task_id is null) or study_plan_task_id=?)
                 order by id limit 1
                """, (rs, ignored) -> new MapRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getInt(5)), Long.valueOf(planId), action,
                taskId == null ? null : Long.valueOf(taskId), taskId == null ? null : Long.valueOf(taskId));
    }

    private List<String> historyActions(String planId) {
        return jdbc.queryForList("select action_type from study_plan_action_history where study_plan_id=? order by id",
                String.class, Long.valueOf(planId));
    }

    private int historyCount(String planId) {
        return jdbc.queryForObject("select count(*) from study_plan_action_history where study_plan_id=?",
                Integer.class, Long.valueOf(planId));
    }

    private int countPlans(String studentId) {
        return jdbc.queryForObject("select count(*) from study_plan where student_id=? and deleted=0",
                Integer.class, Long.valueOf(studentId));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void assertRule(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION"));
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("DATA_VERSION_CONFLICT"));
    }

    private List<Throwable> race(Runnable first, Runnable second) {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Throwable> one = CompletableFuture.supplyAsync(() -> runAtBarrier(ready, start, first));
        CompletableFuture<Throwable> two = CompletableFuture.supplyAsync(() -> runAtBarrier(ready, start, second));
        await(ready);
        start.countDown();
        return java.util.Arrays.asList(one.join(), two.join());
    }

    private Throwable runAtBarrier(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        await(start);
        try {
            action.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private void assertOneConflict(List<Throwable> errors) {
        assertThat(errors.stream().filter(java.util.Objects::isNull).count()).isEqualTo(1);
        assertThat(errors.stream().filter(java.util.Objects::nonNull).toList()).singleElement()
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("DATA_VERSION_CONFLICT"));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private record MapRow(String action, String reason, String note, int before, int after) { }
}

package com.stdntedu.stage8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.stdntedu.generated.model.Resource;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.ResourceHistoryCreateRequest;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudentResourceCreateRequest;
import com.stdntedu.generated.model.StudentResourceDto;
import com.stdntedu.generated.model.StudentResourceStatus;
import com.stdntedu.generated.model.StudentResourceUpdateRequest;
import com.stdntedu.resource.service.LearningResourceService;
import com.stdntedu.resource.service.ResourceHistoryService;
import com.stdntedu.resource.service.StudentResourceService;
import com.stdntedu.resource.mapper.StudentResourceAssignmentMapper;
import com.stdntedu.student.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StageEightB1IntegrationTest {
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

    @Autowired StudentResourceService assignments;
    @Autowired LearningResourceService resources;
    @Autowired ResourceHistoryService histories;
    @Autowired StudentService students;
    @Autowired SubjectMapper subjects;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @SpyBean StudentResourceAssignmentMapper assignmentMapper;

    @Test void scenarios01_02_08_09_10_createDefaultsAndExplicitStatusWithStringIds() {
        String student = student();
        Resource waitingResource = resource(subject(), ResourceStatus.WAITING);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        StudentResourceDto waiting = assignments.create(new StudentResourceCreateRequest(student,
                waitingResource.getId()));
        StudentResourceDto learning = assignments.create(new StudentResourceCreateRequest(student,
                resource(subject(), ResourceStatus.ARCHIVED).getId()).status(StudentResourceStatus.LEARNING));
        assertThat(waiting.getId()).matches("[0-9]+");
        assertThat(waiting.getStudentId()).isEqualTo(student);
        assertThat(waiting.getResourceId()).isEqualTo(waitingResource.getId());
        assertThat(waiting.getStudentStatus()).isEqualTo(StudentResourceStatus.WAITING);
        assertThat(learning.getStudentStatus()).isEqualTo(StudentResourceStatus.LEARNING);
        assertThat(waiting.getVersion()).isZero();
        assertThat(waiting.getAssignedTime().toLocalDateTime()).isAfter(before);
    }

    @Test void scenarios03_04_05_createRejectsMissingStudentMissingResourceAndDeletedResource() {
        Resource resource = resource(subject(), ResourceStatus.WAITING);
        assertThatThrownBy(() -> assignments.create(new StudentResourceCreateRequest("999999", resource.getId())))
                .isInstanceOf(ResourceNotFoundException.class);
        String student = student();
        assertThatThrownBy(() -> assignments.create(new StudentResourceCreateRequest(student, "999999")))
                .isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("update learning_resource set deleted=1 where id=?", Long.valueOf(resource.getId()));
        assertThatThrownBy(() -> assignments.create(new StudentResourceCreateRequest(student, resource.getId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void scenarios06_63_duplicateReturnsUnifiedConflict() throws Exception {
        String student = student();
        String resource = resource(subject(), ResourceStatus.WAITING).getId();
        assignments.create(new StudentResourceCreateRequest(student, resource));
        mvc.perform(post("/api/v1/student-resources").contentType("application/json")
                        .header("X-Request-ID", "duplicate-assignment")
                        .content(json.writeValueAsBytes(new StudentResourceCreateRequest(student, resource))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DATA"))
                .andExpect(jsonPath("$.requestId").value("duplicate-assignment"));
    }

    @Test void scenario07_concurrentDuplicateCreatesLeaveExactlyOneAssignment() throws Exception {
        String student = student();
        String resource = resource(subject(), ResourceStatus.WAITING).getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var first = concurrentCreate(student, resource, ready, start);
        var second = concurrentCreate(student, resource, ready, start);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Attempt> attempts = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        assertThat(attempts).filteredOn(attempt -> attempt.value() != null).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> attempt.error() instanceof BusinessException
                && ((BusinessException) attempt.error()).getStatus().value() == 409).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from student_resource_assignment where student_id=? and resource_id=?",
                Integer.class, Long.valueOf(student), Long.valueOf(resource))).isEqualTo(1);
    }

    @Test void scenarios11_13_14_15_16_17_getAssemblesResourceSubjectAndNullProgress() {
        String subject = subject();
        Resource resource = resource(subject, ResourceStatus.REVIEW);
        StudentResourceDto created = assignments.create(new StudentResourceCreateRequest(student(), resource.getId())
                .status(StudentResourceStatus.ARCHIVED));
        StudentResourceDto found = assignments.get(created.getId());
        assertThat(found.getResourceTitle()).isEqualTo(resource.getTitle());
        assertThat(found.getResourceStatus()).isEqualTo(ResourceStatus.REVIEW);
        assertThat(found.getStudentStatus()).isEqualTo(StudentResourceStatus.ARCHIVED);
        assertThat(found.getSubjectId()).isEqualTo(subject);
        assertThat(found.getSubjectName()).isNotBlank();
        assertThat(found.getLatestProgressPercent()).isNull();
    }

    @Test void scenarios12_62_missingAssignmentReturns404ErrorResponse() throws Exception {
        mvc.perform(get("/api/v1/student-resources/{assignmentId}", "999999")
                        .header("X-Request-ID", "missing-assignment"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("missing-assignment"));
    }

    @Test void scenarios18_19_51_52_53_54_latestProgressUsesCreateTimeThenIdAndPreservesZero() {
        String student = student();
        Resource resource = resource(subject(), ResourceStatus.WAITING);
        StudentResourceDto assignment = assignments.create(new StudentResourceCreateRequest(student, resource.getId()));
        assertThat(assignments.get(assignment.getId()).getLatestProgressPercent()).isNull();
        insertHistory(student, resource.getId(), "91", true, "2026-08-17 10:00:00.000");
        insertHistory(student, resource.getId(), "0", false, "2026-08-17 10:00:00.000");
        assertThat(assignments.get(assignment.getId()).getLatestProgressPercent()).isEqualByComparingTo("0.00");
        insertHistory(student, resource.getId(), "37", false, "2026-08-17 10:01:00.000");
        assertThat(assignments.get(assignment.getId()).getLatestProgressPercent()).isEqualByComparingTo("37.00");
    }

    @Test void scenarios20_21_22_23_24_25_26_27_28_listFiltersPagesAndAssemblesWithoutNPlusOne() {
        String firstStudent = student();
        String secondStudent = student();
        String firstSubject = subject();
        String secondSubject = anotherSubject(firstSubject);
        StudentResourceDto first = assignments.create(new StudentResourceCreateRequest(firstStudent,
                resource(firstSubject, ResourceStatus.WAITING).getId()).status(StudentResourceStatus.LEARNING));
        StudentResourceDto second = assignments.create(new StudentResourceCreateRequest(firstStudent,
                resource(secondSubject, ResourceStatus.COMPLETED).getId()).status(StudentResourceStatus.WAITING));
        assignments.create(new StudentResourceCreateRequest(secondStudent,
                resource(firstSubject, ResourceStatus.WAITING).getId()));
        jdbc.update("update student_resource_assignment set assigned_time='2026-08-17 12:00:00.000' where id in (?,?)",
                Long.valueOf(first.getId()), Long.valueOf(second.getId()));
        insertHistory(firstStudent, first.getResourceId(), "55", false, "2026-08-17 12:01:00.000");
        var page = assignments.list(firstStudent, null, null, 1, 1);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getItems()).extracting(StudentResourceDto::getId).containsExactly(second.getId());
        assertThat(assignments.list(firstStudent, StudentResourceStatus.LEARNING, null, 1, 20).getItems())
                .extracting(StudentResourceDto::getId).containsExactly(first.getId());
        assertThat(assignments.list(firstStudent, null, secondSubject, 1, 20).getItems())
                .extracting(StudentResourceDto::getId).containsExactly(second.getId());
        StudentResourceDto assembled = assignments.list(firstStudent, null, firstSubject, 1, 20).getItems().getFirst();
        assertThat(assembled.getResourceTitle()).isNotBlank();
        assertThat(assembled.getSubjectName()).isNotBlank();
        assertThat(assembled.getLatestProgressPercent()).isEqualByComparingTo("55.00");
    }

    @Test void scenarios29_30_31_32_35_36_37_38_updateUsesContractFieldsAndAllowsManualTransitions() {
        String student = student();
        String resource = resource(subject(), ResourceStatus.WAITING).getId();
        StudentResourceDto created = assignments.create(new StudentResourceCreateRequest(student, resource));
        StudentResourceDto learning = assignments.update(created.getId(),
                update(StudentResourceStatus.LEARNING, "learning", 0));
        StudentResourceDto archived = assignments.update(created.getId(),
                update(StudentResourceStatus.ARCHIVED, "archived", 1));
        StudentResourceDto waiting = assignments.update(created.getId(),
                update(StudentResourceStatus.WAITING, "reset", 2));
        assertThat(learning.getVersion()).isEqualTo(1);
        assertThat(archived.getVersion()).isEqualTo(2);
        assertThat(waiting.getVersion()).isEqualTo(3);
        assertThat(waiting.getStudentId()).isEqualTo(student);
        assertThat(waiting.getResourceId()).isEqualTo(resource);
        assertThat(waiting.getRemark()).isEqualTo("reset");
    }

    @Test void scenarios33_34_staleVersionReturnsDataVersionConflict() throws Exception {
        StudentResourceDto created = assignments.create(new StudentResourceCreateRequest(student(),
                resource(subject(), ResourceStatus.WAITING).getId()));
        assignments.update(created.getId(), update(StudentResourceStatus.LEARNING, null, 0));
        mvc.perform(put("/api/v1/student-resources/{assignmentId}", created.getId())
                        .contentType("application/json").content(json.writeValueAsBytes(
                                update(StudentResourceStatus.REVIEW, null, 0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_VERSION_CONFLICT"));
    }

    @Test void scenario39_historyWithoutAssignmentDoesNotCreateOne() {
        String student = student();
        Resource resource = resource(subject(), ResourceStatus.WAITING);
        histories.create(resource.getId(), history(student, "50", false));
        assertThat(assignmentCount(student, resource.getId())).isZero();
    }

    @Test void scenarios40_41_42_47_49_waitingAndLearningHistoryTransitionsArePrecise() {
        String student = student();
        Resource progressingResource = resource(subject(), ResourceStatus.WAITING);
        StudentResourceDto progressing = assignments.create(new StudentResourceCreateRequest(student,
                progressingResource.getId()));
        histories.create(progressingResource.getId(), history(student, "0", false));
        assertThat(assignments.get(progressing.getId()).getStudentStatus()).isEqualTo(StudentResourceStatus.WAITING);
        histories.create(progressingResource.getId(), history(student, "10", false));
        StudentResourceDto learning = assignments.get(progressing.getId());
        assertThat(learning.getStudentStatus()).isEqualTo(StudentResourceStatus.LEARNING);
        assertThat(learning.getVersion()).isEqualTo(1);
        histories.create(progressingResource.getId(), history(student, "100", true));
        StudentResourceDto completed = assignments.get(progressing.getId());
        assertThat(completed.getStudentStatus()).isEqualTo(StudentResourceStatus.COMPLETED);
        assertThat(completed.getVersion()).isEqualTo(2);

        Resource completedResource = resource(subject(), ResourceStatus.WAITING);
        StudentResourceDto direct = assignments.create(new StudentResourceCreateRequest(student,
                completedResource.getId()));
        histories.create(completedResource.getId(), history(student, "100", true));
        assertThat(assignments.get(direct.getId()).getStudentStatus()).isEqualTo(StudentResourceStatus.COMPLETED);
    }

    @Test void scenarios43_44_45_46_reviewAndArchivedAreNeverAutomaticallyOverwritten() {
        String student = student();
        for (StudentResourceStatus status : List.of(StudentResourceStatus.REVIEW, StudentResourceStatus.ARCHIVED)) {
            Resource resource = resource(subject(), ResourceStatus.WAITING);
            StudentResourceDto assignment = assignments.create(new StudentResourceCreateRequest(student,
                    resource.getId()).status(status));
            histories.create(resource.getId(), history(student, "25", false));
            histories.create(resource.getId(), history(student, "100", true));
            StudentResourceDto found = assignments.get(assignment.getId());
            assertThat(found.getStudentStatus()).isEqualTo(status);
            assertThat(found.getVersion()).isZero();
        }
    }

    @Test void scenario48_historyAndAssignmentTransitionRollBackTogether() {
        String student = student();
        Resource resource = resource(subject(), ResourceStatus.WAITING);
        StudentResourceDto assignment = assignments.create(new StudentResourceCreateRequest(student, resource.getId()));
        int before = historyCount(student, resource.getId());
        doThrow(new DuplicateKeyException("forced assignment failure")).when(assignmentMapper)
                .transitionWithVersion(anyLong(), anyInt(), any(), any());
        try {
            assertThatThrownBy(() -> histories.create(resource.getId(), history(student, "50", false)))
                    .isInstanceOf(DuplicateKeyException.class);
        } finally {
            reset(assignmentMapper);
        }
        assertThat(historyCount(student, resource.getId())).isEqualTo(before);
        assertThat(assignments.get(assignment.getId()).getStudentStatus()).isEqualTo(StudentResourceStatus.WAITING);
    }

    @Test void scenarios50_57_58_59_60_resourceStatusAndMasteryRemainIsolated() {
        String student = student();
        Resource resource = resource(subject(), ResourceStatus.REVIEW);
        int masteryBefore = count("student_mastery");
        int masteryHistoryBefore = count("mastery_history");
        StudentResourceDto assignment = assignments.create(new StudentResourceCreateRequest(student, resource.getId()));
        assignments.update(assignment.getId(), update(StudentResourceStatus.LEARNING, null, 0));
        histories.create(resource.getId(), history(student, "100", true));
        assertThat(resources.get(resource.getId()).getStatus()).isEqualTo(ResourceStatus.REVIEW);
        assertThat(count("student_mastery")).isEqualTo(masteryBefore);
        assertThat(count("mastery_history")).isEqualTo(masteryHistoryBefore);
    }

    @Test void scenarios55_56_concurrentManualUpdateAndHistoryNeverSilentlyOverwrite() throws Exception {
        String student = student();
        Resource resource = resource(subject(), ResourceStatus.WAITING);
        StudentResourceDto assignment = assignments.create(new StudentResourceCreateRequest(student, resource.getId()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Throwable> manual = CompletableFuture.supplyAsync(() -> afterBarrier(ready, start, () ->
                assignments.update(assignment.getId(), update(StudentResourceStatus.REVIEW, null, 0))));
        CompletableFuture<Throwable> automatic = CompletableFuture.supplyAsync(() -> afterBarrier(ready, start, () ->
                histories.create(resource.getId(), history(student, "50", false))));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        Throwable manualError = manual.get(10, TimeUnit.SECONDS);
        Throwable automaticError = automatic.get(10, TimeUnit.SECONDS);
        assertThat(automaticError).isNull();
        StudentResourceDto found = assignments.get(assignment.getId());
        if (manualError == null) {
            assertThat(found.getStudentStatus()).isEqualTo(StudentResourceStatus.REVIEW);
        } else {
            assertThat(manualError).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) manualError).getCode()).isEqualTo("DATA_VERSION_CONFLICT");
            assertThat(found.getStudentStatus()).isEqualTo(StudentResourceStatus.LEARNING);
        }
        assertThat(found.getVersion()).isEqualTo(1);
    }

    @Test void scenarios61_64_apiRequestIdAndAllAssignmentIdsAreJsonStrings() throws Exception {
        StudentResourceDto assignment = assignments.create(new StudentResourceCreateRequest(student(),
                resource(subject(), ResourceStatus.WAITING).getId()));
        mvc.perform(get("/api/v1/student-resources/{assignmentId}", assignment.getId())
                        .header("X-Request-ID", "stage8-assignment"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "stage8-assignment"))
                .andExpect(jsonPath("$.requestId").value("stage8-assignment"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.studentId").isString())
                .andExpect(jsonPath("$.data.resourceId").isString())
                .andExpect(jsonPath("$.data.subjectId").isString());
    }

    @Test void deletedResourceIsHiddenFromAssignmentDetailAndList() {
        String student = student();
        Resource resource = resource(subject(), ResourceStatus.WAITING);
        StudentResourceDto assignment = assignments.create(new StudentResourceCreateRequest(student, resource.getId()));
        jdbc.update("update learning_resource set deleted=1 where id=?", Long.valueOf(resource.getId()));
        assertThatThrownBy(() -> assignments.get(assignment.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(assignments.list(student, null, null, 1, 20).getItems())
                .extracting(StudentResourceDto::getId).doesNotContain(assignment.getId());
        assertThat(jdbc.queryForObject("select count(*) from student_resource_assignment where id=?",
                Integer.class, Long.valueOf(assignment.getId()))).isEqualTo(1);
    }

    private CompletableFuture<Attempt> concurrentCreate(String student, String resource, CountDownLatch ready,
            CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            await(start);
            try {
                return new Attempt(assignments.create(new StudentResourceCreateRequest(student, resource)), null);
            } catch (Throwable error) {
                return new Attempt(null, error);
            }
        });
    }

    private Throwable afterBarrier(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        await(start);
        try {
            action.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private StudentResourceUpdateRequest update(StudentResourceStatus status, String remark, int version) {
        return new StudentResourceUpdateRequest(status, version).remark(remark);
    }

    private ResourceHistoryCreateRequest history(String studentId, String progress, boolean completed) {
        return new ResourceHistoryCreateRequest().studentId(studentId).durationSeconds(120)
                .progressPercent(new BigDecimal(progress)).completed(completed);
    }

    private Resource resource(String subjectId, ResourceStatus status) {
        return resources.create(new ResourceCreate().title("stage8 " + UUID.randomUUID()).resourceType("VIDEO")
                .sourceType("LOCAL").subjectId(subjectId).durationSeconds(600).difficulty(3).status(status)
                .description("stage eight assignment").tags(List.of("stage8")).knowledgeIds(List.of()));
    }

    private String student() {
        return students.create(new StudentCreate().name("stage8 " + UUID.randomUUID())
                .currentStageId("1").currentGradeId("1")).getId();
    }

    private String subject() {
        return subjects.selectList(null).stream().filter(SubjectEntity::getEnabled).findFirst().orElseThrow()
                .getId().toString();
    }

    private String anotherSubject(String excluded) {
        return subjects.selectList(null).stream().filter(SubjectEntity::getEnabled)
                .filter(subject -> !subject.getId().toString().equals(excluded)).findFirst().orElseThrow()
                .getId().toString();
    }

    private void insertHistory(String studentId, String resourceId, String progress, boolean completed,
            String createTime) {
        jdbc.update("""
                insert into resource_history(student_id,resource_id,duration_seconds,progress_percent,completed,create_time)
                values (?,?,?,?,?,?)
                """, Long.valueOf(studentId), Long.valueOf(resourceId), 60, new BigDecimal(progress), completed,
                java.sql.Timestamp.valueOf(createTime));
    }

    private int assignmentCount(String studentId, String resourceId) {
        return jdbc.queryForObject("select count(*) from student_resource_assignment where student_id=? and resource_id=?",
                Integer.class, Long.valueOf(studentId), Long.valueOf(resourceId));
    }

    private int historyCount(String studentId, String resourceId) {
        return jdbc.queryForObject("select count(*) from resource_history where student_id=? and resource_id=?",
                Integer.class, Long.valueOf(studentId), Long.valueOf(resourceId));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private record Attempt(StudentResourceDto value, Throwable error) { }
}

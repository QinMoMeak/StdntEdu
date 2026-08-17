package com.stdntedu.stage7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.Resource;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.ResourceHistoryCreateRequest;
import com.stdntedu.generated.model.ResourceHistoryDto;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.ResourceUpdate;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudyLogCreateRequest;
import com.stdntedu.generated.model.StudyLogDto;
import com.stdntedu.generated.model.StudyLogUpdateRequest;
import com.stdntedu.resource.service.LearningResourceService;
import com.stdntedu.resource.service.ResourceHistoryService;
import com.stdntedu.resource.service.StudyLogService;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import com.stdntedu.student.service.StudentService;
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
class StageSevenIntegrationTest {
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

    @Autowired LearningResourceService resources;
    @Autowired ResourceHistoryService histories;
    @Autowired StudyLogService studyLogs;
    @Autowired StudentService students;
    @Autowired SubjectMapper subjects;
    @Autowired KnowledgeNodeReferenceMapper nodes;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void createsGlobalResourceWithGeneratedUniqueCodeNormalizedTagsAndStringIds() {
        String subject = subject();
        Resource first = resources.create(resource(subject, List.of()).difficulty(0)
                .tags(List.of(" algebra ", "", "algebra", "video")));
        Resource second = resources.create(resource(subject, List.of()).difficulty(5));
        assertThat(first.getId()).matches("[0-9]+");
        assertThat(first.getSubjectId()).isEqualTo(subject);
        assertThat(first.getResourceCode()).hasSize(32).startsWith("RES");
        assertThat(second.getResourceCode()).isNotEqualTo(first.getResourceCode());
        assertThat(first.getTags()).containsExactly("algebra", "video");
        assertThat(first.getDifficulty()).isZero();
        assertThat(second.getDifficulty()).isEqualTo(5);
        assertThat(first.getVersion()).isZero();
    }

    @Test void rejectsMissingAndDisabledResourceSubjects() {
        assertThatThrownBy(() -> resources.create(resource("999999", List.of())))
                .isInstanceOf(BusinessException.class);
        String disabled = disabledSubject();
        assertThatThrownBy(() -> resources.create(resource(disabled, List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test void validatesKnowledgeExistenceEnabledStateAndSubjectMatch() {
        String subject = subject(), otherSubject = anotherSubject(subject);
        KnowledgeNodeReferenceEntity valid = node(subject, true);
        KnowledgeNodeReferenceEntity disabled = node(subject, false);
        KnowledgeNodeReferenceEntity other = node(otherSubject, true);
        assertThat(resources.create(resource(subject, List.of(valid.getId().toString()))).getKnowledgeIds())
                .containsExactly(valid.getId().toString());
        assertThatThrownBy(() -> resources.create(resource(subject, List.of("999999"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resources.create(resource(subject, List.of(disabled.getId().toString()))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resources.create(resource(subject, List.of(other.getId().toString()))))
                .isInstanceOf(BusinessException.class);
    }

    @Test void permitsKnowledgeWithoutSubjectAndDeduplicatesRelationships() {
        KnowledgeNodeReferenceEntity node = node(subject(), true);
        Resource created = resources.create(resource(null,
                List.of(node.getId().toString(), node.getId().toString())));
        assertThat(created.getSubjectId()).isNull();
        assertThat(created.getKnowledgeIds()).containsExactly(node.getId().toString());
        assertThat(jdbc.queryForObject("select count(*) from learning_resource_knowledge where resource_id=?",
                Integer.class, Long.valueOf(created.getId()))).isOne();
    }

    @Test void rejectsDifficultyOutsideFrozenIntegerRange() {
        assertThatThrownBy(() -> resources.create(resource(subject(), List.of()).difficulty(-1)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resources.create(resource(subject(), List.of()).difficulty(6)))
                .isInstanceOf(BusinessException.class);
    }

    @Test void acceptsNullCoverAndRejectsNonNullCoverWithBusinessRuleViolation() {
        resources.create(resource(subject(), List.of()).coverAttachmentId(null));
        assertThatThrownBy(() -> resources.create(resource(subject(), List.of()).coverAttachmentId("1")))
                .isInstanceOf(BusinessException.class).satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getStatus().value()).isEqualTo(422);
                    assertThat(business.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
                });
    }

    @Test void getsResourceAndReturnsNotFoundForMissingResource() {
        Resource created = resources.create(resource(subject(), List.of()));
        assertThat(resources.get(created.getId())).usingRecursiveComparison().isEqualTo(created);
        assertThatThrownBy(() -> resources.get("999999")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void listsResourcesWithStablePaginationAndBatchKnowledgeAssembly() {
        String subject = subject();
        KnowledgeNodeReferenceEntity one = node(subject, true), two = node(subject, true);
        Resource older = resources.create(resource(subject, List.of(one.getId().toString())));
        Resource newer = resources.create(resource(subject, List.of(two.getId().toString())));
        var page = resources.list(1, 2);
        assertThat(page.getPage()).isOne();
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(2);
        assertThat(page.getItems()).extracting(Resource::getId).contains(newer.getId(), older.getId());
        assertThat(page.getItems().stream().filter(item -> item.getId().equals(newer.getId())).findFirst().orElseThrow()
                .getKnowledgeIds()).containsExactly(two.getId().toString());
    }

    @Test void updatesResourceByReplacementAndPreservesIdentityWhileIncrementingVersion() {
        String subject = subject();
        KnowledgeNodeReferenceEntity oldNode = node(subject, true), newNode = node(subject, true);
        Resource created = resources.create(resource(subject, List.of(oldNode.getId().toString())));
        Resource updated = resources.update(created.getId(), update(created, subject,
                List.of(newNode.getId().toString())).title("updated resource"));
        assertThat(updated.getTitle()).isEqualTo("updated resource");
        assertThat(updated.getKnowledgeIds()).containsExactly(newNode.getId().toString());
        assertThat(updated.getVersion()).isEqualTo(created.getVersion() + 1);
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getResourceCode()).isEqualTo(created.getResourceCode());
        Resource withoutKnowledge = resources.update(updated.getId(), update(updated, subject, List.of()));
        assertThat(withoutKnowledge.getKnowledgeIds()).isEmpty();
    }

    @Test void rejectsStaleResourceVersionWithConflictCode() {
        Resource created = resources.create(resource(subject(), List.of()));
        ResourceUpdate stale = update(created, created.getSubjectId(), List.of());
        resources.update(created.getId(), stale);
        assertThatThrownBy(() -> resources.update(created.getId(), stale)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo("DATA_VERSION_CONFLICT"));
    }

    @Test void apiRejectsDecimalDifficultyInsteadOfCoercingIt() throws Exception {
        String body = "{\"title\":\"decimal\",\"resourceType\":\"VIDEO\",\"sourceType\":\"LOCAL\","
                + "\"status\":\"WAITING\",\"difficulty\":2.5}";
        mvc.perform(post("/api/v1/resources").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test void resourceApiReturnsRequestIdStringIdsAndUnifiedErrors() throws Exception {
        Resource created = resources.create(resource(subject(), List.of()));
        mvc.perform(get("/api/v1/resources/{id}", created.getId()).header("X-Request-ID", "stage7-resource"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "stage7-resource"))
                .andExpect(jsonPath("$.requestId").value("stage7-resource"))
                .andExpect(jsonPath("$.data.id").isString()).andExpect(jsonPath("$.data.subjectId").isString());
        mvc.perform(get("/api/v1/resources/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/resources").contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.data.fieldErrors").isArray());
        String unsupportedCover = "{\"title\":\"cover\",\"resourceType\":\"VIDEO\","
                + "\"sourceType\":\"LOCAL\",\"status\":\"WAITING\",\"coverAttachmentId\":\"1\"}";
        mvc.perform(post("/api/v1/resources").contentType("application/json").content(unsupportedCover))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test void createsImmutableHistoryWithoutChangingGlobalResourceStatusAndAllowsCompletedAtNinetyFive() {
        String student = student();
        Resource resource = resources.create(resource(subject(), List.of()).status(ResourceStatus.WAITING));
        ResourceHistoryDto created = histories.create(resource.getId(), history(student, "95", true)
                .note("watched carefully"));
        assertThat(created.getId()).matches("[0-9]+");
        assertThat(created.getStudentId()).isEqualTo(student);
        assertThat(created.getResourceId()).isEqualTo(resource.getId());
        assertThat(created.getProgressPercent()).isEqualByComparingTo("95");
        assertThat(created.getCompleted()).isTrue();
        assertThat(created.getNote()).isEqualTo("watched carefully");
        assertThat(resources.get(resource.getId()).getStatus()).isEqualTo(ResourceStatus.WAITING);
    }

    @Test void validatesHistoryStudentResourceProgressDurationAndTimeRange() {
        Resource resource = resources.create(resource(subject(), List.of()));
        String student = student();
        assertThatThrownBy(() -> histories.create("999999", history(student, "0", false)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> histories.create(resource.getId(), history("999999", "0", false)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> histories.create(resource.getId(), history(student, "-0.01", false)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> histories.create(resource.getId(), history(student, "100.01", false)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> histories.create(resource.getId(), history(student, "50", false)
                .durationSeconds(-1))).isInstanceOf(BusinessException.class);
        OffsetDateTime start = OffsetDateTime.now();
        assertThatThrownBy(() -> histories.create(resource.getId(), history(student, "50", false)
                .startTime(start).endTime(start.minusMinutes(1)))).isInstanceOf(BusinessException.class);
    }

    @Test void acceptsHistoryProgressBoundariesAndPersistsCompletedIndependently() {
        String student = student();
        Resource resource = resources.create(resource(subject(), List.of()));
        assertThat(histories.create(resource.getId(), history(student, "0", false)).getProgressPercent())
                .isEqualByComparingTo("0");
        assertThat(histories.create(resource.getId(), history(student, "100", false)).getProgressPercent())
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select completed from resource_history where resource_id=? order by id desc limit 1",
                Boolean.class, Long.valueOf(resource.getId()))).isFalse();
    }

    @Test void listsResourceHistoryWithStudentAndResourceIsolationPaginationAndStableOrder() {
        String firstStudent = student(), secondStudent = student();
        Resource firstResource = resources.create(resource(subject(), List.of()));
        Resource secondResource = resources.create(resource(subject(), List.of()));
        ResourceHistoryDto older = histories.create(firstResource.getId(), history(firstStudent, "10", false));
        ResourceHistoryDto newer = histories.create(firstResource.getId(), history(firstStudent, "20", false));
        histories.create(firstResource.getId(), history(secondStudent, "30", false));
        histories.create(secondResource.getId(), history(firstStudent, "40", false));
        var page = histories.listForResource(firstResource.getId(), firstStudent, 1, 20);
        assertThat(page.getItems()).extracting(ResourceHistoryDto::getId).containsExactly(newer.getId(), older.getId());
        assertThat(page.getItems()).allSatisfy(item -> {
            assertThat(item.getStudentId()).isEqualTo(firstStudent);
            assertThat(item.getResourceId()).isEqualTo(firstResource.getId());
        });
        assertThatThrownBy(() -> histories.listForResource("999999", firstStudent, 1, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void listsStudentHistoryUsingFrozenFiltersAndJoinedResourceFields() {
        String student = student(), matchingSubject = subject(), otherSubject = anotherSubject(matchingSubject);
        Resource matching = resources.create(resource(matchingSubject, List.of()).resourceType("VIDEO")
                .sourceType("LOCAL"));
        Resource other = resources.create(resource(otherSubject, List.of()).resourceType("BOOK")
                .sourceType("WEB"));
        histories.create(matching.getId(), history(student, "90", true));
        histories.create(other.getId(), history(student, "20", false));
        var page = histories.listForStudent(student, matchingSubject, "VIDEO", "LOCAL", true,
                LocalDate.now(), LocalDate.now(), 1, 20);
        assertThat(page.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResourceId()).isEqualTo(matching.getId());
            assertThat(item.getResourceTitle()).isEqualTo(matching.getTitle());
            assertThat(item.getResourceType()).isEqualTo("VIDEO");
            assertThat(item.getSourceType()).isEqualTo("LOCAL");
        });
    }

    @Test void resourceHistoryApiUsesGeneratedPathParameterAndReturnsStringIds() throws Exception {
        String student = student();
        Resource resource = resources.create(resource(subject(), List.of()));
        ResourceHistoryDto history = histories.create(resource.getId(), history(student, "45", false));
        mvc.perform(get("/api/v1/resources/{resourceId}/history", resource.getId()).param("studentId", student)
                .header("X-Request-ID", "stage7-history"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "stage7-history"))
                .andExpect(jsonPath("$.requestId").value("stage7-history"))
                .andExpect(jsonPath("$.data.items[0].id").value(history.getId()))
                .andExpect(jsonPath("$.data.items[0].id").isString())
                .andExpect(jsonPath("$.data.items[0].studentId").isString())
                .andExpect(jsonPath("$.data.items[0].resourceId").isString());
    }

    @Test void resourceAndHistoryOperationsDoNotWriteMasteryTables() {
        int masteryBefore = count("student_mastery"), historyBefore = count("mastery_history");
        Resource resource = resources.create(resource(subject(), List.of()));
        resources.update(resource.getId(), update(resource, resource.getSubjectId(), List.of()).title("changed"));
        histories.create(resource.getId(), history(student(), "100", true));
        assertThat(count("student_mastery")).isEqualTo(masteryBefore);
        assertThat(count("mastery_history")).isEqualTo(historyBefore);
    }

    @Test void createsStudyLogWithNullSubjectVersionZeroAndStringIds() {
        String student = student();
        StudyLogDto created = studyLogs.create(studyLog(student, null));
        assertThat(created.getId()).matches("[0-9]+");
        assertThat(created.getStudentId()).isEqualTo(student);
        assertThat(created.getSubjectId()).isNull();
        assertThat(created.getSubjectName()).isNull();
        assertThat(created.getVersion()).isZero();
    }

    @Test void validatesStudyLogStudentAndEnabledSubject() {
        String student = student(), validSubject = subject(), disabled = disabledSubject();
        assertThat(studyLogs.create(studyLog(student, validSubject)).getSubjectName()).isNotBlank();
        assertThatThrownBy(() -> studyLogs.create(studyLog("999999", validSubject)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> studyLogs.create(studyLog(student, "999999")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> studyLogs.create(studyLog(student, disabled)))
                .isInstanceOf(BusinessException.class);
    }

    @Test void rejectsNegativeDurationAndFutureStudyDateInSystemTimezone() {
        String student = student();
        assertThatThrownBy(() -> studyLogs.create(studyLog(student, null).durationSeconds(-1)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> studyLogs.create(studyLog(student, null).studyDate(LocalDate.now().plusDays(1))))
                .isInstanceOf(BusinessException.class);
    }

    @Test void getsAndListsStudyLogsWithIsolationFiltersKeywordDatesPaginationAndOrder() {
        String firstStudent = student(), secondStudent = student(), subject = subject();
        StudyLogDto older = studyLogs.create(studyLog(firstStudent, null).studyDate(LocalDate.now().minusDays(1))
                .content("algebra older"));
        StudyLogDto newer = studyLogs.create(studyLog(firstStudent, subject).studyDate(LocalDate.now())
                .content("algebra current"));
        studyLogs.create(studyLog(secondStudent, subject).content("algebra other student"));
        assertThat(studyLogs.get(newer.getId()).getSubjectName()).isNotBlank();
        var all = studyLogs.list(firstStudent, null, LocalDate.now().minusDays(2), LocalDate.now(), "algebra", 1, 20);
        assertThat(all.getItems()).extracting(StudyLogDto::getId).containsExactly(newer.getId(), older.getId());
        var filtered = studyLogs.list(firstStudent, subject, null, null, "current", 1, 1);
        assertThat(filtered.getTotal()).isOne();
        assertThat(filtered.getItems()).singleElement().extracting(StudyLogDto::getId).isEqualTo(newer.getId());
        assertThatThrownBy(() -> studyLogs.get("999999")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void rejectsInvalidStudyLogDateRange() {
        assertThatThrownBy(() -> studyLogs.list(student(), null, LocalDate.now(), LocalDate.now().minusDays(1),
                null, 1, 20)).isInstanceOf(BusinessException.class);
    }

    @Test void updatesStudyLogTwiceWithRealOptimisticVersionIncrements() {
        StudyLogDto created = studyLogs.create(studyLog(student(), null));
        StudyLogDto first = studyLogs.update(created.getId(), update(created).content("first update"));
        StudyLogDto second = studyLogs.update(first.getId(), update(first).content("second update"));
        assertThat(first.getVersion()).isOne();
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(second.getContent()).isEqualTo("second update");
    }

    @Test void rejectsStaleStudyLogVersionWithConflictCode() {
        StudyLogDto created = studyLogs.create(studyLog(student(), null));
        StudyLogUpdateRequest stale = update(created);
        studyLogs.update(created.getId(), stale);
        assertThatThrownBy(() -> studyLogs.update(created.getId(), stale)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo("DATA_VERSION_CONFLICT"));
    }

    @Test void revalidatesSubjectAndFutureDateOnStudyLogUpdate() {
        StudyLogDto created = studyLogs.create(studyLog(student(), null));
        assertThatThrownBy(() -> studyLogs.update(created.getId(), update(created).subjectId(disabledSubject())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> studyLogs.update(created.getId(), update(created)
                .studyDate(LocalDate.now().plusDays(1)))).isInstanceOf(BusinessException.class);
    }

    @Test void logicallyDeletesStudyLogAndHidesItFromGetAndList() {
        StudyLogDto created = studyLogs.create(studyLog(student(), null));
        studyLogs.delete(created.getId());
        Integer deleted = jdbc.queryForObject("select deleted from study_log where id=?", Integer.class,
                Long.valueOf(created.getId()));
        assertThat(deleted).isOne();
        assertThatThrownBy(() -> studyLogs.get(created.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(studyLogs.list(created.getStudentId(), null, null, null, null, 1, 20).getItems())
                .extracting(StudyLogDto::getId).doesNotContain(created.getId());
    }

    @Test void studyLogApiReturnsRequestIdStringIdsAndUnified404409422Errors() throws Exception {
        StudyLogDto created = studyLogs.create(studyLog(student(), subject()));
        mvc.perform(get("/api/v1/study-logs/{id}", created.getId()).header("X-Request-ID", "stage7-log"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "stage7-log"))
                .andExpect(jsonPath("$.requestId").value("stage7-log"))
                .andExpect(jsonPath("$.data.id").isString()).andExpect(jsonPath("$.data.studentId").isString())
                .andExpect(jsonPath("$.data.subjectId").isString());
        mvc.perform(get("/api/v1/study-logs/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/study-logs").contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors").isArray());
        studyLogs.update(created.getId(), update(created).content("advance version"));
        String stale = "{\"studentId\":\"" + created.getStudentId() + "\",\"subjectId\":\""
                + created.getSubjectId() + "\",\"studyDate\":\"" + created.getStudyDate()
                + "\",\"durationSeconds\":600,\"content\":\"stale\",\"version\":0}";
        mvc.perform(put("/api/v1/study-logs/{id}", created.getId()).contentType("application/json").content(stale))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_VERSION_CONFLICT"));
    }

    @Test void studyLogCreateUpdateDeleteDoNotChangeMastery() {
        int masteryBefore = count("student_mastery"), historyBefore = count("mastery_history");
        StudyLogDto created = studyLogs.create(studyLog(student(), subject()));
        StudyLogDto updated = studyLogs.update(created.getId(), update(created).content("updated"));
        studyLogs.delete(updated.getId());
        assertThat(count("student_mastery")).isEqualTo(masteryBefore);
        assertThat(count("mastery_history")).isEqualTo(historyBefore);
    }

    private ResourceCreate resource(String subjectId, List<String> knowledgeIds) {
        return new ResourceCreate().title("resource " + UUID.randomUUID()).resourceType("VIDEO").sourceType("LOCAL")
                .subjectId(subjectId).durationSeconds(600).difficulty(3).status(ResourceStatus.WAITING)
                .description("stage seven resource").tags(List.of("stage7")).knowledgeIds(knowledgeIds);
    }

    private ResourceUpdate update(Resource resource, String subjectId, List<String> knowledgeIds) {
        return new ResourceUpdate().title(resource.getTitle()).resourceType(resource.getResourceType())
                .sourceType(resource.getSourceType()).sourceUrl(resource.getSourceUrl()).subjectId(subjectId)
                .durationSeconds(resource.getDurationSeconds()).difficulty(resource.getDifficulty())
                .status(resource.getStatus()).description(resource.getDescription()).tags(resource.getTags())
                .knowledgeIds(knowledgeIds).version(resource.getVersion());
    }

    private ResourceHistoryCreateRequest history(String studentId, String progress, boolean completed) {
        return new ResourceHistoryCreateRequest().studentId(studentId).durationSeconds(120)
                .progressPercent(new BigDecimal(progress)).completed(completed);
    }

    private StudyLogCreateRequest studyLog(String studentId, String subjectId) {
        return new StudyLogCreateRequest().studentId(studentId).subjectId(subjectId).studyDate(LocalDate.now())
                .durationSeconds(600).content("stage seven study").remark("integration test");
    }

    private StudyLogUpdateRequest update(StudyLogDto log) {
        return new StudyLogUpdateRequest().studentId(log.getStudentId()).subjectId(log.getSubjectId())
                .studyDate(log.getStudyDate()).durationSeconds(log.getDurationSeconds()).content(log.getContent())
                .remark(log.getRemark()).version(log.getVersion());
    }

    private String student() {
        return students.create(new StudentCreate().name("stage7 " + UUID.randomUUID())
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

    private String disabledSubject() {
        SubjectEntity subject = new SubjectEntity();
        subject.setCode("S7-" + UUID.randomUUID().toString().substring(0, 20));
        subject.setName("Disabled " + UUID.randomUUID());
        subject.setSortOrder(999);
        subject.setEnabled(false);
        subjects.insert(subject);
        return subject.getId().toString();
    }

    private KnowledgeNodeReferenceEntity node(String subjectId, boolean enabled) {
        KnowledgeNodeReferenceEntity node = new KnowledgeNodeReferenceEntity();
        node.setNodeCode("S7-" + UUID.randomUUID());
        node.setName("Stage Seven Knowledge");
        node.setNodeType("KNOWLEDGE_POINT");
        node.setSubjectId(Long.valueOf(subjectId));
        node.setEnabled(enabled);
        node.setDeleted(false);
        nodes.insert(node);
        return node;
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}

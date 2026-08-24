package com.stdntedu.stage3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.base.service.BaseDataService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.DataConflictException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.AcademicTermCreateRequest;
import com.stdntedu.generated.model.AcademicTermDto;
import com.stdntedu.generated.model.AcademicTermUpdateRequest;
import com.stdntedu.generated.model.SemesterType;
import com.stdntedu.generated.model.StageDto;
import com.stdntedu.generated.model.Student;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudentUpdate;
import com.stdntedu.student.service.AcademicTermService;
import com.stdntedu.student.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StageThreeIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth")
            .withUsername("student_growth")
            .withPassword("student_growth");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private BaseDataService baseData;
    @Autowired private StudentService students;
    @Autowired private AcademicTermService terms;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Environment environment;

    @Test
    void localV1BindsServerToLoopback() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void localV1ContractHasNoBearerAuthAndPublicApiNeedsNoAuthorization() throws Exception {
        String contract = Files.readString(Path.of("api", "openapi.yaml"), StandardCharsets.UTF_8);
        assertThat(contract).doesNotContain("bearerAuth", "#/components/responses/Unauthorized",
                "#/components/responses/Forbidden");
        mockMvc.perform(get("/api/v1/stages")).andExpect(status().isOk());
    }

    @Test
    void listsEnabledBaseDataInStableOrder() {
        assertThat(baseData.listStages(null, true)).hasSize(3);
        StageDto primary = baseData.listStages(null, true).stream()
                .filter(stage -> "PRIMARY".equals(stage.getCode())).findFirst().orElseThrow();
        assertThat(baseData.listGrades(primary.getId(), true)).hasSize(6);
        assertThat(baseData.listSubjects(true)).hasSize(17);
        assertThat(baseData.listDictionaryItems("question_type", true)).hasSize(14);
    }

    @Test
    void rejectsMissingDictionaryType() {
        assertThatThrownBy(() -> baseData.listDictionaryItems("missing", true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createsStudentWithGeneratedCodeAndValidatesStageGrade() {
        Student student = students.create(studentCreate("  Alice  ", "1", "1"));
        assertThat(student.getId()).matches("[0-9]+");
        assertThat(student.getStudentCode()).matches("STU[0-9]{8}[0-9]{6}");
        assertThat(student.getName()).isEqualTo("Alice");

        assertThatThrownBy(() -> students.create(studentCreate("Mismatch", "1", "7")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grade does not belong to stage");
        assertThatThrownBy(() -> students.create(studentCreate("Future", "1", "1").birthday(LocalDate.now().plusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("birthday cannot be in the future");
    }

    @Test
    void updatesStudentWithOptimisticVersion() {
        Student student = students.create(studentCreate("Bob", "1", "1"));
        StudentUpdate update = new StudentUpdate().name(" Bob Updated ").currentStageId("1")
                .currentGradeId("1").version(student.getVersion());
        Student updated = students.update(student.getId(), update);
        assertThat(updated.getName()).isEqualTo("Bob Updated");
        assertThat(updated.getVersion()).isEqualTo(student.getVersion() + 1);

        assertThatThrownBy(() -> students.update(student.getId(), update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("student version conflict");
    }

    @Test
    void createsTermsEnforcesUniquenessAndSwitchesCurrent() {
        Student student = students.create(studentCreate("Term Student", "1", "1"));
        AcademicTermCreateRequest first = termCreate(student.getId(), "2025-2026", true);
        AcademicTermDto firstCreated = terms.create(first);
        assertThat(firstCreated.getCurrent()).isTrue();

        assertThatThrownBy(() -> terms.create(termCreate(student.getId(), "2025-2026", false)))
                .isInstanceOf(DataConflictException.class);

        AcademicTermDto second = terms.create(termCreate(student.getId(), "2026-2027", true));
        assertThat(second.getCurrent()).isTrue();
        assertThat(terms.list(student.getId(), true)).extracting(AcademicTermDto::getId)
                .containsExactly(second.getId());
        assertThat(terms.list(student.getId(), false)).extracting(AcademicTermDto::getId)
                .contains(firstCreated.getId(), second.getId());
    }

    @Test
    void rejectsInvalidTermDateAndUnknownStudent() {
        assertThatThrownBy(() -> terms.create(termCreate("999999", "2026-2027", false)))
                .isInstanceOf(ResourceNotFoundException.class);
        Student student = students.create(studentCreate("Date Student", "1", "1"));
        assertThatThrownBy(() -> terms.create(termCreate(student.getId(), "2027-2028", false)
                .startDate(LocalDate.of(2027, 9, 1)).endDate(LocalDate.of(2027, 8, 31))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endDate cannot be before startDate");
    }

    @Test
    void exposesUnifiedResponseRequestIdAndStringIds() throws Exception {
        mockMvc.perform(get("/api/v1/stages").header("X-Request-ID", "stage3-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "stage3-test"))
                .andExpect(jsonPath("$.requestId").value("stage3-test"))
                .andExpect(jsonPath("$.data[0].id").isString());
        mockMvc.perform(get("/api/v1/dictionaries/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void missingDictionaryEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/dictionaries/unknown_type"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void staleStudentVersionReturns409WithDataVersionConflict() {
        Student student = students.create(studentCreate("Stale Student", "1", "1"));
        StudentUpdate firstUpdate = new StudentUpdate().name("First Update").currentStageId("1")
                .currentGradeId("1").version(student.getVersion());
        students.update(student.getId(), firstUpdate);

        BusinessException conflict = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> students.update(student.getId(), firstUpdate), BusinessException.class);
        assertThat(conflict.getCode()).isEqualTo("DATA_VERSION_CONFLICT");
        assertThat(conflict.getStatus().value()).isEqualTo(409);
    }

    @Test
    void staleAcademicTermVersionReturns409() {
        Student student = students.create(studentCreate("Stale Term Student", "1", "1"));
        AcademicTermDto term = terms.create(termCreate(student.getId(), "2030-2031", false));
        AcademicTermUpdateRequest firstUpdate = new AcademicTermUpdateRequest().studentId(student.getId())
                .academicYear("2031-2032").semester(SemesterType.FIRST).stageId("1").gradeId("1")
                .startDate(LocalDate.of(2031, 9, 1)).endDate(LocalDate.of(2032, 1, 31))
                .current(false).version(term.getVersion());
        terms.update(term.getId(), firstUpdate);

        BusinessException conflict = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> terms.update(term.getId(), firstUpdate), BusinessException.class);
        assertThat(conflict.getCode()).isEqualTo("DATA_VERSION_CONFLICT");
        assertThat(conflict.getStatus().value()).isEqualTo(409);
    }

    @Test
    void currentOnlyReturnsOnlyCurrentAcademicTerm() {
        Student student = students.create(studentCreate("Current Filter Student", "1", "1"));
        AcademicTermDto oldTerm = terms.create(termCreate(student.getId(), "2032-2033", false));
        AcademicTermDto currentTerm = terms.create(termCreate(student.getId(), "2033-2034", true));

        assertThat(terms.list(student.getId(), true)).extracting(AcademicTermDto::getId)
                .containsExactly(currentTerm.getId()).doesNotContain(oldTerm.getId());
    }

    @Test
    void concurrentCurrentTermCreatesLeaveExactlyOneCurrentTerm() throws Exception {
        Student student = students.create(studentCreate("Concurrent Term Student", "1", "1"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> createCurrentTermConcurrently(
                    termCreate(student.getId(), "2040-2041", true), ready, start));
            var second = executor.submit(() -> createCurrentTermConcurrently(
                    termCreate(student.getId(), "2041-2042", true), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).getCurrent()).isTrue();
            assertThat(second.get(30, TimeUnit.SECONDS).getCurrent()).isTrue();
        } finally {
            executor.shutdownNow();
        }

        Integer currentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic_term WHERE student_id = ? AND is_current = 1 AND deleted = 0",
                Integer.class, Long.valueOf(student.getId()));
        assertThat(currentCount).isEqualTo(1);
        assertThat(terms.list(student.getId(), true)).hasSize(1);
    }

    @Test
    void requestIdHeaderMatchesResponseBody() throws Exception {
        mockMvc.perform(get("/api/v1/subjects").header("X-Request-ID", "request-id-acceptance"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "request-id-acceptance"))
                .andExpect(jsonPath("$.requestId").value("request-id-acceptance"));
    }

    @Test
    void generatedRequestIdMatchesErrorResponseBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dictionaries/missing-generated-request-id"))
                .andExpect(status().isNotFound()).andReturn();
        assertRequestIdMatches(result, null);
    }

    @Test
    void suppliedRequestIdMatchesErrorResponseBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dictionaries/missing-supplied-request-id")
                        .header("X-Request-ID", "stage11a-supplied-request-id"))
                .andExpect(status().isNotFound()).andReturn();
        assertRequestIdMatches(result, "stage11a-supplied-request-id");
    }

    @Test
    void zeroApiIdReturnsBadRequest() throws Exception {
        assertInvalidApiId("0");
    }

    @Test
    void negativeApiIdReturnsBadRequest() throws Exception {
        assertInvalidApiId("-1");
    }

    @Test
    void nonNumericApiIdReturnsBadRequest() throws Exception {
        assertInvalidApiId("not-number");
    }

    @Test
    void overflowingApiIdReturnsBadRequest() throws Exception {
        assertInvalidApiId("9223372036854775808");
    }

    @Test
    void coreApiIdsAreJsonStrings() throws Exception {
        Student student = students.create(studentCreate("String Id Student", "1", "1"));
        terms.create(termCreate(student.getId(), "2034-2035", true));

        mockMvc.perform(get("/api/v1/stages")).andExpect(jsonPath("$.data[0].id").isString());
        mockMvc.perform(get("/api/v1/grades")).andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].stageId").isString());
        mockMvc.perform(get("/api/v1/subjects")).andExpect(jsonPath("$.data[0].id").isString());
        mockMvc.perform(get("/api/v1/dictionaries/question_type"))
                .andExpect(jsonPath("$.data[0].id").isString());
        mockMvc.perform(get("/api/v1/students")).andExpect(jsonPath("$.data[0].id").isString());
        mockMvc.perform(get("/api/v1/academic-terms").param("studentId", student.getId()))
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].studentId").isString())
                .andExpect(jsonPath("$.data[0].stageId").isString())
                .andExpect(jsonPath("$.data[0].gradeId").isString());
    }

    @Test
    void validationErrorContainsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors").isArray())
                .andExpect(jsonPath("$.data.fieldErrors.length()").value(3));
    }

    @Test
    void gradeOutsideStageReturnsBusinessValidationError() {
        BusinessException validation = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> students.create(studentCreate("Invalid Grade Student", "1", "7")), BusinessException.class);
        assertThat(validation.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(validation.getStatus().value()).isEqualTo(422);
    }

    private StudentCreate studentCreate(String name, String stageId, String gradeId) {
        return new StudentCreate().name(name).currentStageId(stageId).currentGradeId(gradeId);
    }

    private AcademicTermCreateRequest termCreate(String studentId, String academicYear, boolean current) {
        return new AcademicTermCreateRequest().studentId(studentId).academicYear(academicYear)
                .semester(SemesterType.FIRST).stageId("1").gradeId("1")
                .startDate(LocalDate.of(Integer.parseInt(academicYear.substring(0, 4)), 9, 1))
                .endDate(LocalDate.of(Integer.parseInt(academicYear.substring(5), 10), 1, 31)).current(current);
    }

    private AcademicTermDto createCurrentTermConcurrently(AcademicTermCreateRequest request, CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent start timed out");
        return terms.create(request);
    }

    private void assertRequestIdMatches(MvcResult result, String expectedRequestId) throws Exception {
        String headerRequestId = result.getResponse().getHeader("X-Request-ID");
        String bodyRequestId = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("requestId").asText();
        assertThat(headerRequestId).isNotBlank().isEqualTo(bodyRequestId);
        if (expectedRequestId != null) assertThat(headerRequestId).isEqualTo(expectedRequestId);
    }

    private void assertInvalidApiId(String id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/students/{studentId}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data.fieldErrors").isArray())
                .andReturn();
        assertRequestIdMatches(result, null);
    }
}

package com.stdntedu.stage3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
    void requestIdHeaderMatchesResponseBody() throws Exception {
        mockMvc.perform(get("/api/v1/subjects").header("X-Request-ID", "request-id-acceptance"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "request-id-acceptance"))
                .andExpect(jsonPath("$.requestId").value("request-id-acceptance"));
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
}

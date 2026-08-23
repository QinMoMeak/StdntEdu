package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.stdntedu.ai.analysis.converter.AiAnalysisConverter;
import com.stdntedu.ai.analysis.entity.AiAnalysisEntity;
import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.ai.analysis.projection.AiAnalysisRow;
import com.stdntedu.ai.analysis.service.AiAnalysisService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.AiAnalysisBusinessType;
import com.stdntedu.generated.model.AiAnalysisDto;
import com.stdntedu.generated.model.AiAnalysisPageResponseAllOfData;
import com.stdntedu.generated.model.AiTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
class StageTenCAiAnalysisIntegrationTest {
    private static final ZoneId SYSTEM_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String INPUT_JSON = """
            {"schemaVersion":"1","promptVersion":"study-plan-generation-v1","request":{}}
            """;

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

    @Autowired AiAnalysisService analyses;
    @Autowired AiAnalysisConverter converter;
    @Autowired AiAnalysisMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private long studentId;
    private long modelOne;
    private long modelTwo;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.update("DELETE FROM ai_analysis");
        jdbc.update("DELETE FROM ai_model");
        jdbc.update("DELETE FROM student");
        studentId = student("Stage10C Student");
        modelOne = model("analysis-one");
        modelTwo = model("analysis-two");
    }

    @Test
    void scenarios01_02_emptyListAndMissingDetailUseFrozenResponses() throws Exception {
        AiAnalysisPageResponseAllOfData empty = analyses.list(null, null, null, null, null,
                null, null, 1, 20);
        assertThat(empty.getItems()).isEmpty();
        assertThat(empty.getTotal()).isZero();
        assertThat(empty.getTotalPages()).isZero();
        assertThatThrownBy(() -> analyses.get("999999999"))
                .isInstanceOf(ResourceNotFoundException.class);

        mvc.perform(get("/api/v1/ai/analyses/{analysisId}", "999999999")
                        .header("X-Request-ID", "stage10c-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-ID", "stage10c-not-found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("stage10c-not-found"));
    }

    @Test
    void scenarios03_08_allLifecycleStatesSnapshotLongDurationAndModelJoin() {
        LocalDateTime created = LocalDateTime.of(2026, 8, 19, 9, 0);
        long pending = analysis(modelOne, AiTaskStatus.PENDING, created, null);
        long running = analysis(modelOne, AiTaskStatus.RUNNING, created.plusSeconds(1), null);
        long success = analysis(modelOne, AiTaskStatus.SUCCESS, created.plusSeconds(2), snapshot("9001"));
        long failed = analysis(modelOne, AiTaskStatus.FAILED, created.plusSeconds(3), null);

        AiAnalysisDto pendingDto = analyses.get(Long.toString(pending));
        AiAnalysisDto runningDto = analyses.get(Long.toString(running));
        AiAnalysisDto successDto = analyses.get(Long.toString(success));
        AiAnalysisDto failedDto = analyses.get(Long.toString(failed));
        assertThat(pendingDto.getStatus()).isEqualTo(AiTaskStatus.PENDING);
        assertThat(runningDto.getStatus()).isEqualTo(AiTaskStatus.RUNNING);
        assertThat(successDto.getResult().getId()).isEqualTo("9001");
        assertThat(successDto.getResult().getTitle()).isEqualTo("Historical snapshot");
        assertThat(failedDto.getErrorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(failedDto.getErrorMessage()).isEqualTo("provider request failed safely");
        assertThat(successDto.getDurationMs()).isEqualTo(4_294_967_296L);
        assertThat(successDto.getModelName()).isEqualTo(modelName(modelOne));
    }

    @Test
    void scenarios09_12_filtersAreExactAndDoNotRequireReferenceExistence() {
        long otherStudent = student("Other Student");
        long first = analysis(modelOne, AiTaskStatus.PENDING, LocalDateTime.of(2026, 8, 19, 9, 0), null);
        analysisFor(otherStudent, modelTwo, AiTaskStatus.SUCCESS,
                LocalDateTime.of(2026, 8, 19, 10, 0), snapshot("9100"));

        assertIds(analyses.list(Long.toString(studentId), null, null, null, null,
                null, null, 1, 20), first);
        assertIds(analyses.list(null, null, null, Long.toString(modelOne), null,
                null, null, 1, 20), first);
        assertIds(analyses.list(null, null, null, null, AiTaskStatus.PENDING,
                null, null, 1, 20), first);
        assertIds(analyses.list(null, AiAnalysisBusinessType.STUDY_PLAN_GENERATION, null, null, null,
                null, null, 1, 20), first, first + 1);
        assertThat(analyses.list("999999999", null, null, null, null,
                null, null, 1, 20).getItems()).isEmpty();
        assertThat(analyses.list(null, null, null, "999999999", null,
                null, null, 1, 20).getItems()).isEmpty();
    }

    @Test
    void scenarios13_14_businessIdRulesReturn422() throws Exception {
        assertRule(() -> analyses.list(null, null, "1", null, null, null, null, 1, 20));
        assertRule(() -> analyses.list(null, AiAnalysisBusinessType.STUDY_PLAN_GENERATION,
                "1", null, null, null, null, 1, 20));

        mvc.perform(get("/api/v1/ai/analyses")
                        .param("businessId", "1").header("X-Request-ID", "stage10c-business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.requestId").value("stage10c-business"));
    }

    @Test
    void scenarios15_18_timeRangeUsesInclusiveStartExclusiveEndAndRejectsInvalidRange() {
        LocalDateTime boundary = LocalDateTime.of(2026, 8, 19, 11, 0);
        long before = analysis(modelOne, AiTaskStatus.PENDING, boundary.minusNanos(1_000_000), null);
        long atStart = analysis(modelOne, AiTaskStatus.PENDING, boundary, null);
        long inside = analysis(modelOne, AiTaskStatus.PENDING, boundary.plusSeconds(1), null);
        long atEnd = analysis(modelOne, AiTaskStatus.PENDING, boundary.plusSeconds(2), null);
        OffsetDateTime start = boundary.atZone(SYSTEM_ZONE).toOffsetDateTime();
        OffsetDateTime end = boundary.plusSeconds(2).atZone(SYSTEM_ZONE).toOffsetDateTime();
        assertIds(analyses.list(null, null, null, null, null, start, end, 1, 20), atStart, inside);
        assertThat(before).isPositive();
        assertThat(atEnd).isPositive();
        assertRule(() -> analyses.list(null, null, null, null, null, start, start, 1, 20));
        assertRule(() -> analyses.list(null, null, null, null, null, end, start, 1, 20));
    }

    @Test
    void scenarios19_22_sortingStablePaginationAndMetadata() {
        LocalDateTime same = LocalDateTime.of(2026, 8, 19, 12, 0);
        long older = analysis(modelOne, AiTaskStatus.PENDING, same.minusSeconds(1), null);
        long sameLow = analysis(modelOne, AiTaskStatus.PENDING, same, null);
        long sameHigh = analysis(modelOne, AiTaskStatus.PENDING, same, null);
        AiAnalysisPageResponseAllOfData first = analyses.list(null, null, null, null, null,
                null, null, 1, 2);
        AiAnalysisPageResponseAllOfData second = analyses.list(null, null, null, null, null,
                null, null, 2, 2);
        assertThat(first.getItems()).extracting(AiAnalysisDto::getId)
                .containsExactly(Long.toString(sameHigh), Long.toString(sameLow));
        assertThat(second.getItems()).extracting(AiAnalysisDto::getId)
                .containsExactly(Long.toString(older));
        assertThat(first.getPage()).isEqualTo(1);
        assertThat(first.getPageSize()).isEqualTo(2);
        assertThat(first.getTotal()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
    }

    @Test
    void scenarios23_35_snapshotIdsNullabilityTokensAndTimesAreExact() throws Exception {
        LocalDateTime created = LocalDateTime.of(2026, 8, 19, 13, 0, 0, 123_000_000);
        long pending = analysis(modelOne, AiTaskStatus.PENDING, created, null);
        long running = analysis(modelOne, AiTaskStatus.RUNNING, created.plusSeconds(1), null);
        long success = analysis(modelOne, AiTaskStatus.SUCCESS, created.plusSeconds(2), snapshot("9223372036854775806"));
        long failed = analysis(modelOne, AiTaskStatus.FAILED, created.plusSeconds(3), null);

        jdbc.update("UPDATE ai_analysis SET prompt_tokens=NULL, completion_tokens=NULL WHERE id=?", pending);
        AiAnalysisDto pendingDto = analyses.get(Long.toString(pending));
        AiAnalysisDto successDto = analyses.get(Long.toString(success));
        assertThat(pendingDto.getResult()).isNull();
        assertThat(analyses.get(Long.toString(running)).getResult()).isNull();
        assertThat(analyses.get(Long.toString(failed)).getResult()).isNull();
        assertThat(pendingDto.getPromptTokens()).isNull();
        assertThat(pendingDto.getCompletionTokens()).isNull();
        assertThat(pendingDto.getEstimatedCost()).isNull();
        assertThat(pendingDto.getCurrencyCode()).isNull();
        assertThat(pendingDto.getCreatedAt()).isEqualTo(created.atZone(SYSTEM_ZONE).toOffsetDateTime());
        assertThat(successDto.getStartedAt()).isEqualTo(created.plusSeconds(1).atZone(SYSTEM_ZONE).toOffsetDateTime());
        assertThat(successDto.getFinishedAt()).isEqualTo(created.plusSeconds(2).atZone(SYSTEM_ZONE).toOffsetDateTime());
        assertThat(successDto.getResult().getId()).isEqualTo("9223372036854775806");
        assertThat(successDto.getResult().getStudentId()).isEqualTo(Long.toString(studentId));

        mvc.perform(get("/api/v1/ai/analyses/{analysisId}", success)
                        .header("X-Request-ID", "stage10c-success"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "stage10c-success"))
                .andExpect(jsonPath("$.requestId").value("stage10c-success"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.modelId").isString())
                .andExpect(jsonPath("$.data.result.id").isString())
                .andExpect(jsonPath("$.data.result.studentId").isString());
    }

    @Test
    void scenarios27_28_corruptSuccessSnapshotsBecomeGenericInternalErrors() throws Exception {
        AiAnalysisRow nullSnapshot = row(AiTaskStatus.SUCCESS, null);
        assertThatThrownBy(() -> converter.toDto(nullSnapshot, SYSTEM_ZONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI analysis result snapshot is invalid");

        long malformed = analysis(modelOne, AiTaskStatus.SUCCESS,
                LocalDateTime.of(2026, 8, 19, 14, 0), "[]");
        mvc.perform(get("/api/v1/ai/analyses/{analysisId}", malformed)
                        .header("X-Request-ID", "stage10c-corrupt"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("internal server error"))
                .andExpect(jsonPath("$.requestId").value("stage10c-corrupt"))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("[]"))));
    }

    @Test
    void scenario32_converterPreservesPersistedCostAndCurrencyWithoutCalculating() {
        AiAnalysisRow row = row(AiTaskStatus.PENDING, null);
        row.setEstimatedCost(new BigDecimal("12.345678"));
        row.setCurrencyCode("CNY");
        AiAnalysisDto dto = converter.toDto(row, SYSTEM_ZONE);
        assertThat(dto.getEstimatedCost()).isEqualByComparingTo("12.345678");
        assertThat(dto.getCurrencyCode()).isEqualTo("CNY");
    }

    @Test
    void scenarios06_16_failedOutputIsRedactedAndInputJsonRemainsInternal() throws Exception {
        long failed = analysis(modelOne, AiTaskStatus.FAILED,
                LocalDateTime.of(2026, 8, 19, 15, 0), null);
        jdbc.update("UPDATE ai_analysis SET error_message=? WHERE id=?",
                "Authorization: Bearer top-secret; C:\\private\\provider.json", failed);
        mvc.perform(get("/api/v1/ai/analyses/{analysisId}", failed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorMessage").value("AI analysis failed (details redacted)"))
                .andExpect(jsonPath("$.data.inputSummary").value("persisted input summary"))
                .andExpect(jsonPath("$.data.inputJson").doesNotExist());

        AiAnalysisEntity entity = mapper.selectById(failed);
        assertThat(entity.getInputJson()).contains("study-plan-generation-v1");
        assertThat(entity.getIdempotencyKey()).startsWith("stage10c-");
        assertThat(entity.getRequestHash()).hasSize(64);
        assertThat(entity.getDurationMs()).isInstanceOf(Long.class);
    }

    @Test
    void scenarios36_43_queriesAreFixedShapeAndReadOnlyAcrossDomainTables() {
        analysis(modelOne, AiTaskStatus.SUCCESS, LocalDateTime.of(2026, 8, 19, 16, 0), snapshot("9400"));
        Map<String, String> before = state();
        analyses.list(null, null, null, null, null, null, null, 1, 20);
        analyses.get(jdbc.queryForObject("SELECT CAST(MAX(id) AS CHAR) FROM ai_analysis", String.class));
        assertThat(state()).isEqualTo(before);

        List<Map<String, Object>> plan = jdbc.queryForList("""
                EXPLAIN SELECT aa.id, am.model_name
                  FROM ai_analysis aa JOIN ai_model am ON am.id=aa.ai_model_id
                 WHERE aa.student_id=? AND aa.business_type='STUDY_PLAN_GENERATION'
                   AND aa.status='SUCCESS' AND aa.create_time>=?
                 ORDER BY aa.create_time DESC, aa.id DESC LIMIT 20
                """, studentId, LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(plan).isNotEmpty();
    }

    @Test
    void scenarios49_55_databaseAndGeneratedContractRemainAtFrozenBaseline() {
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("23");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'",
                Integer.class)).isEqualTo(47);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class)).isEqualTo(31);
        assertThat(AiAnalysisBusinessType.values()).containsExactly(AiAnalysisBusinessType.STUDY_PLAN_GENERATION);
        assertThat(AiTaskStatus.values()).contains(AiTaskStatus.PENDING, AiTaskStatus.RUNNING,
                AiTaskStatus.SUCCESS, AiTaskStatus.FAILED);
    }

    @Test
    void scenarioHttpListReturnsPageNullableFieldsAndStableRequestId() throws Exception {
        analysis(modelOne, AiTaskStatus.PENDING, LocalDateTime.of(2026, 8, 19, 17, 0), null);
        mvc.perform(get("/api/v1/ai/analyses")
                        .param("studentId", Long.toString(studentId))
                        .param("page", "1").param("pageSize", "10")
                        .header("X-Request-ID", "stage10c-list"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "stage10c-list"))
                .andExpect(jsonPath("$.requestId").value("stage10c-list"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").isString())
                .andExpect(jsonPath("$.data.items[0].result").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].estimatedCost").value(org.hamcrest.Matchers.nullValue()));
    }

    private long analysis(long modelId, AiTaskStatus status, LocalDateTime created, String resultJson) {
        return analysisFor(studentId, modelId, status, created, resultJson);
    }

    private long analysisFor(long student, long modelId, AiTaskStatus status, LocalDateTime created,
            String resultJson) {
        LocalDateTime started = status == AiTaskStatus.PENDING ? null : created.minusSeconds(1);
        LocalDateTime finished = status == AiTaskStatus.SUCCESS || status == AiTaskStatus.FAILED ? created : null;
        Long duration = null;
        if (status == AiTaskStatus.SUCCESS) duration = 4_294_967_296L;
        if (status == AiTaskStatus.FAILED) duration = 1_000L;
        String errorCode = status == AiTaskStatus.FAILED ? "PROVIDER_ERROR" : null;
        String errorMessage = status == AiTaskStatus.FAILED ? "provider request failed safely" : null;
        String key = "stage10c-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_analysis(student_id,business_type,business_id,ai_model_id,prompt_template_id,
                    status,input_summary,input_json,idempotency_key,request_hash,result_json,error_code,error_message,
                    prompt_tokens,completion_tokens,duration_ms,started_time,finished_time,estimated_cost,currency_code,
                    create_time)
                VALUES (?,'STUDY_PLAN_GENERATION',NULL,?,NULL,?,'persisted input summary',?,?,?,CAST(? AS JSON),
                    ?,?,NULL,NULL,?,?,?,NULL,NULL,?)
                """, student, modelId, status.name(), INPUT_JSON, key, "a".repeat(64), resultJson,
                errorCode, errorMessage, duration, started, finished, created);
        return jdbc.queryForObject("SELECT id FROM ai_analysis WHERE idempotency_key=?", Long.class, key);
    }

    private long student(String name) {
        String code = "S10C-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO student(student_code,name,deleted,version) VALUES (?,?,0,0)", code, name);
        return jdbc.queryForObject("SELECT id FROM student WHERE student_code=?", Long.class, code);
    }

    private long model(String suffix) {
        String modelName = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO ai_model(name,provider,model_type,model_name,protocol,auth_type,api_base_url,
                    supports_vision,supports_json,local_flag,enabled,priority_no,timeout_seconds,version)
                VALUES (?,'CUSTOM','CHAT',?,'OPENAI_COMPATIBLE','NONE','http://127.0.0.1:9',
                    0,1,1,1,100,2,0)
                """, "Stage10C " + suffix, modelName);
        return jdbc.queryForObject("SELECT id FROM ai_model WHERE model_name=?", Long.class, modelName);
    }

    private String modelName(long id) {
        return jdbc.queryForObject("SELECT model_name FROM ai_model WHERE id=?", String.class, id);
    }

    private String snapshot(String id) {
        return """
                {"id":"%s","studentId":"%s","title":"Historical snapshot","planType":"AI",
                 "startDate":"2026-08-20","endDate":"2026-08-26","status":"DRAFT","tasks":[],
                 "totalTaskCount":0,"completedTaskCount":0,"version":1,
                 "createdAt":"2026-08-19T10:00:00+08:00","updatedAt":"2026-08-19T10:00:00+08:00"}
                """.formatted(id, studentId);
    }

    private AiAnalysisRow row(AiTaskStatus status, String resultJson) {
        AiAnalysisRow row = new AiAnalysisRow();
        row.setId(1L);
        row.setStudentId(studentId);
        row.setBusinessType(AiAnalysisBusinessType.STUDY_PLAN_GENERATION);
        row.setAiModelId(modelOne);
        row.setModelName("projection model");
        row.setStatus(status);
        row.setResultJson(resultJson);
        row.setCreateTime(LocalDateTime.of(2026, 8, 19, 8, 0));
        return row;
    }

    private void assertIds(AiAnalysisPageResponseAllOfData page, long... expected) {
        assertThat(page.getItems()).extracting(AiAnalysisDto::getId)
                .containsExactlyInAnyOrder(java.util.Arrays.stream(expected).mapToObj(Long::toString).toArray(String[]::new));
    }

    private void assertRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        });
    }

    private Map<String, String> state() {
        Map<String, String> state = new LinkedHashMap<>();
        for (String table : List.of("ai_analysis", "study_plan", "study_plan_task", "ai_model", "ai_secret",
                "ai_extraction_task", "wrong_question", "student_mastery", "mastery_history")) {
            state.put(table, jdbc.queryForObject(
                    "SELECT CONCAT(COUNT(*), ':', COALESCE(SUM(id),0)) FROM " + table, String.class));
        }
        return state;
    }
}

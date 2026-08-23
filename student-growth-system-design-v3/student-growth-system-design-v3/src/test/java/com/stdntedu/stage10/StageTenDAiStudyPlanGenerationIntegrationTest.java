package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.stdntedu.ai.analysis.entity.AiAnalysisEntity;
import com.stdntedu.ai.analysis.generation.AiStudyPlanGenerationDispatcher;
import com.stdntedu.ai.analysis.generation.AiStudyPlanGenerationRecovery;
import com.stdntedu.ai.analysis.generation.AiStudyPlanGenerationService;
import com.stdntedu.ai.analysis.generation.AiStudyPlanGenerationWorker;
import com.stdntedu.ai.analysis.generation.GenerationFailure;
import com.stdntedu.ai.analysis.generation.StudyPlanGenerationInputCodec;
import com.stdntedu.ai.analysis.generation.StudyPlanGenerationProposalParser;
import com.stdntedu.ai.analysis.generation.model.NormalizedStudyPlanGenerationRequest;
import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.ai.analysis.service.AiAnalysisService;
import com.stdntedu.ai.model.service.AiSecretService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.AiAnalysisDto;
import com.stdntedu.generated.model.AiModelType;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.StudyPlanDto;
import com.stdntedu.generated.model.StudyPlanGenerateRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
class StageTenDAiStudyPlanGenerationIntegrationTest {
    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(
            "stage10d-master-key-32-bytes!!xx".getBytes(StandardCharsets.UTF_8));
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.SUCCESS);
    private static final AtomicReference<String> PROPOSAL = new AtomicReference<>();
    private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_AUTH = new AtomicReference<>();
    private static final AtomicInteger PROVIDER_CALLS = new AtomicInteger();
    private static volatile CountDownLatch providerEntered;
    private static volatile CountDownLatch releaseProvider;
    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static int port;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("STDNTEDU_AI_SECRET_MASTER_KEY", () -> MASTER_KEY);
        registry.add("app.ai.provider.max-response-bytes", () -> 2048);
        registry.add("app.ai.extraction.pending-rescan.initial-delay-ms", () -> 3600000);
        registry.add("app.ai.study-plan.pending-rescan.initial-delay-ms", () -> 3600000);
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/", StageTenDAiStudyPlanGenerationIntegrationTest::respond);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Autowired AiStudyPlanGenerationService generation;
    @Autowired AiStudyPlanGenerationWorker worker;
    @Autowired AiStudyPlanGenerationRecovery recovery;
    @Autowired AiAnalysisMapper analysisMapper;
    @Autowired AiAnalysisService analysisQueries;
    @Autowired StudyPlanGenerationInputCodec inputCodec;
    @Autowired StudyPlanGenerationProposalParser proposalParser;
    @Autowired AiSecretService secrets;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired @Qualifier("aiStudyPlanExecutor") ThreadPoolTaskExecutor taskExecutor;

    private long studentId;
    private long modelId;
    private long subjectId;
    private long gradeId;

    @BeforeEach
    void cleanAndSeed() {
        release();
        awaitExecutorIdle();
        deleteInOrder();
        resetProvider();
        subjectId = jdbc.queryForObject("SELECT id FROM subject WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
        gradeId = jdbc.queryForObject("SELECT id FROM grade WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
        studentId = student("Stage10D Student");
        modelId = model("OPENAI_COMPATIBLE", AiModelType.CHAT, true, "NONE", null, 3);
        PROPOSAL.set(readingProposal(LocalDate.of(2026, 9, 1)));
    }

    @AfterEach
    void finishWorkers() {
        release();
        awaitExecutorIdle();
    }

    @Test
    void scenarios01_09_httpAcceptsPendingAndPersistsAuthoritativeSafeInputAfterCommit() throws Exception {
        delayProvider();
        var result = mvc.perform(post("/api/v1/study-plans/generate")
                        .header("Idempotency-Key", "stage10d-http-accept-001")
                        .header("X-Request-ID", "stage10d-request")
                        .contentType("application/json").content(objectMapper.writeValueAsBytes(request(modelId))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Request-ID", "stage10d-request"))
                .andExpect(jsonPath("$.requestId").value("stage10d-request"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.businessType").value("STUDY_PLAN_GENERATION"))
                .andExpect(jsonPath("$.data.businessId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.studentId").value(Long.toString(studentId)))
                .andExpect(jsonPath("$.data.modelId").value(Long.toString(modelId))).andReturn();
        String analysisId = JSON.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asText();
        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(analysisStatus(Long.parseLong(analysisId))).isEqualTo("RUNNING");
        String input = jdbc.queryForObject("SELECT CAST(input_json AS CHAR) FROM ai_analysis WHERE id=?",
                String.class, Long.parseLong(analysisId));
        JsonNode persistedInput = JSON.readTree(input);
        assertThat(persistedInput.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(input).contains("study-plan-generation-v1")
                .doesNotContain("Idempotency-Key", "Authorization", "requestId", "secret", "storage_path");
        assertThat(jdbc.queryForObject("SELECT input_summary FROM ai_analysis WHERE id=?", String.class,
                Long.parseLong(analysisId))).contains("AI study plan");
        release();
        awaitStatus(Long.parseLong(analysisId), "SUCCESS");
    }

    @Test
    void scenarios10_12_idempotentReplayUsesStableCanonicalHashAndConflictIs409() {
        delayProvider();
        StudyPlanGenerateRequest firstRequest = request(modelId).subjectIds(List.of(Long.toString(subjectId)));
        AiAnalysisDto first = generation.generate("stage10d-idempotent-001", firstRequest);
        AiAnalysisDto replay = generation.generate("stage10d-idempotent-001",
                request(modelId).subjectIds(List.of(Long.toString(subjectId))));
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(count("ai_analysis")).isEqualTo(1);
        String hash = jdbc.queryForObject("SELECT request_hash FROM ai_analysis", String.class);
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThatThrownBy(() -> generation.generate("stage10d-idempotent-001",
                request(modelId).dailyAvailableMinutes(61)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(count("ai_analysis")).isEqualTo(1);
        release();
        awaitStatus(Long.parseLong(first.getId()), "SUCCESS");
        jdbc.update("UPDATE ai_model SET enabled=0 WHERE id=?", modelId);
        assertThat(generation.generate("stage10d-idempotent-001", firstRequest).getId()).isEqualTo(first.getId());
    }

    @Test
    void scenario13_concurrentSameKeyCreatesOneAnalysisAndOneWorker() throws Exception {
        delayProvider();
        int callers = 6;
        CyclicBarrier barrier = new CyclicBarrier(callers);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
        for (int index = 0; index < callers; index++) {
            futures.add(pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return generation.generate("stage10d-concurrent-001", request(modelId)).getId();
            }));
        }
        Set<String> ids = new java.util.HashSet<>();
        for (var future : futures) ids.add(future.get(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertThat(ids).hasSize(1);
        assertThat(count("ai_analysis")).isEqualTo(1);
        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(PROVIDER_CALLS.get()).isEqualTo(1);
        release();
        awaitStatus(Long.parseLong(ids.iterator().next()), "SUCCESS");
        assertThat(count("study_plan")).isEqualTo(1);
    }

    @Test
    void scenarios14_16_invalidModelAndStudentNeverCreateAnalysis() {
        long disabled = model("OPENAI_COMPATIBLE", AiModelType.CHAT, false, "NONE", null, 3);
        long embedding = model("OPENAI_COMPATIBLE", AiModelType.EMBEDDING, true, "NONE", null, 3);
        assertThatThrownBy(() -> generation.generate("stage10d-disabled-001", request(disabled)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> generation.generate("stage10d-embedding-001", request(embedding)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> generation.generate("stage10d-student-404", request(modelId).studentId("999999999")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(count("ai_analysis")).isZero();
        assertThat(PROVIDER_CALLS.get()).isZero();
    }

    @Test
    void scenarios17_23_casClaimDatabaseInputAndVersionFailuresAreDeterministic() throws Exception {
        AiAnalysisEntity pending = pending(request(modelId), "stage10d-worker-input-001");
        delayProvider();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> worker.execute(pending.getId()));
        pool.submit(() -> worker.execute(pending.getId()));
        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(analysisStatus(pending.getId())).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("SELECT started_time IS NOT NULL FROM ai_analysis WHERE id=?",
                Boolean.class, pending.getId())).isTrue();
        assertThat(PROVIDER_CALLS.get()).isEqualTo(1);
        release();
        awaitStatus(pending.getId(), "SUCCESS");
        pool.shutdownNow();

        NormalizedStudyPlanGenerationRequest normalized = inputCodec.normalize(request(modelId));
        String valid = inputCodec.encode(normalized);
        assertThatThrownBy(() -> inputCodec.decode(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .isInstanceOfSatisfying(GenerationFailure.class,
                        ex -> assertThat(ex.code()).isEqualTo("UNSUPPORTED_INPUT_SCHEMA"));
        assertThatThrownBy(() -> inputCodec.decode(valid.replace(
                "study-plan-generation-v1", "study-plan-generation-v2")))
                .isInstanceOfSatisfying(GenerationFailure.class,
                        ex -> assertThat(ex.code()).isEqualTo("UNSUPPORTED_PROMPT_VERSION"));
    }

    @Test
    void scenarios24_33_openAiCompatibleUsesBearerModelOptionsAndRealUsage() {
        String secretRef = secrets.create("stage10d-top-secret");
        long bearer = model("OPENAI_COMPATIBLE", AiModelType.MULTIMODAL, true,
                "BEARER_API_KEY", secretRef, 3);
        AiAnalysisDto accepted = generation.generate("stage10d-openai-success-001", request(bearer));
        awaitStatus(Long.parseLong(accepted.getId()), "SUCCESS");
        assertThat(LAST_PATH.get()).endsWith("/chat/completions");
        assertThat(LAST_AUTH.get()).isEqualTo("Bearer stage10d-top-secret");
        assertThat(LAST_BODY.get()).contains("\"temperature\":0.250")
                .contains("\"max_tokens\":512").contains("\"response_format\"")
                .doesNotContain("stage10d-top-secret");
        AiAnalysisDto result = analysisQueries.get(accepted.getId());
        assertThat(result.getPromptTokens()).isEqualTo(11);
        assertThat(result.getCompletionTokens()).isEqualTo(22);
        assertThat(result.getEstimatedCost()).isNull();
        assertThat(result.getCurrencyCode()).isNull();

        MODE.set(Mode.NO_USAGE);
        AiAnalysisDto noUsage = generation.generate("stage10d-openai-no-usage-001", request(bearer));
        awaitStatus(Long.parseLong(noUsage.getId()), "SUCCESS");
        AiAnalysisDto noUsageResult = analysisQueries.get(noUsage.getId());
        assertThat(noUsageResult.getPromptTokens()).isNull();
        assertThat(noUsageResult.getCompletionTokens()).isNull();
    }

    @Test
    void scenarios25_33_ollamaUsesTextJsonProtocolWithoutAuthorization() {
        long ollama = model("OLLAMA", AiModelType.CHAT, true, "NONE", null, 3);
        AiAnalysisDto accepted = generation.generate("stage10d-ollama-success-001", request(ollama));
        awaitStatus(Long.parseLong(accepted.getId()), "SUCCESS");
        assertThat(LAST_PATH.get()).endsWith("/api/chat");
        assertThat(LAST_AUTH.get()).isNull();
        assertThat(LAST_BODY.get()).contains("\"format\":\"json\"")
                .contains("\"num_predict\":512").doesNotContain("images");
        AiAnalysisDto result = analysisQueries.get(accepted.getId());
        assertThat(result.getPromptTokens()).isEqualTo(11);
        assertThat(result.getCompletionTokens()).isEqualTo(22);
    }

    @Test
    void scenarios34_47_proposalIsStrictRequestDatesWinAndAllTaskTypesUseDomainRules() {
        long resource = resource(studentId);
        long wrong = wrongQuestion(studentId);
        long knowledge = knowledge();
        long exam = exam(studentId);
        PROPOSAL.set("""
                {"title":"All Types","planType":"AI","startDate":"2035-01-01","endDate":"2035-01-02",
                 "dailyAvailableMinutes":999,"description":"strict proposal","tasks":[
                  {"taskDate":"2026-09-01","taskType":"WRONG_QUESTION_REVIEW","title":"WQ","wrongQuestionId":"%d"},
                  {"taskDate":"2026-09-01","taskType":"RESOURCE_LEARNING","title":"Resource","resourceId":"%d"},
                  {"taskDate":"2026-09-02","taskType":"KNOWLEDGE_PRACTICE","title":"Knowledge","knowledgeId":"%d"},
                  {"taskDate":"2026-09-02","taskType":"EXAM_REVIEW","title":"Exam","examId":"%d"},
                  {"taskDate":"2026-09-03","taskType":"READING","title":"Read"},
                  {"taskDate":"2026-09-03","taskType":"OTHER","title":"Other"}]}
                """.formatted(wrong, resource, knowledge, exam));
        StudyPlanGenerateRequest request = request(modelId)
                .targetKnowledgeIds(List.of(Long.toString(knowledge)))
                .subjectIds(List.of(Long.toString(subjectId)));
        AiAnalysisDto accepted = generation.generate("stage10d-all-types-001", request);
        awaitStatus(Long.parseLong(accepted.getId()), "SUCCESS");
        StudyPlanDto plan = analysisQueries.get(accepted.getId()).getResult();
        assertThat(plan.getStudentId()).isEqualTo(Long.toString(studentId));
        assertThat(plan.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(plan.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(plan.getDailyAvailableMinutes()).isEqualTo(60);
        assertThat(plan.getStatus().getValue()).isEqualTo("DRAFT");
        assertThat(plan.getTasks()).hasSize(6).allSatisfy(task -> {
            assertThat(task.getStatus().getValue()).isEqualTo("TODO");
            assertThat(task.getVersion()).isEqualTo(1);
        });
        assertThatThrownBy(() -> proposalParser.parse(
                "{\"title\":\"x\",\"planType\":\"AI\",\"status\":\"ACTIVE\",\"tasks\":[]}"))
                .isInstanceOf(GenerationFailure.class);
    }

    @Test
    void scenarios42_47_invalidLinksOwnershipReferencesAndDatesFailWithoutPlan() {
        long other = student("Other Student");
        long otherWrong = wrongQuestion(other);
        long otherExam = exam(other);
        List<String> invalid = List.of(
                proposalTask("RESOURCE_LEARNING", "\"resourceId\":\"999999999\"", "2026-09-01"),
                proposalTask("WRONG_QUESTION_REVIEW", "\"wrongQuestionId\":\"" + otherWrong + "\"", "2026-09-01"),
                proposalTask("EXAM_REVIEW", "\"examId\":\"" + otherExam + "\"", "2026-09-01"),
                proposalTask("KNOWLEDGE_PRACTICE", "\"knowledgeId\":\"999999999\"", "2026-09-01"),
                proposalTask("READING", "\"resourceId\":\"1\"", "2026-09-01"),
                proposalTask("READING", "", "2026-10-01"));
        int index = 0;
        for (String proposal : invalid) {
            PROPOSAL.set(proposal);
            AiAnalysisDto accepted = generation.generate("stage10d-invalid-" + String.format("%03d", index++),
                    request(modelId));
            awaitStatus(Long.parseLong(accepted.getId()), "FAILED");
        }
        assertThat(count("study_plan")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis WHERE result_json IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    void scenarios48_57_successCreatesAtomicDraftSnapshotWithStringIds() {
        AiAnalysisDto accepted = generation.generate("stage10d-success-atomic-001", request(modelId));
        awaitStatus(Long.parseLong(accepted.getId()), "SUCCESS");
        AiAnalysisDto analysis = analysisQueries.get(accepted.getId());
        StudyPlanDto plan = analysis.getResult();
        assertThat(plan.getSourceAnalysisId()).isEqualTo(accepted.getId());
        assertThat(plan.getId()).matches("[0-9]+");
        assertThat(plan.getTasks().getFirst().getId()).matches("[0-9]+");
        assertThat(analysis.getFinishedAt()).isNotNull();
        assertThat(analysis.getDurationMs()).isNotNegative();
        assertThat(analysis.getErrorCode()).isNull();
        assertThat(analysis.getErrorMessage()).isNull();
        assertThat(count("study_plan")).isEqualTo(1);
        assertThat(count("study_plan_task")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT CAST(source_analysis_id AS CHAR) FROM study_plan",
                String.class)).isEqualTo(accepted.getId());
    }

    @Test
    void scenarios35_36_and58_65_providerAndDomainFailuresAreSanitizedAndAtomic() {
        for (Mode mode : List.of(Mode.MALFORMED, Mode.MARKDOWN, Mode.HTTP_500, Mode.AUTH_FAILURE)) {
            MODE.set(mode);
            AiAnalysisDto accepted = generation.generate("stage10d-provider-fail-" + mode.name(), request(modelId));
            awaitStatus(Long.parseLong(accepted.getId()), "FAILED");
        }
        PROPOSAL.set("""
                {"title":"Rollback","planType":"AI","tasks":[
                 {"taskDate":"2026-09-01","taskType":"READING","title":"first"},
                 {"taskDate":"2026-09-01","taskType":"RESOURCE_LEARNING","title":"bad","resourceId":"999999999"}]}
                """);
        MODE.set(Mode.SUCCESS);
        AiAnalysisDto domain = generation.generate("stage10d-domain-rollback-001", request(modelId));
        awaitStatus(Long.parseLong(domain.getId()), "FAILED");
        assertThat(count("study_plan")).isZero();
        assertThat(count("study_plan_task")).isZero();
        List<Map<String, Object>> failed = jdbc.queryForList("""
                SELECT error_code,error_message,result_json,finished_time,duration_ms FROM ai_analysis
                WHERE status='FAILED'
                """);
        assertThat(failed).allSatisfy(row -> {
            assertThat(row.get("error_code")).isNotNull();
            assertThat(row.get("error_message").toString()).doesNotContain("raw", "Authorization", "secret");
            assertThat(row.get("result_json")).isNull();
            assertThat(row.get("finished_time")).isNotNull();
            assertThat(((Number) row.get("duration_ms")).longValue()).isNotNegative();
        });
    }

    @Test
    void scenarioProviderTimeoutFailsWithoutFixedSleepOrPlan() {
        long timeoutModel = model("OPENAI_COMPATIBLE", AiModelType.CHAT, true, "NONE", null, 1);
        delayProvider();
        AiAnalysisDto accepted = generation.generate("stage10d-timeout-001", request(timeoutModel));
        assertThat(providerEnteredAwait()).isTrue();
        awaitStatus(Long.parseLong(accepted.getId()), "FAILED");
        release();
        assertThat(analysisQueries.get(accepted.getId()).getErrorCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(count("study_plan")).isZero();
    }

    @Test
    void stage11cOversizedProviderResponseFailsWithStableSanitizedCode() {
        MODE.set(Mode.OVERSIZED);
        AiAnalysisDto accepted = generation.generate("stage11c-oversized-study-plan", request(modelId));

        awaitStatus(Long.parseLong(accepted.getId()), "FAILED");

        AiAnalysisDto failed = analysisQueries.get(accepted.getId());
        assertThat(failed.getErrorCode()).isEqualTo("PROVIDER_RESPONSE_TOO_LARGE");
        assertThat(failed.getErrorMessage()).doesNotContain("raw-provider-secret");
        assertThat(count("study_plan")).isZero();
    }

    @Test
    void scenarios66_72_recoveryRedispatchesResetsRunningAndBlocksExistingPlan() {
        AiAnalysisEntity reset = pending(request(modelId), "stage10d-recovery-reset");
        assertThat(analysisMapper.claim(reset.getId())).isEqualTo(1);
        assertThat(analysisMapper.resetRunning(reset.getId())).isEqualTo(1);
        Map<String, Object> resetState = jdbc.queryForMap(
                "SELECT status,started_time,finished_time,duration_ms FROM ai_analysis WHERE id=?", reset.getId());
        assertThat(resetState.get("status")).isEqualTo("PENDING");
        assertThat(resetState.get("started_time")).isNull();
        assertThat(resetState.get("finished_time")).isNull();
        assertThat(resetState.get("duration_ms")).isNull();
        jdbc.update("DELETE FROM ai_analysis WHERE id=?", reset.getId());

        AiAnalysisEntity running = pending(request(modelId), "stage10d-recovery-running");
        assertThat(analysisMapper.claim(running.getId())).isEqualTo(1);
        recovery.recover();
        awaitStatus(running.getId(), "SUCCESS");
        assertThat(count("study_plan")).isEqualTo(1);

        AiAnalysisEntity conflict = pending(request(modelId), "stage10d-recovery-conflict");
        assertThat(analysisMapper.claim(conflict.getId())).isEqualTo(1);
        jdbc.update("""
                INSERT INTO study_plan(student_id,title,plan_type,start_date,end_date,status,source_analysis_id,
                    deleted,version) VALUES (?,'Existing','AI','2026-09-01','2026-09-03','DRAFT',?,0,1)
                """, studentId, conflict.getId());
        int calls = PROVIDER_CALLS.get();
        recovery.recover();
        assertThat(analysisStatus(conflict.getId())).isEqualTo("FAILED");
        assertThat(PROVIDER_CALLS.get()).isEqualTo(calls);
        recovery.recover();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_plan WHERE source_analysis_id=?",
                Integer.class, conflict.getId())).isEqualTo(1);

        AiAnalysisEntity rejected = pending(request(modelId), "stage10d-rejected-pending");
        AsyncTaskExecutor rejecting = task -> { throw new TaskRejectedException("full"); };
        AiStudyPlanGenerationDispatcher rejectedDispatcher = new AiStudyPlanGenerationDispatcher(rejecting, worker);
        assertThat(rejectedDispatcher.dispatch(rejected.getId())).isFalse();
        assertThat(analysisStatus(rejected.getId())).isEqualTo("PENDING");
        recovery.rescanPending();
        awaitStatus(rejected.getId(), "SUCCESS");
    }

    @Test
    void scenario39_modelDisabledAfterAcceptanceFailsBeforeProviderCall() {
        AiAnalysisEntity pending = pending(request(modelId), "stage10d-disable-race");
        jdbc.update("UPDATE ai_model SET enabled=0 WHERE id=?", modelId);
        worker.execute(pending.getId());
        assertThat(analysisStatus(pending.getId())).isEqualTo("FAILED");
        assertThat(PROVIDER_CALLS.get()).isZero();
        assertThat(count("study_plan")).isZero();
    }

    @Test
    void scenarios73_84_generationDoesNotTouchOtherDomainsOrLeakSensitiveData() {
        Map<String, String> before = state(List.of("student_mastery", "mastery_history", "wrong_review",
                "ai_extraction_task", "resource_history", "student_resource_assignment"));
        AiAnalysisDto accepted = generation.generate("stage10d-isolation-001", request(modelId));
        awaitStatus(Long.parseLong(accepted.getId()), "SUCCESS");
        assertThat(state(before.keySet().stream().toList())).isEqualTo(before);
        String input = jdbc.queryForObject("SELECT CAST(input_json AS CHAR) FROM ai_analysis WHERE id=?",
                String.class, Long.parseLong(accepted.getId()));
        String result = jdbc.queryForObject("SELECT CAST(result_json AS CHAR) FROM ai_analysis WHERE id=?",
                String.class, Long.parseLong(accepted.getId()));
        assertThat(input + result).doesNotContain("Authorization", "apiKey", "secret", "storage_path");
        assertThat(LAST_BODY.get()).doesNotContain("Stage10D Student", "Authorization", "secret");
    }

    @Test
    void scenarios85_103_frozenBaselineGeneratorAndDatabaseRemainIntact() {
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("21");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'",
                Integer.class)).isEqualTo(47);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class)).isEqualTo(31);
        assertThat(AiTaskStatus.values()).contains(AiTaskStatus.PENDING, AiTaskStatus.RUNNING,
                AiTaskStatus.SUCCESS, AiTaskStatus.FAILED);
    }

    private StudyPlanGenerateRequest request(long selectedModel) {
        return new StudyPlanGenerateRequest(Long.toString(studentId), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3), 60, Long.toString(selectedModel));
    }

    private AiAnalysisEntity pending(StudyPlanGenerateRequest request, String key) {
        NormalizedStudyPlanGenerationRequest normalized = inputCodec.normalize(request);
        AiAnalysisEntity pending = new AiAnalysisEntity();
        pending.setStudentId(Long.parseLong(normalized.studentId()));
        pending.setBusinessType(com.stdntedu.generated.model.AiAnalysisBusinessType.STUDY_PLAN_GENERATION);
        pending.setAiModelId(Long.parseLong(normalized.modelId()));
        pending.setStatus(AiTaskStatus.PENDING);
        pending.setInputSummary("test input");
        pending.setInputJson(inputCodec.encode(normalized));
        pending.setIdempotencyKey(key);
        pending.setRequestHash(inputCodec.requestHash(normalized));
        analysisMapper.insertPending(pending);
        return analysisMapper.selectByIdempotency(pending.getStudentId(), key);
    }

    private long student(String name) {
        String code = "S10D-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO student(student_code,name,deleted,version) VALUES (?,?,0,0)", code, name);
        return jdbc.queryForObject("SELECT id FROM student WHERE student_code=?", Long.class, code);
    }

    private long model(String protocol, AiModelType type, boolean enabled, String authType,
            String secretRef, int timeout) {
        String modelName = "s10d-" + UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("""
                INSERT INTO ai_model(name,provider,model_type,model_name,protocol,auth_type,api_base_url,api_key_ref,
                    supports_vision,supports_json,local_flag,enabled,priority_no,timeout_seconds,temperature,max_tokens,version)
                VALUES ('Stage10D','CUSTOM',?,?,?,? ,?,?,1,1,1,?,100,?,0.250,512,0)
                """, type.name(), modelName, protocol, authType,
                "http://127.0.0.1:" + port, secretRef, enabled, timeout);
        return jdbc.queryForObject("SELECT id FROM ai_model WHERE model_name=?", Long.class, modelName);
    }

    private long knowledge() {
        String code = "S10D-K-" + UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("""
                INSERT INTO knowledge_node(node_code,name,node_type,grade_id,subject_id,level_no,enabled,deleted,version)
                VALUES (?,'Stage10D Knowledge','POINT',?,?,1,1,0,1)
                """, code, gradeId, subjectId);
        return jdbc.queryForObject("SELECT id FROM knowledge_node WHERE node_code=?", Long.class, code);
    }

    private long resource(long student) {
        String code = "S10D-R-" + UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("""
                INSERT INTO learning_resource(resource_code,title,resource_type,source_type,subject_id,status,deleted,version)
                VALUES (?,'Stage10D Resource','VIDEO','MANUAL',?,'WAITING',0,1)
                """, code, subjectId);
        long id = jdbc.queryForObject("SELECT id FROM learning_resource WHERE resource_code=?", Long.class, code);
        jdbc.update("INSERT INTO student_resource_assignment(student_id,resource_id,status,version) VALUES (?,?,'WAITING',0)",
                student, id);
        return id;
    }

    private long wrongQuestion(long student) {
        jdbc.update("""
                INSERT INTO wrong_question(student_id,subject_id,source_type,question_text,status,review_stage,
                    review_count,occurred_date,deleted,version)
                VALUES (?,?,'PRACTICE','Stage10D question','NEW',0,0,'2026-08-01',0,1)
                """, student, subjectId);
        return jdbc.queryForObject("SELECT MAX(id) FROM wrong_question", Long.class);
    }

    private long exam(long student) {
        jdbc.update("""
                INSERT INTO exam(student_id,exam_name,exam_type,exam_date,total_score,total_full_score,deleted,version)
                VALUES (?,'Stage10D Exam','QUIZ','2026-08-01',80,100,0,1)
                """, student);
        return jdbc.queryForObject("SELECT MAX(id) FROM exam", Long.class);
    }

    private String readingProposal(LocalDate taskDate) {
        return """
                {"title":"AI Plan","planType":"AI","startDate":"2030-01-01","endDate":"2030-01-02",
                 "dailyAvailableMinutes":999,"description":"generated safely","tasks":[
                  {"taskDate":"%s","taskType":"READING","title":"Read","expectedDurationSeconds":600,"sortOrder":1}]}
                """.formatted(taskDate);
    }

    private String proposalTask(String type, String link, String date) {
        String comma = link.isBlank() ? "" : "," + link;
        return "{\"title\":\"Invalid\",\"planType\":\"AI\",\"tasks\":[{\"taskDate\":\""
                + date + "\",\"taskType\":\"" + type + "\",\"title\":\"bad\"" + comma + "}]}";
    }

    private void delayProvider() {
        MODE.set(Mode.DELAYED);
        providerEntered = new CountDownLatch(1);
        releaseProvider = new CountDownLatch(1);
    }

    private boolean providerEnteredAwait() {
        try { return providerEntered.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }

    private void release() {
        CountDownLatch latch = releaseProvider;
        if (latch != null) latch.countDown();
    }

    private void resetProvider() {
        MODE.set(Mode.SUCCESS);
        LAST_PATH.set(null);
        LAST_BODY.set(null);
        LAST_AUTH.set(null);
        PROVIDER_CALLS.set(0);
        providerEntered = new CountDownLatch(0);
        releaseProvider = new CountDownLatch(0);
    }

    private void awaitStatus(long analysisId, String expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (expected.equals(analysisStatus(analysisId))) return;
            try { TimeUnit.MILLISECONDS.sleep(25); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
        }
        throw new AssertionError("analysis did not reach " + expected + "; actual=" + analysisStatus(analysisId));
    }

    private String analysisStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM ai_analysis WHERE id=?", String.class, id);
    }

    private void awaitExecutorIdle() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (taskExecutor.getActiveCount() == 0 && taskExecutor.getThreadPoolExecutor().getQueue().isEmpty()) return;
            try { TimeUnit.MILLISECONDS.sleep(25); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
        }
        throw new AssertionError("AI study-plan executor did not become idle");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Map<String, String> state(List<String> tables) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : tables) {
            result.put(table, jdbc.queryForObject(
                    "SELECT CONCAT(COUNT(*),':',COALESCE(SUM(id),0)) FROM " + table, String.class));
        }
        return result;
    }

    private void deleteInOrder() {
        for (String table : List.of("study_plan_action_history", "study_plan_task", "study_plan", "ai_analysis",
                "ai_extraction_confirmation_item", "ai_extraction_confirmation", "ai_extraction_correction",
                "ai_extraction_question_knowledge", "ai_extraction_question", "ai_extraction_file", "attachment",
                "ai_extraction_task", "student_resource_assignment", "resource_history",
                "learning_resource_knowledge", "learning_resource", "wrong_question_knowledge", "wrong_review",
                "wrong_question", "score_knowledge", "score_record", "exam", "mastery_history", "student_mastery",
                "knowledge_relation", "knowledge_node", "ai_model", "ai_secret", "academic_term", "student")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        PROVIDER_CALLS.incrementAndGet();
        LAST_PATH.set(exchange.getRequestURI().getPath());
        LAST_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
        LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Mode mode = MODE.get();
        if (mode == Mode.DELAYED) {
            providerEntered.countDown();
            try { releaseProvider.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        if (mode == Mode.OVERSIZED) {
            byte[] bytes = ("raw-provider-secret" + "x".repeat(4096)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } catch (IOException ignored) { }
            exchange.close();
            return;
        }
        int status = mode == Mode.HTTP_500 ? 500 : mode == Mode.AUTH_FAILURE ? 401 : 200;
        String content = switch (mode) {
            case MALFORMED -> "{not-json";
            case MARKDOWN -> "```json\n" + PROPOSAL.get() + "\n```";
            default -> PROPOSAL.get();
        };
        Map<String, Object> body;
        if (exchange.getRequestURI().getPath().endsWith("/api/chat")) {
            body = mode == Mode.NO_USAGE
                    ? Map.of("message", Map.of("content", content))
                    : Map.of("message", Map.of("content", content), "prompt_eval_count", 11, "eval_count", 22);
        } else {
            body = mode == Mode.NO_USAGE
                    ? Map.of("choices", List.of(Map.of("message", Map.of("content", content))))
                    : Map.of("choices", List.of(Map.of("message", Map.of("content", content))),
                            "usage", Map.of("prompt_tokens", 11, "completion_tokens", 22));
        }
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private enum Mode { SUCCESS, DELAYED, MALFORMED, MARKDOWN, HTTP_500, AUTH_FAILURE, NO_USAGE, OVERSIZED }
}

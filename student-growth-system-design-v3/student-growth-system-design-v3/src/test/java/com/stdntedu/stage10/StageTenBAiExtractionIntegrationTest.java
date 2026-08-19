package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.service.AiExtractionConfirmationService;
import com.stdntedu.ai.extraction.service.AiExtractionQuestionService;
import com.stdntedu.ai.extraction.service.AiExtractionService;
import com.stdntedu.ai.extraction.service.CreateExtractionCommand;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.AiConfirm;
import com.stdntedu.generated.model.AiConfirmItem;
import com.stdntedu.generated.model.AiExtractionQuestionStatus;
import com.stdntedu.generated.model.AiExtractionQuestionUpdateRequest;
import com.stdntedu.generated.model.AiInputType;
import com.stdntedu.generated.model.AiTask;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.CancelAiExtractionRequest;
import com.stdntedu.generated.model.KnowledgeLink;
import com.stdntedu.generated.model.RetryAiExtractionRequest;
import com.stdntedu.generated.model.WrongSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
class StageTenBAiExtractionIntegrationTest {
    private static final String MASTER_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final Path STORAGE_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "stdntedu-stage10b-" + UUID.randomUUID()).toAbsolutePath();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.SUCCESS);
    private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
    private static volatile CountDownLatch providerEntered;
    private static volatile CountDownLatch releaseProvider;
    private static volatile long knowledgeId;
    private static HttpServer server;
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
        registry.add("app.ai.extraction.storage-root", STORAGE_ROOT::toString);
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", StageTenBAiExtractionIntegrationTest::respond);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (server != null) server.stop(0);
        deleteTree(STORAGE_ROOT);
    }

    @Autowired AiExtractionService extractions;
    @Autowired AiExtractionQuestionService questions;
    @Autowired AiExtractionConfirmationService confirmations;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private long studentId;
    private long subjectId;
    private long gradeId;

    @BeforeEach
    void cleanAndSeed() throws IOException {
        deleteInOrder();
        deleteTree(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        MODE.set(Mode.SUCCESS);
        LAST_PATH.set(null);
        LAST_BODY.set(null);
        providerEntered = new CountDownLatch(0);
        releaseProvider = new CountDownLatch(0);

        subjectId = jdbc.queryForObject("SELECT id FROM subject WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
        gradeId = jdbc.queryForObject("SELECT id FROM grade WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
        String studentCode = "S10B-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO student(student_code,name,deleted,version) VALUES (?, 'Stage10B Student',0,0)",
                studentCode);
        studentId = jdbc.queryForObject("SELECT id FROM student WHERE student_code=?", Long.class, studentCode);
        String nodeCode = "S10B-K-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("""
                INSERT INTO knowledge_node(node_code,name,node_type,grade_id,subject_id,level_no,enabled,deleted,version)
                VALUES (?, 'Stage10B Knowledge', 'POINT', ?, ?, 1, 1, 0, 1)
                """, nodeCode, gradeId, subjectId);
        knowledgeId = jdbc.queryForObject("SELECT id FROM knowledge_node WHERE node_code=?", Long.class, nodeCode);
    }

    @Test
    void scenariosUploadTaskAttachmentQuestionAndStringIds() throws Exception {
        long modelId = model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "openai");
        AiTask task = create(modelId);

        assertThat(task.getStatus()).isEqualTo(AiTaskStatus.REVIEW_REQUIRED);
        assertThat(task.getInputType()).isEqualTo(AiInputType.IMAGE);
        assertThat(task.getTaskId()).matches("[0-9]+");
        assertThat(task.getStudentId()).isEqualTo(Long.toString(studentId));
        assertThat(task.getSubjectId()).isEqualTo(Long.toString(subjectId));
        assertThat(task.getFileCount()).isEqualTo(1);
        assertThat(task.getQuestionCount()).isEqualTo(1);
        assertThat(count("attachment")).isEqualTo(1);
        assertThat(count("ai_extraction_file")).isEqualTo(1);
        assertThat(count("ai_extraction_question")).isEqualTo(1);
        assertThat(count("ai_extraction_question_knowledge")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT mime_type FROM attachment", String.class)).isEqualTo("image/png");
        assertThat(Files.isRegularFile(Path.of(jdbc.queryForObject(
                "SELECT storage_path FROM attachment", String.class)))).isTrue();

        mvc.perform(get("/api/v1/ai/wrong-question-extractions/{taskId}", task.getTaskId())
                        .header("X-Request-ID", "stage10b-task"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "stage10b-task"))
                .andExpect(jsonPath("$.data.taskId").isString())
                .andExpect(jsonPath("$.data.studentId").isString())
                .andExpect(jsonPath("$.data.modelId").isString())
                .andExpect(jsonPath("$.requestId").value("stage10b-task"));
    }

    @Test
    void scenarioMultipartOperationAcceptsFrozenFieldsAndReturns202() throws Exception {
        long modelId = model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "multipart");
        MockMultipartFile image = upload();
        mvc.perform(multipart("/api/v1/ai/wrong-question-extractions")
                        .file(image)
                        .param("studentId", Long.toString(studentId))
                        .param("sourceType", "PRACTICE")
                        .param("modelId", Long.toString(modelId))
                        .param("subjectId", Long.toString(subjectId))
                        .param("gradeId", Long.toString(gradeId))
                        .param("sourceName", "worksheet"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").isString())
                .andExpect(jsonPath("$.data.status").value("REVIEW_REQUIRED"));
    }

    @Test
    void scenariosProviderSelectionUsesProtocolAndOllamaReceivesPlainBase64() throws Exception {
        long modelId = model("OLLAMA", "MULTIMODAL", true, "ollama");
        AiTask task = create(modelId);

        assertThat(task.getStatus()).isEqualTo(AiTaskStatus.REVIEW_REQUIRED);
        assertThat(LAST_PATH.get()).endsWith("/ollama/api/chat");
        assertThat(LAST_BODY.get()).contains("\"images\":[\"").doesNotContain("data:image/");
    }

    @Test
    void scenariosDisabledChatAndEmbeddingModelsAreRejectedBeforeProviderCall() {
        long disabled = model("OPENAI_COMPATIBLE", "MULTIMODAL", false, "disabled");
        long chat = model("OPENAI_COMPATIBLE", "CHAT", true, "chat");
        long embedding = model("OPENAI_COMPATIBLE", "EMBEDDING", true, "embedding");

        assertRule(() -> create(disabled));
        assertRule(() -> create(chat));
        assertRule(() -> create(embedding));
        assertThat(LAST_PATH.get()).isNull();
        assertThat(count("ai_extraction_task")).isZero();
    }

    @Test
    void scenariosMalformedProviderResultFailsWholeTaskWithoutPartialQuestions() throws Exception {
        MODE.set(Mode.MALFORMED);
        AiTask task = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "malformed"));

        assertThat(task.getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(task.getErrorCode()).isEqualTo("PROVIDER_RESPONSE_INVALID");
        assertThat(task.getErrorMessage()).doesNotContain("raw-provider-secret");
        assertThat(count("ai_extraction_question")).isZero();
    }

    @Test
    void scenariosFailedTaskCanBeExplicitlyRetriedWithCasAndOriginalAttachment() throws Exception {
        MODE.set(Mode.HTTP_500);
        AiTask failed = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "retry"));
        assertThat(failed.getStatus()).isEqualTo(AiTaskStatus.FAILED);
        MODE.set(Mode.SUCCESS);

        AiTask retried = extractions.retry(failed.getTaskId(),
                new RetryAiExtractionRequest().resetTemporaryQuestions(true));
        assertThat(retried.getStatus()).isEqualTo(AiTaskStatus.REVIEW_REQUIRED);
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getFileCount()).isEqualTo(1);
        assertThat(retried.getQuestionCount()).isEqualTo(1);
        assertThat(count("attachment")).isEqualTo(1);
    }

    @Test
    void scenarioDelayedProviderResultCannotOverwriteCancellationOrCreateQuestions() throws Exception {
        MODE.set(Mode.DELAYED);
        providerEntered = new CountDownLatch(1);
        releaseProvider = new CountDownLatch(1);
        long modelId = model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "delayed");

        CompletableFuture<AiTask> creation = CompletableFuture.supplyAsync(() -> createUnchecked(modelId));
        assertThat(providerEntered.await(10, TimeUnit.SECONDS)).isTrue();
        Long taskId = awaitRunningTask();
        AiTask cancelled = extractions.cancel(taskId.toString(), new CancelAiExtractionRequest().reason("user stop"));
        releaseProvider.countDown();
        AiTask returned = creation.get(10, TimeUnit.SECONDS);

        assertThat(cancelled.getStatus()).isEqualTo(AiTaskStatus.CANCELLED);
        assertThat(returned.getStatus()).isEqualTo(AiTaskStatus.CANCELLED);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_extraction_task WHERE id=?", String.class, taskId))
                .isEqualTo("CANCELLED");
        assertThat(count("ai_extraction_question")).isZero();
    }

    @Test
    void scenariosQuestionUpdateUsesOptimisticLockAndPersistsCorrectionsAndFinalKnowledge() throws Exception {
        AiTask task = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "question-update"));
        String questionId = questionId(task);
        AiExtractionQuestionUpdateRequest update = new AiExtractionQuestionUpdateRequest(0)
                .questionText("manually corrected question")
                .correctAnswer("42")
                .status(AiExtractionQuestionStatus.CONFIRMED)
                .knowledgePoints(List.of(new KnowledgeLink(Long.toString(knowledgeId), true)));

        var changed = questions.update(task.getTaskId(), questionId, update);
        assertThat(changed.getVersion()).isEqualTo(1);
        assertThat(changed.getUserModified()).isTrue();
        assertThat(changed.getKnowledgeCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getKnowledgeId()).isEqualTo(Long.toString(knowledgeId));
            assertThat(candidate.getSource().getValue()).isEqualTo("USER");
            assertThat(candidate.getConfirmed()).isTrue();
        });
        assertThat(count("ai_extraction_correction")).isGreaterThanOrEqualTo(3);
        assertThatThrownBy(() -> questions.update(task.getTaskId(), questionId, update))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("DATA_VERSION_CONFLICT"));
    }

    @Test
    void scenariosQuestionListBatchesCandidatesAndRejectsCrossTaskQuestion() throws Exception {
        AiTask first = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "batch-first"));
        AiTask second = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "batch-second"));
        String foreignQuestion = questionId(second);

        assertThat(questions.list(first.getTaskId()).getItems()).singleElement()
                .satisfies(item -> assertThat(item.getKnowledgeCandidates()).hasSize(1));
        assertThatThrownBy(() -> questions.update(first.getTaskId(), foreignQuestion,
                new AiExtractionQuestionUpdateRequest(0).questionText("wrong scope")))
                .isInstanceOf(com.stdntedu.common.exception.ResourceNotFoundException.class);
    }

    @Test
    void scenariosConfirmCreatesWrongQuestionAndMappingWithoutMasterySideEffects() throws Exception {
        AiTask task = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "confirm"));
        String questionId = questionId(task);
        AiConfirm request = confirmation(questionId, Long.toString(knowledgeId), "confirmed question");

        var result = confirmations.confirm(task.getTaskId(), "confirm-key-0001", request);
        assertThat(result.getTaskStatus()).isEqualTo(AiTaskStatus.SUCCESS);
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getWrongQuestionIds()).singleElement()
                .satisfies(id -> assertThat(id).matches("[0-9]+"));
        assertThat(count("ai_extraction_confirmation")).isEqualTo(1);
        assertThat(count("ai_extraction_confirmation_item")).isEqualTo(1);
        assertThat(count("wrong_question")).isEqualTo(1);
        assertThat(count("wrong_question_knowledge")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source_type FROM wrong_question", String.class)).isEqualTo("PRACTICE");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_extraction_question", String.class)).isEqualTo("SAVED");
        assertThat(count("wrong_review")).isZero();
        assertThat(count("student_mastery")).isZero();
        assertThat(count("mastery_history")).isZero();
    }

    @Test
    void scenariosConfirmationSameHashReplaysAndDifferentHashConflicts() throws Exception {
        AiTask task = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "idempotency"));
        String questionId = questionId(task);
        AiConfirm original = confirmation(questionId, Long.toString(knowledgeId), "same payload");
        var first = confirmations.confirm(task.getTaskId(), "idem-key-0000001", original);
        var replay = confirmations.confirm(task.getTaskId(), "idem-key-0000001", original);

        assertThat(replay).isEqualTo(first);
        assertThat(count("wrong_question")).isEqualTo(1);
        AiConfirm changed = confirmation(questionId, Long.toString(knowledgeId), "different payload");
        assertThatThrownBy(() -> confirmations.confirm(task.getTaskId(), "idem-key-0000001", changed))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(count("wrong_question")).isEqualTo(1);
    }

    @Test
    void scenarioConfirmIsAllOrNothingWhenLaterQuestionFailsDomainValidation() throws Exception {
        MODE.set(Mode.TWO_QUESTIONS);
        AiTask task = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "rollback"));
        List<String> ids = questions.list(task.getTaskId()).getItems().stream().map(item -> item.getId()).toList();
        AiConfirmItem valid = confirmItem(ids.get(0), Long.toString(knowledgeId), "valid question");
        AiConfirmItem invalid = confirmItem(ids.get(1), "999999999999", "invalid question");

        assertThatThrownBy(() -> confirmations.confirm(task.getTaskId(), "rollback-key-001",
                new AiConfirm(true, List.of(valid, invalid))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(count("ai_extraction_confirmation")).isZero();
        assertThat(count("ai_extraction_confirmation_item")).isZero();
        assertThat(count("wrong_question")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_extraction_question WHERE status='SAVED'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_extraction_task WHERE id=?", String.class,
                Long.valueOf(task.getTaskId()))).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void scenarioSavedQuestionIsImmutableAfterConfirmation() throws Exception {
        AiTask task = create(model("OPENAI_COMPATIBLE", "MULTIMODAL", true, "saved"));
        String questionId = questionId(task);
        confirmations.confirm(task.getTaskId(), "saved-key-000001",
                confirmation(questionId, Long.toString(knowledgeId), "saved question"));

        assertThatThrownBy(() -> questions.update(task.getTaskId(), questionId,
                new AiExtractionQuestionUpdateRequest(1).questionText("must fail")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private AiTask create(long modelId) throws IOException {
        return extractions.create(List.of(upload()), command(modelId));
    }

    private AiTask createUnchecked(long modelId) {
        try {
            return create(modelId);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private CreateExtractionCommand command(long modelId) {
        return new CreateExtractionCommand(Long.toString(studentId), Long.toString(subjectId),
                Long.toString(gradeId), WrongSource.PRACTICE, "worksheet", null, Long.toString(modelId),
                true, true, false);
    }

    private long model(String protocol, String type, boolean enabled, String suffix) {
        String modelName = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String base = "http://127.0.0.1:" + port + "/" + ("OLLAMA".equals(protocol) ? "ollama" : "openai");
        jdbc.update("""
                INSERT INTO ai_model(name,provider,model_type,model_name,protocol,auth_type,api_base_url,
                    supports_vision,supports_json,local_flag,enabled,priority_no,timeout_seconds,version)
                VALUES (?,?,?,?,?,'NONE',?,1,1,1,?,100,2,0)
                """, "Stage10B " + suffix, "CUSTOM", type, modelName, protocol, base, enabled);
        return jdbc.queryForObject("SELECT id FROM ai_model WHERE model_name=?", Long.class, modelName);
    }

    private String questionId(AiTask task) {
        return questions.list(task.getTaskId()).getItems().getFirst().getId();
    }

    private AiConfirm confirmation(String questionId, String nodeId, String text) {
        return new AiConfirm(true, List.of(confirmItem(questionId, nodeId, text)));
    }

    private AiConfirmItem confirmItem(String questionId, String nodeId, String text) {
        return new AiConfirmItem(questionId, true, text).questionType("SHORT_ANSWER")
                .studentAnswer("41").correctAnswer("42").analysisText("manual review")
                .knowledgePoints(List.of(new KnowledgeLink(nodeId, true)));
    }

    private MockMultipartFile upload() throws IOException {
        return new MockMultipartFile("files", "question.png", "image/png", png());
    }

    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x224466);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private Long awaitRunningTask() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            List<Long> ids = jdbc.query("SELECT id FROM ai_extraction_task WHERE status='RUNNING'",
                    (rs, row) -> rs.getLong(1));
            if (!ids.isEmpty()) return ids.getFirst();
            Thread.sleep(50);
        }
        throw new AssertionError("running extraction task was not observed");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void deleteInOrder() {
        for (String table : List.of("ai_extraction_confirmation_item", "ai_extraction_confirmation",
                "ai_extraction_correction", "ai_extraction_question_knowledge", "ai_extraction_question",
                "ai_extraction_file", "attachment", "ai_extraction_task", "wrong_question_knowledge",
                "wrong_review", "wrong_question", "mastery_history", "student_mastery", "knowledge_node",
                "ai_model", "ai_secret", "student")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private void assertRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        });
    }

    private static void respond(HttpExchange exchange) throws IOException {
        LAST_PATH.set(exchange.getRequestURI().getPath());
        LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Mode mode = MODE.get();
        if (mode == Mode.DELAYED) {
            providerEntered.countDown();
            try {
                if (!releaseProvider.await(10, TimeUnit.SECONDS)) throw new IOException("provider release timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            }
        }
        int statusCode = mode == Mode.HTTP_500 ? 500 : 200;
        String body = responseBody(mode, exchange.getRequestURI().getPath());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String responseBody(Mode mode, String path) throws IOException {
        if (mode == Mode.HTTP_500) return "{\"error\":\"raw-provider-secret\"}";
        String content;
        if (mode == Mode.MALFORMED) {
            content = "{malformed raw-provider-secret";
        } else {
            String second = mode == Mode.TWO_QUESTIONS ? "," + questionJson(2, "Question two") : "";
            content = "{\"questions\":[" + questionJson(1, "Question one") + second + "]}";
        }
        String encoded = JSON.writeValueAsString(content);
        return path.endsWith("/api/chat")
                ? "{\"message\":{\"content\":" + encoded + "}}"
                : "{\"choices\":[{\"message\":{\"content\":" + encoded + "}}]}";
    }

    private static String questionJson(int page, String text) {
        return "{\"pageNumber\":" + page + ",\"questionNumber\":\"" + page
                + "\",\"questionType\":\"SHORT_ANSWER\",\"questionText\":\"" + text
                + "\",\"studentAnswer\":\"41\",\"correctAnswer\":\"42\","
                + "\"answerSource\":\"AI\",\"analysisText\":\"analysis\","
                + "\"analysisSource\":\"AI\",\"errorType\":null,\"difficulty\":2,"
                + "\"confidence\":0.95,\"knowledgeCandidates\":[{\"knowledgeId\":\""
                + knowledgeId + "\",\"knowledgeCode\":\"K\",\"knowledgeName\":\"Knowledge\","
                + "\"confidence\":0.9,\"primary\":true}]}";
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }

    private enum Mode { SUCCESS, TWO_QUESTIONS, MALFORMED, HTTP_500, DELAYED }
}

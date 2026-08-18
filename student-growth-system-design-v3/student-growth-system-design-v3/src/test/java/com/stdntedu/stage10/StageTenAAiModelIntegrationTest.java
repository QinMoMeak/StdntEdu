package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.stdntedu.ai.model.service.AiModelService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.AiAuthType;
import com.stdntedu.generated.model.AiModelCreateRequest;
import com.stdntedu.generated.model.AiModelDto;
import com.stdntedu.generated.model.AiModelStatusChangeRequest;
import com.stdntedu.generated.model.AiModelType;
import com.stdntedu.generated.model.AiModelUpdateRequest;
import com.stdntedu.generated.model.AiProtocol;
import com.stdntedu.generated.model.AiProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
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
@ExtendWith(OutputCaptureExtension.class)
class StageTenAAiModelIntegrationTest {
    private static final String MASTER_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String TEST_SECRET = "integration-secret-9A7B";
    private static final String RAW_PROVIDER_SECRET = "provider-body-secret-must-not-leak";
    private static final Map<String, String> AUTHORIZATION = new ConcurrentHashMap<>();
    private static final Set<String> REQUEST_PATHS = ConcurrentHashMap.newKeySet();
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
    }

    @BeforeAll static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", StageTenAAiModelIntegrationTest::respond);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Autowired AiModelService models;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM ai_model");
        jdbc.update("DELETE FROM ai_secret");
        AUTHORIZATION.clear();
        REQUEST_PATHS.clear();
    }

    @Test void scenarios09_13_19_27_noneModelCreatesWithoutSecretAndUsesStringId() throws Exception {
        AiModelDto created = models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, endpoint("compatible", "none-model"), "none-model", null));

        assertThat(created.getVersion()).isZero();
        assertThat(created.getApiKeyConfigured()).isFalse();
        assertThat(created.getApiKeyMasked()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_secret", Integer.class)).isZero();
        assertThat(created.getId()).matches("[0-9]+");
        mvc.perform(get("/api/v1/ai/models/{id}", created.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(false))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyRef").doesNotExist());
    }

    @Test void scenarios10_21_26_bearerModelPersistsOnlyEncryptedSecretAndMaskedMetadata() throws Exception {
        AiModelDto created = models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.BEARER_API_KEY, endpoint("compatible", "secure-model"), "secure-model", TEST_SECRET));
        String secretRef = secretRef(created);
        byte[] encrypted = jdbc.queryForObject("SELECT encrypted_value FROM ai_secret WHERE secret_ref=?",
                byte[].class, secretRef);

        assertThat(encrypted).isNotEqualTo(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        assertThat(secretRef).startsWith("ais_").isNotEqualTo(TEST_SECRET);
        assertThat(created.getApiKeyConfigured()).isTrue();
        assertThat(created.getApiKeyMasked()).isEqualTo("****9A7B");
        String body = mvc.perform(get("/api/v1/ai/models"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(TEST_SECRET, secretRef, "encryptedValue", "nonce");
    }

    @Test void scenarios11_14_20_invalidCreateRulesAreRejected() {
        assertBusinessRule(() -> models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.BEARER_API_KEY, URI.create("http://localhost"), unique("required"), null)));
        assertBusinessRule(() -> models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("relative/path"), unique("relative"), null)));
        assertBusinessRule(() -> models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("http://user:password@localhost"), unique("userinfo"), null)));
    }

    @Test void scenarios14_17_numericBoundariesAreEnforced() {
        AiModelCreateRequest zero = create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("http://localhost"), unique("zero"), null)
                .temperature(java.math.BigDecimal.ZERO).maxTokens(1);
        assertThat(models.create(zero).getTemperature()).isEqualByComparingTo("0");
        AiModelCreateRequest two = create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("http://localhost"), unique("two"), null)
                .temperature(new java.math.BigDecimal("2"));
        assertThat(models.create(two).getTemperature()).isEqualByComparingTo("2");
        assertBusinessRule(() -> models.create(create(AiProvider.QWEN, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("http://localhost"), unique("hot"), null)
                .temperature(new java.math.BigDecimal("2.001"))));
        assertBusinessRule(() -> models.create(create(AiProvider.DEEPSEEK, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("http://localhost"), unique("tokens"), null).maxTokens(0)));
    }

    @Test void scenarios28_33_36_37_updateWithoutApiKeyKeepsSecretAndUpdatesContractFields() {
        AiModelDto created = bearer("keep-secret", TEST_SECRET);
        String ref = secretRef(created);
        String cipher = cipher(ref);
        AiModelUpdateRequest update = update(created).remark("updated remark")
                .modelType(AiModelType.MULTIMODAL).protocol(AiProtocol.OLLAMA).authType(AiAuthType.BEARER_API_KEY);

        AiModelDto changed = models.update(created.getId(), update);
        assertThat(changed.getVersion()).isEqualTo(1);
        assertThat(changed.getRemark()).isEqualTo("updated remark");
        assertThat(changed.getModelType()).isEqualTo(AiModelType.MULTIMODAL);
        assertThat(changed.getProtocol()).isEqualTo(AiProtocol.OLLAMA);
        assertThat(secretRef(changed)).isEqualTo(ref);
        assertThat(cipher(ref)).isEqualTo(cipher);
    }

    @Test void scenarios29_35_replacingSecretReusesReferenceAndStaleUpdateRollsBack() {
        AiModelDto created = bearer("replace-secret", TEST_SECRET);
        String ref = secretRef(created);
        String oldCipher = cipher(ref);
        AiModelDto replaced = models.update(created.getId(), update(created).apiKey("replacement-secret-C3D4"));

        assertThat(secretRef(replaced)).isEqualTo(ref);
        assertThat(cipher(ref)).isNotEqualTo(oldCipher);
        assertThat(replaced.getApiKeyMasked()).isEqualTo("****C3D4");
        String replacementCipher = cipher(ref);
        assertThatThrownBy(() -> models.update(created.getId(), update(created).apiKey("must-rollback-E5F6")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo("DATA_VERSION_CONFLICT"));
        assertThat(cipher(ref)).isEqualTo(replacementCipher);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_secret", Integer.class)).isEqualTo(1);
    }

    @Test void scenarios30_32_clearDeletesSecretWhileBlankKeyIsValidationFailure() {
        AiModelDto created = bearer("clear-secret", TEST_SECRET);
        String ref = secretRef(created);
        AiModelUpdateRequest clear = update(created).authType(AiAuthType.NONE).clearApiKey(true);
        AiModelDto changed = models.update(created.getId(), clear);

        assertThat(changed.getApiKeyConfigured()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_secret WHERE secret_ref=?", Integer.class, ref)).isZero();
        assertBusinessRule(() -> models.update(changed.getId(), update(changed).apiKey("")));
    }

    @Test void scenario31_apiKeyAndClearTogetherReturnsSanitized422() throws Exception {
        AiModelDto created = bearer("conflicting-secret", TEST_SECRET);
        String leaked = "request-secret-must-not-leak";
        String json = """
                {"name":"name","provider":"OPENAI","modelName":"%s","modelType":"CHAT",
                 "protocol":"OPENAI_COMPATIBLE","authType":"BEARER_API_KEY","baseUrl":"http://localhost",
                 "apiKey":"%s","clearApiKey":true,"version":0}
                """.formatted(created.getModelName(), leaked);
        String body = mvc.perform(put("/api/v1/ai/models/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(leaked);
    }

    @Test void scenarios38_44_enableDisableUseOptimisticLockAndRejectNoOpTransitions() {
        AiModelDto created = models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("http://localhost"), unique("state"), null).enabled(false));
        AiModelDto enabled = models.enable(created.getId(), new AiModelStatusChangeRequest(0));
        assertThat(enabled.getEnabled()).isTrue();
        assertThat(enabled.getVersion()).isEqualTo(1);
        assertBusinessRule(() -> models.enable(created.getId(), new AiModelStatusChangeRequest(1)));
        assertConflict(() -> models.disable(created.getId(), new AiModelStatusChangeRequest(0)));
        AiModelDto disabled = models.disable(created.getId(), new AiModelStatusChangeRequest(1));
        assertThat(disabled.getEnabled()).isFalse();
        assertThat(disabled.getVersion()).isEqualTo(2);
        assertBusinessRule(() -> models.disable(created.getId(), new AiModelStatusChangeRequest(2)));
    }

    @Test void scenario42_bearerModelWithoutSecretCannotBeEnabledOrTested() {
        AiModelDto created = models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, URI.create("http://localhost"), unique("dangling"), null).enabled(false));
        jdbc.update("UPDATE ai_model SET auth_type='BEARER_API_KEY' WHERE id=?", Long.valueOf(created.getId()));
        assertBusinessRule(() -> models.enable(created.getId(), new AiModelStatusChangeRequest(0)));
        assertBusinessRule(() -> models.testConnection(created.getId()));
    }

    @Test void scenarios45_49_openAiCompatibleUsesExactBasePathAndAuthType() {
        String modelName = unique("compatible");
        AiModelDto bearer = models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.BEARER_API_KEY, endpoint("compatible/v1", modelName), modelName, TEST_SECRET));
        var bearerResult = models.testConnection(bearer.getId());
        assertThat(bearerResult.getSuccess()).isTrue();
        assertThat(bearerResult.getLatencyMs()).isNotNegative();
        assertThat(AUTHORIZATION.get("/compatible/v1/models")).isEqualTo("Bearer " + TEST_SECRET);

        String noneName = unique("compatible-none");
        AiModelDto none = models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.NONE, endpoint("compatible", noneName), noneName, null));
        assertThat(models.testConnection(none.getId()).getSuccess()).isTrue();
        assertThat(AUTHORIZATION).doesNotContainKey("/compatible/models");
    }

    @Test void scenarios50_53_providerFailuresUseStableSanitizedCategories() throws Exception {
        AiModelDto unauthorized = compatibleAt("unauthorized", "auth-model", TEST_SECRET);
        assertThat(models.testConnection(unauthorized.getId()).getErrorCode()).isEqualTo("AUTHENTICATION_FAILED");
        AiModelDto missing = compatibleAt("missing", "missing-model", null);
        var missingResult = models.testConnection(missing.getId());
        assertThat(missingResult.getSuccess()).isFalse();
        assertThat(missingResult.getErrorCode()).isEqualTo("MODEL_NOT_FOUND");
        AiModelDto error = compatibleAt("error", "error-model", null);
        String response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/ai/models/{id}/test", error.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains("PROTOCOL_ERROR").doesNotContain(RAW_PROVIDER_SECRET);
        AiModelDto slow = compatibleAt("slow", "slow-model", null);
        assertThat(models.testConnection(slow.getId()).getErrorCode()).isEqualTo("TIMEOUT");
    }

    @Test void scenarios54_60_ollamaSupportsLocalHttpBearerNoneAndSafeFailures() {
        String noneName = unique("ollama-none");
        AiModelDto none = models.create(create(AiProvider.OLLAMA, AiProtocol.OLLAMA, AiAuthType.NONE,
                endpoint("ollama", noneName), noneName, null));
        assertThat(models.testConnection(none.getId()).getSuccess()).isTrue();
        assertThat(AUTHORIZATION).doesNotContainKey("/ollama/api/tags");

        String bearerName = unique("ollama-bearer");
        AiModelDto bearer = models.create(create(AiProvider.OLLAMA, AiProtocol.OLLAMA,
                AiAuthType.BEARER_API_KEY, endpoint("ollama-auth", bearerName), bearerName, TEST_SECRET));
        assertThat(models.testConnection(bearer.getId()).getSuccess()).isTrue();
        assertThat(AUTHORIZATION.get("/ollama-auth/api/tags")).isEqualTo("Bearer " + TEST_SECRET);

        String missingName = unique("ollama-missing");
        AiModelDto missing = models.create(create(AiProvider.OLLAMA, AiProtocol.OLLAMA, AiAuthType.NONE,
                endpoint("ollama-missing", missingName), missingName, null));
        assertThat(models.testConnection(missing.getId()).getErrorCode()).isEqualTo("MODEL_NOT_FOUND");

        AiModelDto network = models.create(create(AiProvider.OLLAMA, AiProtocol.OLLAMA, AiAuthType.NONE,
                URI.create("http://127.0.0.1:1"), unique("network"), null).timeoutSeconds(1));
        assertThat(models.testConnection(network.getId()).getErrorCode()).isEqualTo("NETWORK_ERROR");
    }

    @Test void scenarios61_65_testConnectionIsReadOnlyAcrossModelSecretAndBusinessTables() {
        String name = unique("readonly");
        AiModelDto model = models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.BEARER_API_KEY, endpoint("compatible", name), name, TEST_SECRET));
        String ref = secretRef(model);
        Map<String, Object> beforeModel = jdbc.queryForMap("SELECT version, enabled, update_time FROM ai_model WHERE id=?",
                Long.valueOf(model.getId()));
        String beforeSecret = cipher(ref);
        int analyses = count("ai_analysis");
        int extractions = count("ai_extraction_task");
        int operations = count("operation_log");

        assertThat(models.testConnection(model.getId()).getSuccess()).isTrue();

        assertThat(jdbc.queryForMap("SELECT version, enabled, update_time FROM ai_model WHERE id=?",
                Long.valueOf(model.getId()))).isEqualTo(beforeModel);
        assertThat(cipher(ref)).isEqualTo(beforeSecret);
        assertThat(count("ai_analysis")).isEqualTo(analyses);
        assertThat(count("ai_extraction_task")).isEqualTo(extractions);
        assertThat(count("operation_log")).isEqualTo(operations);
    }

    @Test void scenarios66_72_validationAndApplicationOutputNeverExposeApiKey(CapturedOutput output) throws Exception {
        String json = """
                {"name":"name","provider":"OPENAI","modelName":"validation-model","modelType":"CHAT",
                 "protocol":"OPENAI_COMPATIBLE","authType":"BEARER_API_KEY","baseUrl":"http://localhost",
                 "apiKey":""}
                """;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ai/models")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("apiKey"))
                .andExpect(jsonPath("$.data.fieldErrors[0].rejectedValue").value("***REDACTED***"));

        models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE, AiAuthType.BEARER_API_KEY,
                URI.create("http://localhost"), unique("log-safe"), TEST_SECRET));
        assertThat(output.getAll()).doesNotContain(TEST_SECRET);
    }

    @Test void scenarios73_76_adapterSelectionDependsOnProtocolNotProviderBrand() {
        String openAiName = unique("openai-brand");
        AiModelDto openAi = models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE, AiAuthType.NONE,
                endpoint("compatible", openAiName), openAiName, null));
        String customName = unique("custom-brand");
        AiModelDto custom = models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE, AiAuthType.NONE,
                endpoint("compatible", customName), customName, null));
        String crossedName = unique("crossed-brand");
        AiModelDto crossed = models.create(create(AiProvider.OPENAI, AiProtocol.OLLAMA, AiAuthType.NONE,
                endpoint("ollama", crossedName), crossedName, null));

        assertThat(models.testConnection(openAi.getId()).getSuccess()).isTrue();
        assertThat(models.testConnection(custom.getId()).getSuccess()).isTrue();
        assertThat(models.testConnection(crossed.getId()).getSuccess()).isTrue();
        assertThat(REQUEST_PATHS).contains("/compatible/models", "/ollama/api/tags");
    }

    @Test void scenarios77_82_listGetMissingAndAllIdsFollowFrozenContract() {
        AiModelDto first = models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE, AiAuthType.NONE,
                URI.create("http://localhost"), unique("first"), null).priority(20));
        AiModelDto second = models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE, AiAuthType.NONE,
                URI.create("http://localhost"), unique("second"), null).priority(10));
        assertThat(models.list()).extracting(AiModelDto::getId).contains(first.getId(), second.getId());
        assertThat(models.get(first.getId()).getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> models.get("999999999"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(models.list()).allSatisfy(dto -> assertThat(dto.getId()).matches("[0-9]+"));
    }

    @Test void invalidPersistedConfigurationReturns422BeforeNetworkCall() {
        AiModelDto model = models.create(create(AiProvider.CUSTOM, AiProtocol.OPENAI_COMPATIBLE, AiAuthType.NONE,
                URI.create("http://localhost"), unique("bad-saved"), null));
        jdbc.update("UPDATE ai_model SET api_base_url='not-a-uri' WHERE id=?", Long.valueOf(model.getId()));
        assertBusinessRule(() -> models.testConnection(model.getId()));
    }

    private AiModelDto bearer(String prefix, String apiKey) {
        String name = unique(prefix);
        return models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE,
                AiAuthType.BEARER_API_KEY, URI.create("http://localhost"), name, apiKey));
    }

    private AiModelDto compatibleAt(String mode, String name, String apiKey) {
        AiAuthType auth = apiKey == null ? AiAuthType.NONE : AiAuthType.BEARER_API_KEY;
        String modelName = unique(name);
        return models.create(create(AiProvider.OPENAI, AiProtocol.OPENAI_COMPATIBLE,
                auth, endpoint(mode, modelName), modelName, apiKey));
    }

    private AiModelCreateRequest create(AiProvider provider, AiProtocol protocol, AiAuthType auth,
            URI baseUrl, String modelName, String apiKey) {
        return new AiModelCreateRequest("Model " + modelName, provider, modelName, AiModelType.CHAT,
                protocol, auth, baseUrl).apiKey(apiKey).timeoutSeconds(2);
    }

    private AiModelUpdateRequest update(AiModelDto model) {
        return new AiModelUpdateRequest(model.getName(), model.getProvider(), model.getModelName(),
                model.getModelType(), model.getProtocol(), model.getAuthType(), model.getBaseUrl(), model.getVersion())
                .supportsVision(model.getSupportsVision()).supportsJson(model.getSupportsJson()).local(model.getLocal())
                .enabled(model.getEnabled()).priority(model.getPriority()).timeoutSeconds(model.getTimeoutSeconds())
                .temperature(model.getTemperature()).maxTokens(model.getMaxTokens()).remark(model.getRemark());
    }

    private String secretRef(AiModelDto model) {
        return jdbc.queryForObject("SELECT api_key_ref FROM ai_model WHERE id=?", String.class,
                Long.valueOf(model.getId()));
    }

    private String cipher(String ref) {
        return jdbc.queryForObject("SELECT HEX(encrypted_value) FROM ai_secret WHERE secret_ref=?", String.class, ref);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static URI endpoint(String path, String model) {
        return URI.create("http://127.0.0.1:" + port + "/" + path + "?model=" + model);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void assertBusinessRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
                    assertThat(business.getStatus().value()).isEqualTo(422);
                });
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo("DATA_VERSION_CONFLICT"));
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        REQUEST_PATHS.add(path);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null) AUTHORIZATION.remove(path);
        else AUTHORIZATION.put(path, authorization);
        String model = query(exchange.getRequestURI().getRawQuery(), "model");
        int status = 200;
        String body;
        if (path.contains("/slow/")) {
            try {
                Thread.sleep(2_500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (path.contains("/unauthorized/")) {
            status = 401;
            body = "{\"error\":\"" + RAW_PROVIDER_SECRET + "\"}";
        } else if (path.contains("/error/")) {
            status = 500;
            body = "{\"error\":\"" + RAW_PROVIDER_SECRET + "\"}";
        } else if (path.contains("missing")) {
            body = path.endsWith("/api/tags") ? "{\"models\":[]}" : "{\"data\":[]}";
        } else if (path.endsWith("/api/tags")) {
            body = "{\"models\":[{\"name\":\"" + model + "\"}]}";
        } else {
            body = "{\"data\":[{\"id\":\"" + model + "\"}]}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // A timeout test intentionally closes the client side first.
        } finally {
            exchange.close();
        }
    }

    private static String query(String rawQuery, String key) {
        if (rawQuery == null) return "";
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair[0].equals(key)) return pair.length == 2 ? pair[1] : "";
        }
        return "";
    }
}

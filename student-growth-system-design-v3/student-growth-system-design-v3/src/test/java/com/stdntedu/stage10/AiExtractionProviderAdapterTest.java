package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderRequest;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResultParser;
import com.stdntedu.ai.extraction.provider.AiProviderException;
import com.stdntedu.ai.extraction.provider.ProviderVisualInput;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.provider.OllamaProviderClient;
import com.stdntedu.ai.model.provider.OpenAiCompatibleProviderClient;
import com.stdntedu.ai.model.provider.ProviderErrorSanitizer;
import com.stdntedu.generated.model.AiAuthType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiExtractionProviderAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void openAiCompatibleMultimodalSuccess(@TempDir Path directory) throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"questions\\\":[{\\\"questionText\\\":\\\"2+2\\\",\\\"confidence\\\":0.9}]}\"}}]}");
        });
        var result = openAi().extract(model(AiAuthType.BEARER_API_KEY), "secret-key".toCharArray(),
                request(directory));
        assertThat(result.questions()).singleElement().satisfies(question ->
                assertThat(question.questionText()).isEqualTo("2+2"));
        assertThat(authorization.get()).isEqualTo("Bearer secret-key");
        assertThat(objectMapper.readTree(body.get()).path("messages").path(0).path("content").path(1)
                .path("image_url").path("url").asText()).startsWith("data:image/png;base64,");
    }

    @Test void ollamaMultimodalSuccessUsesPlainBase64(@TempDir Path directory) throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"message\":{\"content\":\"{\\\"questions\\\":[{\\\"questionText\\\":\\\"x\\\"}]}\"}}");
        });
        var result = ollama().extract(model(AiAuthType.NONE), null, request(directory));
        assertThat(result.questions()).hasSize(1);
        String image = objectMapper.readTree(body.get()).path("messages").path(0).path("images").path(0).asText();
        assertThat(image).doesNotStartWith("data:").isEqualTo("AQIDBA==");
    }

    @Test void malformedProviderJsonIsSanitized(@TempDir Path directory) throws Exception {
        server.createContext("/chat/completions", exchange -> respond(exchange, 200,
                "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"));
        assertThatThrownBy(() -> openAi().extract(model(AiAuthType.NONE), null, request(directory)))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("PROVIDER_RESPONSE_INVALID");
                    assertThat(ex.getMessage()).doesNotContain("not-json");
                });
    }

    @Test void provider401IsSanitized(@TempDir Path directory) throws Exception {
        server.createContext("/chat/completions", exchange -> respond(exchange, 401, "secret response"));
        assertProviderError(directory, "PROVIDER_AUTHENTICATION_FAILED");
    }

    @Test void provider500IsSanitized(@TempDir Path directory) throws Exception {
        server.createContext("/chat/completions", exchange -> respond(exchange, 500, "internal raw body"));
        assertProviderError(directory, "PROVIDER_REQUEST_FAILED");
    }

    @Test void providerTimeoutIsSanitized(@TempDir Path directory) throws Exception {
        server.createContext("/chat/completions", exchange -> {
            try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, "{}");
        });
        AiModelEntity model = model(AiAuthType.NONE);
        model.setTimeoutSeconds(1);
        assertThatThrownBy(() -> openAi().extract(model, null, request(directory)))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.code()).isEqualTo("PROVIDER_TIMEOUT"));
    }

    @Test void oversizedProviderResponseIsRejectedAtSixteenMiBBoundary(@TempDir Path directory) throws Exception {
        server.createContext("/chat/completions", exchange -> {
            long size = 16L * 1024 * 1024 + 1;
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, size);
            byte[] chunk = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int length = (int) Math.min(chunk.length, remaining);
                exchange.getResponseBody().write(chunk, 0, length);
                remaining -= length;
            }
            exchange.close();
        });
        assertThatThrownBy(() -> openAi().extract(model(AiAuthType.NONE), null, request(directory)))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.code()).isEqualTo("PROVIDER_RESPONSE_INVALID"));
    }

    @Test void rawPdfMimeIsNeverAcceptedByProviderRequest(@TempDir Path directory) throws Exception {
        Path image = directory.resolve("page.jpg");
        Files.write(image, new byte[] {1, 2, 3});
        AiExtractionProviderRequest request = new AiExtractionProviderRequest("prompt",
                List.of(new ProviderVisualInput(image, "image/jpeg", 0, 1, 3)), directory);
        server.createContext("/api/chat", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).doesNotContain("application/pdf");
            respond(exchange, 200, "{\"message\":{\"content\":\"{\\\"questions\\\":[{\\\"questionText\\\":\\\"x\\\"}]}\"}}");
        });
        assertThat(ollama().extract(model(AiAuthType.NONE), null, request).questions()).hasSize(1);
    }

    private void assertProviderError(Path directory, String code) throws Exception {
        assertThatThrownBy(() -> openAi().extract(model(AiAuthType.NONE), null, request(directory)))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }

    private AiExtractionProviderRequest request(Path directory) throws Exception {
        Path image = directory.resolve("image.png");
        Files.write(image, new byte[] {1, 2, 3, 4});
        return new AiExtractionProviderRequest("JSON only",
                List.of(new ProviderVisualInput(image, "image/png", 0, null, 4)), directory);
    }

    private AiModelEntity model(AiAuthType authType) {
        AiModelEntity model = new AiModelEntity();
        model.setApiBaseUrl(baseUrl);
        model.setModelName("vision-model");
        model.setAuthType(authType);
        model.setTimeoutSeconds(5);
        return model;
    }

    private OpenAiCompatibleProviderClient openAi() {
        return new OpenAiCompatibleProviderClient(objectMapper, new ProviderErrorSanitizer(),
                new AiExtractionProviderResultParser(objectMapper));
    }

    private OllamaProviderClient ollama() {
        return new OllamaProviderClient(objectMapper, new ProviderErrorSanitizer(),
                new AiExtractionProviderResultParser(objectMapper));
    }

    private void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

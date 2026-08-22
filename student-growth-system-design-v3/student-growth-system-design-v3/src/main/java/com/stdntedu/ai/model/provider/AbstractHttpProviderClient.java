package com.stdntedu.ai.model.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderRequest;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResult;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResultParser;
import com.stdntedu.ai.extraction.provider.AiProviderException;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.provider.ProviderErrorSanitizer.ProviderErrorCode;
import com.stdntedu.generated.model.AiAuthType;

abstract class AbstractHttpProviderClient implements AiProviderClient {
    private final ObjectMapper objectMapper;
    private final ProviderErrorSanitizer errors;
    private final AiExtractionProviderResultParser extractionParser;
    @Value("${app.ai.provider.max-response-bytes:4194304}")
    private int maxResponseBytes = 4 * 1024 * 1024;

    AbstractHttpProviderClient(ObjectMapper objectMapper, ProviderErrorSanitizer errors,
            AiExtractionProviderResultParser extractionParser) {
        this.objectMapper = objectMapper;
        this.errors = errors;
        this.extractionParser = extractionParser;
    }

    @Override
    public ProviderConnectionResult testConnection(AiModelEntity model, char[] secret) {
        long started = System.nanoTime();
        byte[] body = null;
        try {
            URI endpoint = endpoint(URI.create(model.getApiBaseUrl()));
            Duration timeout = Duration.ofSeconds(model.getTimeoutSeconds());
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Accept", "application/json").GET();
            if (model.getAuthType() == AiAuthType.BEARER_API_KEY) {
                if (secret == null || secret.length == 0) {
                    return errors.failure(ProviderErrorCode.AUTHENTICATION_FAILED, elapsed(started));
                }
                request.header("Authorization", "Bearer " + new String(secret));
            }
            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                body = readBounded(input);
            }
            long latency = elapsed(started);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return errors.failure(ProviderErrorCode.AUTHENTICATION_FAILED, latency);
            }
            if (response.statusCode() == 404) {
                return errors.failure(ProviderErrorCode.MODEL_NOT_FOUND, latency);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return errors.failure(ProviderErrorCode.PROTOCOL_ERROR, latency);
            }
            JsonNode root = objectMapper.readTree(body);
            if (!validResponse(root)) return errors.failure(ProviderErrorCode.PROTOCOL_ERROR, latency);
            return modelExists(root, model.getModelName()) ? errors.success(latency)
                    : errors.failure(ProviderErrorCode.MODEL_NOT_FOUND, latency);
        } catch (HttpTimeoutException ex) {
            return errors.failure(ProviderErrorCode.TIMEOUT, elapsed(started));
        } catch (ProviderResponseTooLargeException ex) {
            return errors.failure(ProviderErrorCode.RESPONSE_TOO_LARGE, elapsed(started));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return errors.failure(ProviderErrorCode.PROTOCOL_ERROR, elapsed(started));
        } catch (IOException ex) {
            return errors.failure(ProviderErrorCode.NETWORK_ERROR, elapsed(started));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return errors.failure(ProviderErrorCode.NETWORK_ERROR, elapsed(started));
        } finally {
            if (body != null) Arrays.fill(body, (byte) 0);
        }
    }

    @Override
    public AiExtractionProviderResult extract(AiModelEntity model, char[] secret,
            AiExtractionProviderRequest extraction) {
        Path requestBody = null;
        byte[] responseBody = null;
        try {
            requestBody = Files.createTempFile(extraction.workingDirectory(), "provider-request-", ".json");
            try (OutputStream output = Files.newOutputStream(requestBody)) {
                writeExtractionRequest(output, model, extraction);
            }
            URI endpoint = endpoint(URI.create(model.getApiBaseUrl()), extractionEndpointPath());
            Duration timeout = Duration.ofSeconds(model.getTimeoutSeconds());
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Accept", "application/json").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofFile(requestBody));
            authorize(request, model, secret);
            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                responseBody = readBounded(input);
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw provider("PROVIDER_AUTHENTICATION_FAILED", "provider authentication failed");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw provider("PROVIDER_REQUEST_FAILED", "provider request failed");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            String content = extractionContent(root);
            if (content == null || content.isBlank()) throw provider("PROVIDER_RESPONSE_INVALID",
                    "provider response was invalid");
            return extractionParser.parse(content);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw provider("PROVIDER_TIMEOUT", "provider request timed out");
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw provider("PROVIDER_PROTOCOL_ERROR", "provider request configuration was invalid");
        } catch (IOException ex) {
            throw provider("PROVIDER_NETWORK_ERROR", "provider network request failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw provider("PROVIDER_NETWORK_ERROR", "provider network request failed");
        } finally {
            if (responseBody != null) Arrays.fill(responseBody, (byte) 0);
            if (requestBody != null) {
                try { Files.deleteIfExists(requestBody); } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public AiStructuredGenerationResult generate(AiModelEntity model, char[] secret,
            AiStructuredGenerationRequest generation) {
        byte[] requestBody = null;
        byte[] responseBody = null;
        try {
            requestBody = objectMapper.writeValueAsBytes(generationRequest(model, generation.prompt()));
            URI endpoint = endpoint(URI.create(model.getApiBaseUrl()), extractionEndpointPath());
            Duration timeout = Duration.ofSeconds(model.getTimeoutSeconds());
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Accept", "application/json").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            authorize(request, model, secret);
            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                responseBody = readBounded(input);
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw provider("PROVIDER_AUTHENTICATION_FAILED", "provider authentication failed");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw provider("PROVIDER_REQUEST_FAILED", "provider request failed");
            }
            return generationResult(objectMapper.readTree(responseBody));
        } catch (AiProviderException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw provider("PROVIDER_TIMEOUT", "provider request timed out");
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw provider("PROVIDER_PROTOCOL_ERROR", "provider request configuration was invalid");
        } catch (IOException ex) {
            throw provider("PROVIDER_NETWORK_ERROR", "provider network request failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw provider("PROVIDER_NETWORK_ERROR", "provider network request failed");
        } finally {
            if (requestBody != null) Arrays.fill(requestBody, (byte) 0);
            if (responseBody != null) Arrays.fill(responseBody, (byte) 0);
        }
    }

    protected abstract String endpointPath();
    protected abstract boolean validResponse(JsonNode root);
    protected abstract boolean modelExists(JsonNode root, String modelName);
    protected abstract String extractionEndpointPath();
    protected abstract void writeExtractionRequest(OutputStream output, AiModelEntity model,
            AiExtractionProviderRequest request) throws IOException;
    protected abstract String extractionContent(JsonNode root);
    protected abstract JsonNode generationRequest(AiModelEntity model, String prompt);
    protected abstract AiStructuredGenerationResult generationResult(JsonNode root);

    protected ObjectMapper objectMapper() { return objectMapper; }

    protected Integer nullableNonNegativeInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (!node.canConvertToInt() || node.intValue() < 0) {
            throw provider("PROVIDER_RESPONSE_INVALID", "provider response was invalid");
        }
        return node.intValue();
    }

    protected AiProviderException invalidProviderResponse() {
        return provider("PROVIDER_RESPONSE_INVALID", "provider response was invalid");
    }

    protected void writeBase64(OutputStream output, Path image) throws IOException {
        try (InputStream input = Files.newInputStream(image);
             OutputStream encoded = java.util.Base64.getEncoder().wrap(new NonClosingOutputStream(output))) {
            input.transferTo(encoded);
        }
    }

    protected void writeUtf8(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected String jsonString(String value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    private URI endpoint(URI base) throws URISyntaxException {
        return endpoint(base, endpointPath());
    }

    private URI endpoint(URI base, String endpointPath) throws URISyntaxException {
        String path = base.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (!path.endsWith("/")) path += "/";
        path += endpointPath;
        return new URI(base.getScheme(), base.getRawAuthority(), path, base.getRawQuery(), null);
    }

    private void authorize(HttpRequest.Builder request, AiModelEntity model, char[] secret) {
        if (model.getAuthType() != AiAuthType.BEARER_API_KEY) return;
        if (secret == null || secret.length == 0) {
            throw provider("PROVIDER_AUTHENTICATION_FAILED", "provider authentication failed");
        }
        request.header("Authorization", "Bearer " + new String(secret));
    }

    private AiProviderException provider(String code, String message) {
        return new AiProviderException(code, message);
    }

    private byte[] readBounded(InputStream input) throws IOException {
        int maximum = Math.max(1, maxResponseBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
        byte[] buffer = new byte[Math.min(maximum + 1, 8192)];
        while (true) {
            int remaining = maximum - output.size();
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining + 1));
            if (read < 0) return output.toByteArray();
            if (read > remaining) {
                throw new ProviderResponseTooLargeException();
            }
            output.write(buffer, 0, read);
        }
    }

    private long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private static final class NonClosingOutputStream extends java.io.FilterOutputStream {
        private NonClosingOutputStream(OutputStream output) { super(output); }
        @Override public void close() throws IOException { flush(); }
    }

    private static final class ProviderResponseTooLargeException extends AiProviderException {
        private ProviderResponseTooLargeException() {
            super("PROVIDER_RESPONSE_TOO_LARGE", "provider response exceeded the configured limit");
        }
    }
}

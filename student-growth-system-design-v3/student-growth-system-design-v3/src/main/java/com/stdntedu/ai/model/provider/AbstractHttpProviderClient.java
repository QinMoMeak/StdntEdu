package com.stdntedu.ai.model.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.provider.ProviderErrorSanitizer.ProviderErrorCode;
import com.stdntedu.generated.model.AiAuthType;

abstract class AbstractHttpProviderClient implements AiProviderClient {
    private final ObjectMapper objectMapper;
    private final ProviderErrorSanitizer errors;

    AbstractHttpProviderClient(ObjectMapper objectMapper, ProviderErrorSanitizer errors) {
        this.objectMapper = objectMapper;
        this.errors = errors;
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
            HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            body = response.body();
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

    protected abstract String endpointPath();
    protected abstract boolean validResponse(JsonNode root);
    protected abstract boolean modelExists(JsonNode root, String modelName);

    private URI endpoint(URI base) throws URISyntaxException {
        String path = base.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (!path.endsWith("/")) path += "/";
        path += endpointPath();
        return new URI(base.getScheme(), base.getRawAuthority(), path, base.getRawQuery(), null);
    }

    private long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }
}

package com.stdntedu.ai.model.provider;

import org.springframework.stereotype.Component;

@Component
public class ProviderErrorSanitizer {
    public ProviderConnectionResult success(long latencyMs) {
        return new ProviderConnectionResult(true, null, "connection succeeded", nonNegative(latencyMs));
    }

    public ProviderConnectionResult failure(ProviderErrorCode code, long latencyMs) {
        return new ProviderConnectionResult(false, code.name(), code.message(), nonNegative(latencyMs));
    }

    private long nonNegative(long latencyMs) {
        return Math.max(0, latencyMs);
    }

    public enum ProviderErrorCode {
        NETWORK_ERROR("provider network request failed"),
        AUTHENTICATION_FAILED("provider authentication failed"),
        MODEL_NOT_FOUND("configured model was not found"),
        PROTOCOL_ERROR("provider response did not match the configured protocol"),
        RESPONSE_TOO_LARGE("provider response exceeded the configured limit"),
        TIMEOUT("provider request timed out");

        private final String message;

        ProviderErrorCode(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }
}

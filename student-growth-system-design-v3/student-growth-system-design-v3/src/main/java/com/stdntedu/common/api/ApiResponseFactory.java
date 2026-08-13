package com.stdntedu.common.api;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ApiResponseFactory {

    public <T> ApiResponse<T> success(T data, String requestId) {
        return create("OK", "success", data, requestId);
    }

    public <T> ApiResponse<T> created(T data, String requestId) {
        return create("CREATED", "created", data, requestId);
    }

    public <T> ApiResponse<T> accepted(T data, String requestId) {
        return create("ACCEPTED", "accepted", data, requestId);
    }

    public ApiResponse<Void> successWithoutData(String requestId) {
        return create("OK", "success", null, requestId);
    }

    public ApiResponse<ErrorBody> error(String code, String message, String requestId, List<FieldErrorItem> fieldErrors) {
        return create(code, message, new ErrorBody(fieldErrors), requestId);
    }

    private <T> ApiResponse<T> create(String code, String message, T data, String requestId) {
        return new ApiResponse<>(code, message, data, requestId, OffsetDateTime.now());
    }

    public record ErrorBody(List<FieldErrorItem> fieldErrors) {
    }

    public record FieldErrorItem(String field, String message, Object rejectedValue) {
    }
}

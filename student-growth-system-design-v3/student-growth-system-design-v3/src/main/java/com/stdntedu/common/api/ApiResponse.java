package com.stdntedu.common.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(String code, String message, T data, String requestId, OffsetDateTime timestamp) {
}

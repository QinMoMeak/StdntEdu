package com.stdntedu.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseFactoryTest {
    private final ApiResponseFactory factory = new ApiResponseFactory();

    @Test
    void createsEnvelopeWithRequestIdAndData() {
        ApiResponse<String> response = factory.success("data", "request-1");
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo("data");
        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void calculatesPageTotals() {
        PageResult<String> result = PageResult.of(java.util.List.of("a"), 2, 20, 41);
        assertThat(result.totalPages()).isEqualTo(3);
    }
}

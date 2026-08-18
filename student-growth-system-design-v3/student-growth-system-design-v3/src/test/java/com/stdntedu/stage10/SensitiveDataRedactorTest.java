package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;

import com.stdntedu.ai.model.security.SensitiveDataRedactor;
import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTest {
    private final SensitiveDataRedactor redactor = new SensitiveDataRedactor();

    @Test void scenarios66_69_sensitiveNamesAreCaseInsensitiveAndFullyRedacted() {
        assertThat(redactor.redactValue("apiKey", "one")).isEqualTo("***REDACTED***");
        assertThat(redactor.redactValue("AUTHORIZATION", "two")).isEqualTo("***REDACTED***");
        assertThat(redactor.redactValue("refresh_Token", "three")).isEqualTo("***REDACTED***");
        assertThat(redactor.redactValue("client-secret", "four")).isEqualTo("***REDACTED***");
        assertThat(redactor.redactValue("Password", "five")).isEqualTo("***REDACTED***");
    }

    @Test void scenario70_ordinaryRejectedValueIsPreserved() {
        assertThat(redactor.redactValue("modelName", "ordinary-value")).isEqualTo("ordinary-value");
        assertThat(redactor.redactValue("apiKey", null)).isNull();
    }
}

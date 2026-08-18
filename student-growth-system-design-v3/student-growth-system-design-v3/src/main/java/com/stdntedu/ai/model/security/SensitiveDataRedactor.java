package com.stdntedu.ai.model.security;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class SensitiveDataRedactor {
    public static final String REDACTED = "***REDACTED***";
    private static final Set<String> SENSITIVE_PARTS = Set.of(
            "apikey", "authorization", "token", "secret", "password");

    public boolean isSensitiveName(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_PARTS.stream().anyMatch(normalized::contains);
    }

    public Object redactValue(String name, Object value) {
        return isSensitiveName(name) && value != null ? REDACTED : value;
    }
}

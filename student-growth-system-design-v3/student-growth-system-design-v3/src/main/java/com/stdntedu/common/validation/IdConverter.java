package com.stdntedu.common.validation;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class IdConverter {

    public Long toLong(String value) {
        if (value == null || value.isBlank() || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("ID must be a positive decimal string");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("ID is outside the supported range", ex);
        }
    }

    public String toString(Long value) {
        return value == null ? null : value.toString();
    }

    public List<Long> toLongs(Collection<String> values) {
        return values == null ? List.of() : values.stream().map(this::toLong).toList();
    }
}

package com.stdntedu.common.validation;

import java.util.Collection;
import java.util.List;

import com.stdntedu.common.exception.InvalidIdException;
import org.springframework.stereotype.Component;

@Component
public class IdConverter {

    public Long toLong(String value) {
        if (value == null || value.isBlank() || !value.matches("[1-9][0-9]*")) {
            throw new InvalidIdException();
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new InvalidIdException();
        }
    }

    public String toString(Long value) {
        return value == null ? null : value.toString();
    }

    public List<Long> toLongs(Collection<String> values) {
        return values == null ? List.of() : values.stream().map(this::toLong).toList();
    }
}

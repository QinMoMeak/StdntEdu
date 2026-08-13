package com.stdntedu.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class IdConverterTest {
    private final IdConverter converter = new IdConverter();

    @Test
    void convertsPositiveIdsAndCollections() {
        assertThat(converter.toLong("42")).isEqualTo(42L);
        assertThat(converter.toString(42L)).isEqualTo("42");
        assertThat(converter.toLongs(List.of("1", "2"))).containsExactly(1L, 2L);
    }

    @Test
    void rejectsBlankZeroNegativeAndNonNumericIds() {
        for (String value : java.util.stream.Stream.of((String) null, "", "0", "-1", "abc").toList()) {
            assertThatThrownBy(() -> converter.toLong(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}

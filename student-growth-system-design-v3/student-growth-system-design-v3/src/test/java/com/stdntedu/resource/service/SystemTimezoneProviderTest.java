package com.stdntedu.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemTimezoneProviderTest {
    private static final Instant NOW = Instant.parse("2026-08-22T16:30:00Z");

    @Test
    void fixedClockAndSystemTimezoneAreAuthoritativeWhenJvmDefaultDiffers() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            SystemTimezoneProvider time = provider("Asia/Shanghai");

            assertThat(time.instant()).isEqualTo(NOW);
            assertThat(time.today()).isEqualTo(LocalDate.of(2026, 8, 23));
            assertThat(time.localDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 23, 0, 30));
            assertThat(time.offsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-08-23T00:30:00+08:00"));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void datetimeConversionsUseTheConfiguredBusinessZone() {
        SystemTimezoneProvider time = provider("Asia/Shanghai");

        assertThat(time.toLocalDateTime(OffsetDateTime.parse("2026-08-22T12:00:00Z")))
                .isEqualTo(LocalDateTime.of(2026, 8, 22, 20, 0));
        assertThat(time.toOffsetDateTime(LocalDateTime.of(2026, 8, 22, 20, 0)))
                .isEqualTo(OffsetDateTime.of(2026, 8, 22, 20, 0, 0, 0, ZoneOffset.ofHours(8)));
        assertThat(time.toOffsetDateTime(null)).isNull();
    }

    private SystemTimezoneProvider provider(String zone) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT config_value FROM system_config WHERE config_key = 'system.timezone'",
                String.class)).thenReturn(zone);
        return new SystemTimezoneProvider(jdbc, Clock.fixed(NOW, ZoneId.of("UTC")));
    }
}

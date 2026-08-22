package com.stdntedu.resource.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SystemTimezoneProvider {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SystemTimezoneProvider(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public ZoneId get() {
        String value = jdbc.queryForObject(
                "SELECT config_value FROM system_config WHERE config_key = 'system.timezone'", String.class);
        return ZoneId.of(value);
    }

    public Instant instant() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(instant(), get());
    }

    public LocalDateTime localDateTime() {
        return LocalDateTime.ofInstant(instant(), get());
    }

    public OffsetDateTime offsetDateTime() {
        return instant().atZone(get()).toOffsetDateTime();
    }

    public LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return LocalDateTime.ofInstant(value.toInstant(), get());
    }

    public OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(get()).toOffsetDateTime();
    }
}

package com.stdntedu.resource.service;

import java.time.ZoneId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SystemTimezoneProvider {
    private final JdbcTemplate jdbc;

    public SystemTimezoneProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ZoneId get() {
        String value = jdbc.queryForObject(
                "SELECT config_value FROM system_config WHERE config_key = 'system.timezone'", String.class);
        return ZoneId.of(value);
    }
}

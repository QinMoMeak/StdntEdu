package com.stdntedu.common.web;

import java.time.OffsetDateTime;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class HealthController {
    private final Flyway flyway;
    private final String application;
    private final String version;

    public HealthController(Flyway flyway,
            @Value("${spring.application.name:student-growth-system}") String application,
            @Value("${app.version:dev}") String version) {
        this.flyway = flyway;
        this.application = application;
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        var current = flyway.info().current();
        return Map.of("status", "UP", "application", application, "version", version,
                "timestamp", OffsetDateTime.now(), "database", "UP",
                "flywayVersion", current == null ? "none" : current.getVersion().toString());
    }
}

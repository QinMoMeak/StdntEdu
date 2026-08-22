package com.stdntedu.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationTimeConfig {
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}

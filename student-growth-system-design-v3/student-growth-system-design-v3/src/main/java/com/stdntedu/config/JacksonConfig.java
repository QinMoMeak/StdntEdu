package com.stdntedu.config;

import java.time.ZoneId;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
                .setTimeZone(java.util.TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")));
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        return mapper;
    }
}

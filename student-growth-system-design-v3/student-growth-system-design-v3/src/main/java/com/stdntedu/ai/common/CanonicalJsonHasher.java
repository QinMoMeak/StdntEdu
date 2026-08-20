package com.stdntedu.ai.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CanonicalJsonHasher {
    private final ObjectMapper objectMapper;

    public CanonicalJsonHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(JsonNode canonicalValue) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(canonicalValue).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("canonical JSON hash could not be created", ex);
        }
    }
}

package com.stdntedu.ai.extraction.provider;

import java.math.BigDecimal;

public record AiKnowledgeCandidate(String knowledgeId, String knowledgeCode, String knowledgeName,
        BigDecimal confidence, boolean primary) { }

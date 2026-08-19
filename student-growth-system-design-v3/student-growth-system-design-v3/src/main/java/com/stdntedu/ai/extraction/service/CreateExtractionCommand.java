package com.stdntedu.ai.extraction.service;

import com.stdntedu.generated.model.WrongSource;

public record CreateExtractionCommand(String studentId, String subjectId, String gradeId, WrongSource sourceType,
        String sourceName, String examId, String modelId, boolean recognizeAnalysis, boolean matchKnowledge,
        boolean maskPersonalInfo) { }

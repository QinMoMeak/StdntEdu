package com.stdntedu.ai.analysis.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.analysis.generation.model.NormalizedStudyPlanGenerationRequest;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class StudyPlanGenerationPromptV1 {
    private final ObjectMapper objectMapper;

    public StudyPlanGenerationPromptV1(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public String render(NormalizedStudyPlanGenerationRequest request, StudyPlanGenerationContext context) {
        try {
            return """
                    Return exactly one JSON object and no Markdown or code fences.
                    Generate a study-plan proposal with fields title, planType, description, tasks.
                    Each task requires taskDate, taskType and title; optional link fields are resourceId,
                    wrongQuestionId, knowledgeId and examId. Allowed taskType values are
                    WRONG_QUESTION_REVIEW, RESOURCE_LEARNING, KNOWLEDGE_PRACTICE, EXAM_REVIEW, READING, OTHER.
                    Link rules: wrong-question tasks use only wrongQuestionId; resource tasks use only resourceId;
                    knowledge tasks use only knowledgeId; exam tasks use only examId; READING and OTHER use none.
                    Do not emit database IDs for the plan/tasks, studentId, status, version, completedTime,
                    actualDurationSeconds or sourceAnalysisId. Do not invent referenced IDs. Task dates must stay
                    within the requested range and expectedDurationSeconds must be non-negative. An empty tasks array
                    is valid when no safe task can be proposed. The server owns student, dates, status and versions.
                    Request constraints: %s
                    Allowed reference IDs: %s
                    """.formatted(objectMapper.writeValueAsString(request), objectMapper.writeValueAsString(context));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("generation prompt could not be rendered", ex);
        }
    }
}

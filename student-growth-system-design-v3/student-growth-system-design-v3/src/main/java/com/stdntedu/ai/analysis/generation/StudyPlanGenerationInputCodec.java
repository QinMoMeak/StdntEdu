package com.stdntedu.ai.analysis.generation;

import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.analysis.generation.model.NormalizedStudyPlanGenerationRequest;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationInput;
import com.stdntedu.ai.common.CanonicalJsonHasher;
import com.stdntedu.generated.model.StudyPlanGenerateRequest;
import org.springframework.stereotype.Component;

@Component
public class StudyPlanGenerationInputCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROMPT_VERSION = "study-plan-generation-v1";

    private final ObjectMapper objectMapper;
    private final ObjectMapper strictMapper;
    private final CanonicalJsonHasher hasher;

    public StudyPlanGenerationInputCodec(ObjectMapper objectMapper, CanonicalJsonHasher hasher) {
        this.objectMapper = objectMapper;
        this.strictMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.hasher = hasher;
    }

    public NormalizedStudyPlanGenerationRequest normalize(StudyPlanGenerateRequest request) {
        return new NormalizedStudyPlanGenerationRequest(request.getStudentId(), request.getStartDate(),
                request.getEndDate(), request.getDailyAvailableMinutes(), sorted(request.getSubjectIds()),
                sorted(request.getTargetKnowledgeIds()), !Boolean.FALSE.equals(request.getIncludeWrongQuestionReview()),
                !Boolean.FALSE.equals(request.getIncludeResources()), request.getModelId());
    }

    public String requestHash(NormalizedStudyPlanGenerationRequest request) {
        return hasher.hash(objectMapper.valueToTree(request));
    }

    public String encode(NormalizedStudyPlanGenerationRequest request) {
        try {
            return objectMapper.writeValueAsString(new StudyPlanGenerationInput(
                    SCHEMA_VERSION, PROMPT_VERSION, request));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("study plan generation input could not be encoded", ex);
        }
    }

    public StudyPlanGenerationInput decode(String inputJson) {
        try {
            StudyPlanGenerationInput input = strictMapper.readValue(inputJson, StudyPlanGenerationInput.class);
            if (input.schemaVersion() != SCHEMA_VERSION) {
                throw new GenerationFailure("UNSUPPORTED_INPUT_SCHEMA", "unsupported generation input schema");
            }
            if (!PROMPT_VERSION.equals(input.promptVersion())) {
                throw new GenerationFailure("UNSUPPORTED_PROMPT_VERSION", "unsupported generation prompt version");
            }
            if (input.request() == null) throw invalidInput();
            return input;
        } catch (GenerationFailure ex) {
            throw ex;
        } catch (JsonProcessingException | RuntimeException ex) {
            throw invalidInput();
        }
    }

    private List<String> sorted(List<String> values) {
        return values == null ? List.of() : values.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private GenerationFailure invalidInput() {
        return new GenerationFailure("INVALID_INPUT_JSON", "generation input was invalid");
    }
}

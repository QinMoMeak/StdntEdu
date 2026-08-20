package com.stdntedu.ai.analysis.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationProposal;
import com.stdntedu.ai.analysis.generation.model.StudyPlanTaskProposal;
import org.springframework.stereotype.Component;

@Component
public class StudyPlanGenerationProposalParser {
    private final ObjectMapper strictMapper;

    public StudyPlanGenerationProposalParser(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public StudyPlanGenerationProposal parse(String content) {
        try {
            StudyPlanGenerationProposal proposal = strictMapper.readValue(content, StudyPlanGenerationProposal.class);
            validate(proposal);
            return proposal;
        } catch (GenerationFailure ex) {
            throw ex;
        } catch (JsonProcessingException | RuntimeException ex) {
            throw invalid();
        }
    }

    private void validate(StudyPlanGenerationProposal proposal) {
        if (proposal == null || blank(proposal.title()) || proposal.title().length() > 255
                || blank(proposal.planType()) || proposal.planType().length() > 32 || proposal.tasks() == null) {
            throw invalid();
        }
        for (StudyPlanTaskProposal task : proposal.tasks()) {
            if (task == null || task.taskDate() == null || task.taskType() == null
                    || blank(task.title()) || task.title().length() > 255
                    || task.expectedDurationSeconds() != null && task.expectedDurationSeconds() < 0) {
                throw invalid();
            }
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private GenerationFailure invalid() {
        return new GenerationFailure("PROVIDER_RESPONSE_INVALID", "provider response was invalid");
    }
}

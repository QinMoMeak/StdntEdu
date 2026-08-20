package com.stdntedu.ai.analysis.generation;

import com.stdntedu.ai.analysis.entity.AiAnalysisEntity;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationContext;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationInput;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationProposal;
import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.ai.extraction.provider.AiProviderException;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.mapper.AiModelMapper;
import com.stdntedu.ai.model.provider.AiProviderClientRegistry;
import com.stdntedu.ai.model.provider.AiStructuredGenerationRequest;
import com.stdntedu.ai.model.provider.AiStructuredGenerationResult;
import com.stdntedu.ai.model.service.AiSecretService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.AiAuthType;
import com.stdntedu.generated.model.AiModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiStudyPlanGenerationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AiStudyPlanGenerationWorker.class);

    private final AiAnalysisMapper analyses;
    private final AiModelMapper models;
    private final AiSecretService secrets;
    private final AiProviderClientRegistry providers;
    private final StudyPlanGenerationInputCodec inputCodec;
    private final StudyPlanGenerationContextLoader contexts;
    private final StudyPlanGenerationPromptV1 promptV1;
    private final StudyPlanGenerationProposalParser proposals;
    private final AiStudyPlanGenerationCompletionService completion;
    private final AiStudyPlanGenerationFailureService failures;

    public AiStudyPlanGenerationWorker(AiAnalysisMapper analyses, AiModelMapper models, AiSecretService secrets,
            AiProviderClientRegistry providers, StudyPlanGenerationInputCodec inputCodec,
            StudyPlanGenerationContextLoader contexts, StudyPlanGenerationPromptV1 promptV1,
            StudyPlanGenerationProposalParser proposals, AiStudyPlanGenerationCompletionService completion,
            AiStudyPlanGenerationFailureService failures) {
        this.analyses = analyses;
        this.models = models;
        this.secrets = secrets;
        this.providers = providers;
        this.inputCodec = inputCodec;
        this.contexts = contexts;
        this.promptV1 = promptV1;
        this.proposals = proposals;
        this.completion = completion;
        this.failures = failures;
    }

    public void execute(Long analysisId) {
        if (analyses.claim(analysisId) != 1) return;
        AiStructuredGenerationResult result = null;
        try {
            AiAnalysisEntity analysis = analyses.selectById(analysisId);
            if (analysis == null) throw new GenerationFailure("ANALYSIS_NOT_FOUND", "analysis was not found");
            StudyPlanGenerationInput input = inputCodec.decode(analysis.getInputJson());
            AiModelEntity model = requireExecutableModel(analysis.getAiModelId());
            StudyPlanGenerationContext context = contexts.load(input.request());
            String prompt = promptV1.render(input.request(), context);
            result = generate(model, prompt);
            StudyPlanGenerationProposal proposal = proposals.parse(result.content());
            completion.complete(analysisId, input.request(), proposal,
                    result.promptTokens(), result.completionTokens());
        } catch (AiProviderException ex) {
            fail(analysisId, ex.code(), ex.getMessage(), result);
        } catch (GenerationFailure ex) {
            fail(analysisId, ex.code(), ex.getMessage(), result);
        } catch (BusinessException ex) {
            fail(analysisId, "PROPOSAL_VALIDATION_FAILED", "generated study plan violated domain rules", result);
        } catch (Exception ex) {
            fail(analysisId, "GENERATION_FAILED", "study plan generation failed", result);
        }
    }

    private AiStructuredGenerationResult generate(AiModelEntity model, String prompt) {
        AiStructuredGenerationRequest request = new AiStructuredGenerationRequest(prompt);
        if (model.getAuthType() == AiAuthType.BEARER_API_KEY) {
            if (model.getApiKeyRef() == null) throw new GenerationFailure(
                    "MODEL_CONFIGURATION_INVALID", "AI model configuration is invalid");
            return secrets.withDecryptedSecret(model.getApiKeyRef(), secret -> providers.generate(model, secret, request));
        }
        return providers.generate(model, null, request);
    }

    private AiModelEntity requireExecutableModel(Long modelId) {
        AiModelEntity model = models.selectById(modelId);
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())
                || model.getModelType() == AiModelType.EMBEDDING) {
            throw new GenerationFailure("MODEL_UNAVAILABLE", "AI model is unavailable");
        }
        return model;
    }

    private void fail(Long analysisId, String code, String message, AiStructuredGenerationResult result) {
        Integer promptTokens = result == null ? null : result.promptTokens();
        Integer completionTokens = result == null ? null : result.completionTokens();
        try {
            if (!failures.fail(analysisId, code, message, promptTokens, completionTokens)) {
                LOG.warn("AI study-plan failure transition was not applied for analysis {}", analysisId);
            }
        } catch (RuntimeException ex) {
            LOG.error("AI study-plan failure transition crashed for analysis {}", analysisId);
        }
    }
}

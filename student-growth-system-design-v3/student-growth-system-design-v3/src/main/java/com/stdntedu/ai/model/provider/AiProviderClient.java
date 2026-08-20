package com.stdntedu.ai.model.provider;

import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderRequest;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResult;
import com.stdntedu.generated.model.AiProtocol;

public interface AiProviderClient {
    AiProtocol protocol();
    ProviderConnectionResult testConnection(AiModelEntity model, char[] secret);
    AiExtractionProviderResult extract(AiModelEntity model, char[] secret, AiExtractionProviderRequest request);
    AiStructuredGenerationResult generate(AiModelEntity model, char[] secret,
            AiStructuredGenerationRequest request);
}

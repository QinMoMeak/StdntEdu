package com.stdntedu.ai.model.provider;

import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.generated.model.AiProtocol;

public interface AiProviderClient {
    AiProtocol protocol();
    ProviderConnectionResult testConnection(AiModelEntity model, char[] secret);
}

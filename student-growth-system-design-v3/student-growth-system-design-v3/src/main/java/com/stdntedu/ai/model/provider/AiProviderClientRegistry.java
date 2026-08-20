package com.stdntedu.ai.model.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderRequest;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResult;
import com.stdntedu.generated.model.AiProtocol;
import org.springframework.stereotype.Component;

@Component
public class AiProviderClientRegistry {
    private final Map<AiProtocol, AiProviderClient> clients = new EnumMap<>(AiProtocol.class);

    public AiProviderClientRegistry(List<AiProviderClient> clients) {
        for (AiProviderClient client : clients) {
            if (this.clients.put(client.protocol(), client) != null) {
                throw new IllegalStateException("duplicate AI protocol adapter: " + client.protocol());
            }
        }
    }

    public ProviderConnectionResult testConnection(AiModelEntity model, char[] secret) {
        AiProviderClient client = clients.get(model.getProtocol());
        if (client == null) throw new IllegalStateException("unsupported AI protocol");
        return client.testConnection(model, secret);
    }

    public AiExtractionProviderResult extract(AiModelEntity model, char[] secret,
            AiExtractionProviderRequest request) {
        AiProviderClient client = clients.get(model.getProtocol());
        if (client == null) throw new IllegalStateException("unsupported AI protocol");
        return client.extract(model, secret, request);
    }

    public AiStructuredGenerationResult generate(AiModelEntity model, char[] secret,
            AiStructuredGenerationRequest request) {
        AiProviderClient client = clients.get(model.getProtocol());
        if (client == null) throw new IllegalStateException("unsupported AI protocol");
        return client.generate(model, secret, request);
    }
}

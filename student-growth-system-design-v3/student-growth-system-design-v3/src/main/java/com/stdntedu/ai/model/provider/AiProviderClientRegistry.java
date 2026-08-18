package com.stdntedu.ai.model.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.stdntedu.ai.model.entity.AiModelEntity;
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
}

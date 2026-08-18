package com.stdntedu.ai.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.generated.model.AiProtocol;
import org.springframework.stereotype.Component;

@Component
public class OllamaProviderClient extends AbstractHttpProviderClient {
    public OllamaProviderClient(ObjectMapper objectMapper, ProviderErrorSanitizer errors) {
        super(objectMapper, errors);
    }

    @Override public AiProtocol protocol() { return AiProtocol.OLLAMA; }
    @Override protected String endpointPath() { return "api/tags"; }
    @Override protected boolean validResponse(JsonNode root) { return root.path("models").isArray(); }
    @Override protected boolean modelExists(JsonNode root, String modelName) {
        for (JsonNode model : root.path("models")) {
            if (modelName.equals(model.path("name").asText(null))) return true;
        }
        return false;
    }
}

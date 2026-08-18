package com.stdntedu.ai.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.generated.model.AiProtocol;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleProviderClient extends AbstractHttpProviderClient {
    public OpenAiCompatibleProviderClient(ObjectMapper objectMapper, ProviderErrorSanitizer errors) {
        super(objectMapper, errors);
    }

    @Override public AiProtocol protocol() { return AiProtocol.OPENAI_COMPATIBLE; }
    @Override protected String endpointPath() { return "models"; }
    @Override protected boolean validResponse(JsonNode root) { return root.path("data").isArray(); }
    @Override protected boolean modelExists(JsonNode root, String modelName) {
        for (JsonNode model : root.path("data")) {
            if (modelName.equals(model.path("id").asText(null))) return true;
        }
        return false;
    }
}

package com.stdntedu.ai.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderRequest;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResultParser;
import com.stdntedu.ai.extraction.provider.ProviderVisualInput;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.generated.model.AiProtocol;
import org.springframework.stereotype.Component;

@Component
public class OllamaProviderClient extends AbstractHttpProviderClient {
    public OllamaProviderClient(ObjectMapper objectMapper, ProviderErrorSanitizer errors,
            AiExtractionProviderResultParser extractionParser) {
        super(objectMapper, errors, extractionParser);
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

    @Override protected String extractionEndpointPath() { return "api/chat"; }

    @Override protected void writeExtractionRequest(java.io.OutputStream output, AiModelEntity model,
            AiExtractionProviderRequest request) throws java.io.IOException {
        writeUtf8(output, "{\"model\":" + jsonString(model.getModelName())
                + ",\"stream\":false,\"format\":\"json\"");
        if (model.getTemperature() != null || model.getMaxTokens() != null) {
            writeUtf8(output, ",\"options\":{");
            boolean comma = false;
            if (model.getTemperature() != null) {
                writeUtf8(output, "\"temperature\":" + model.getTemperature());
                comma = true;
            }
            if (model.getMaxTokens() != null) {
                writeUtf8(output, (comma ? "," : "") + "\"num_predict\":" + model.getMaxTokens());
            }
            writeUtf8(output, "}");
        }
        writeUtf8(output, ",\"messages\":[{\"role\":\"user\",\"content\":"
                + jsonString(request.prompt()) + ",\"images\":[");
        for (int i = 0; i < request.visuals().size(); i++) {
            if (i > 0) writeUtf8(output, ",");
            writeUtf8(output, "\"");
            writeBase64(output, request.visuals().get(i).path());
            writeUtf8(output, "\"");
        }
        writeUtf8(output, "]}]} ");
    }

    @Override protected String extractionContent(JsonNode root) {
        JsonNode content = root.path("message").path("content");
        return content.isTextual() ? content.asText() : null;
    }
}

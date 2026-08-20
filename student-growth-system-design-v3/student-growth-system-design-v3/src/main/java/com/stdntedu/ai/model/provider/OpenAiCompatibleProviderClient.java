package com.stdntedu.ai.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderRequest;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResultParser;
import com.stdntedu.ai.extraction.provider.ProviderVisualInput;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.generated.model.AiProtocol;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleProviderClient extends AbstractHttpProviderClient {
    public OpenAiCompatibleProviderClient(ObjectMapper objectMapper, ProviderErrorSanitizer errors,
            AiExtractionProviderResultParser extractionParser) {
        super(objectMapper, errors, extractionParser);
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

    @Override protected String extractionEndpointPath() { return "chat/completions"; }

    @Override protected void writeExtractionRequest(java.io.OutputStream output, AiModelEntity model,
            AiExtractionProviderRequest request) throws java.io.IOException {
        writeUtf8(output, "{\"model\":" + jsonString(model.getModelName()) + ",\"stream\":false");
        if (model.getTemperature() != null) writeUtf8(output, ",\"temperature\":" + model.getTemperature());
        if (model.getMaxTokens() != null) writeUtf8(output, ",\"max_tokens\":" + model.getMaxTokens());
        writeUtf8(output, ",\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":"
                + jsonString(request.prompt()) + "}");
        for (ProviderVisualInput image : request.visuals()) {
            writeUtf8(output, ",{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:"
                    + image.mimeType() + ";base64,");
            writeBase64(output, image.path());
            writeUtf8(output, "\"}}");
        }
        writeUtf8(output, "]}]}");
    }

    @Override protected String extractionContent(JsonNode root) {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isTextual() ? content.asText() : null;
    }

    @Override protected JsonNode generationRequest(AiModelEntity model, String prompt) {
        ObjectNode root = objectMapper().createObjectNode();
        root.put("model", model.getModelName());
        root.put("stream", false);
        if (model.getTemperature() != null) root.put("temperature", model.getTemperature());
        if (model.getMaxTokens() != null) root.put("max_tokens", model.getMaxTokens());
        root.putObject("response_format").put("type", "json_object");
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);
        return root;
    }

    @Override protected AiStructuredGenerationResult generationResult(JsonNode root) {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) throw invalidProviderResponse();
        JsonNode usage = root.path("usage");
        return new AiStructuredGenerationResult(content.asText(),
                nullableNonNegativeInteger(usage.path("prompt_tokens")),
                nullableNonNegativeInteger(usage.path("completion_tokens")));
    }
}

package com.stdntedu.ai.model.service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.ai.model.converter.AiModelConverter;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.mapper.AiModelMapper;
import com.stdntedu.ai.model.provider.AiProviderClientRegistry;
import com.stdntedu.ai.model.provider.ProviderConnectionResult;
import com.stdntedu.ai.model.service.AiSecretService.SecretView;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiAuthType;
import com.stdntedu.generated.model.AiModelConnectionTestDto;
import com.stdntedu.generated.model.AiModelCreateRequest;
import com.stdntedu.generated.model.AiModelDto;
import com.stdntedu.generated.model.AiModelStatusChangeRequest;
import com.stdntedu.generated.model.AiModelUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelService {
    private static final BigDecimal MIN_TEMPERATURE = BigDecimal.ZERO;
    private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("2");

    private final AiModelMapper models;
    private final AiSecretService secrets;
    private final AiModelConverter converter;
    private final AiProviderClientRegistry providers;
    private final IdConverter ids;

    public AiModelService(AiModelMapper models, AiSecretService secrets, AiModelConverter converter,
            AiProviderClientRegistry providers, IdConverter ids) {
        this.models = models;
        this.secrets = secrets;
        this.converter = converter;
        this.providers = providers;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public List<AiModelDto> list() {
        List<AiModelEntity> entities = models.selectList(Wrappers.<AiModelEntity>lambdaQuery()
                .orderByAsc(AiModelEntity::getPriorityNo).orderByAsc(AiModelEntity::getId));
        Map<String, SecretView> secretViews = secrets.views(entities.stream()
                .map(AiModelEntity::getApiKeyRef).toList());
        return entities.stream().map(entity -> converter.toDto(entity,
                entity.getApiKeyRef() == null ? new SecretView(false, null)
                        : secretViews.get(entity.getApiKeyRef()))).toList();
    }

    @Transactional(readOnly = true)
    public AiModelDto get(String modelId) {
        return toDto(require(ids.toLong(modelId)));
    }

    @Transactional
    public AiModelDto create(AiModelCreateRequest request) {
        Normalized normalized = validate(request.getName(), request.getModelName(), request.getBaseUrl(),
                request.getProvider(), request.getModelType(), request.getProtocol(), request.getAuthType(),
                request.getTemperature(), request.getMaxTokens(), request.getTimeoutSeconds(), request.getApiKey());
        if (request.getAuthType() == AiAuthType.BEARER_API_KEY && normalized.apiKey() == null) {
            throw invalid("API key is required for bearer authentication");
        }
        AiModelEntity entity = converter.fromCreate(request, normalized.name(), normalized.modelName(),
                normalized.baseUrl());
        if (normalized.apiKey() != null) entity.setApiKeyRef(secrets.create(normalized.apiKey()));
        models.insert(entity);
        return toDto(require(entity.getId()));
    }

    @Transactional
    public AiModelDto update(String modelId, AiModelUpdateRequest request) {
        Long id = ids.toLong(modelId);
        AiModelEntity entity = require(id);
        requireVersion(entity, request.getVersion());
        boolean clear = Boolean.TRUE.equals(request.getClearApiKey());
        if (clear && request.getApiKey() != null) {
            throw invalid("apiKey and clearApiKey cannot be submitted together");
        }
        Normalized normalized = validate(request.getName(), request.getModelName(), request.getBaseUrl(),
                request.getProvider(), request.getModelType(), request.getProtocol(), request.getAuthType(),
                request.getTemperature(), request.getMaxTokens(), request.getTimeoutSeconds(), request.getApiKey());

        String previousRef = entity.getApiKeyRef();
        String nextRef = previousRef;
        if (normalized.apiKey() != null) {
            if (previousRef == null) nextRef = secrets.create(normalized.apiKey());
            else secrets.replace(previousRef, normalized.apiKey());
        } else if (clear) {
            nextRef = null;
        }
        if (request.getAuthType() == AiAuthType.BEARER_API_KEY && nextRef == null) {
            throw invalid("API key is required for bearer authentication");
        }

        converter.applyUpdate(request, entity, normalized.name(), normalized.modelName(), normalized.baseUrl());
        entity.setApiKeyRef(nextRef);
        if (models.updateWithVersion(entity, request.getVersion()) == 0) throw versionConflict();
        if (clear && previousRef != null) secrets.delete(previousRef);
        return toDto(require(id));
    }

    @Transactional
    public AiModelDto enable(String modelId, AiModelStatusChangeRequest request) {
        return changeEnabled(modelId, request.getVersion(), true);
    }

    @Transactional
    public AiModelDto disable(String modelId, AiModelStatusChangeRequest request) {
        return changeEnabled(modelId, request.getVersion(), false);
    }

    public AiModelConnectionTestDto testConnection(String modelId) {
        AiModelEntity entity = require(ids.toLong(modelId));
        validateStored(entity);
        ProviderConnectionResult result;
        if (entity.getAuthType() == AiAuthType.BEARER_API_KEY) {
            if (entity.getApiKeyRef() == null) throw invalid("bearer model has no configured API key");
            result = secrets.withDecryptedSecret(entity.getApiKeyRef(),
                    secret -> providers.testConnection(entity, secret));
        } else {
            result = providers.testConnection(entity, null);
        }
        return new AiModelConnectionTestDto()
                .success(result.success())
                .provider(entity.getProvider())
                .modelName(entity.getModelName())
                .latencyMs((int) Math.min(Integer.MAX_VALUE, result.latencyMs()))
                .errorCode(result.errorCode())
                .message(result.message());
    }

    private AiModelDto changeEnabled(String modelId, Integer version, boolean target) {
        Long id = ids.toLong(modelId);
        AiModelEntity entity = require(id);
        requireVersion(entity, version);
        if (Boolean.valueOf(target).equals(entity.getEnabled())) {
            throw invalid(target ? "AI model is already enabled" : "AI model is already disabled");
        }
        if (target && entity.getAuthType() == AiAuthType.BEARER_API_KEY) {
            if (entity.getApiKeyRef() == null) throw invalid("bearer model has no configured API key");
            secrets.view(entity.getApiKeyRef());
        }
        if (models.changeEnabledWithVersion(id, version, !target, target) == 0) throw versionConflict();
        return toDto(require(id));
    }

    private Normalized validate(String name, String modelName, URI baseUrl, Object provider, Object modelType,
            Object protocol, AiAuthType authType, BigDecimal temperature, Integer maxTokens, Integer timeoutSeconds,
            String apiKey) {
        String cleanName = required(name, "name", 128);
        String cleanModelName = required(modelName, "modelName", 128);
        if (provider == null || modelType == null || protocol == null || authType == null) {
            throw invalid("provider, modelType, protocol and authType are required");
        }
        String cleanBaseUrl = validateBaseUrl(baseUrl);
        if (temperature != null && (temperature.compareTo(MIN_TEMPERATURE) < 0
                || temperature.compareTo(MAX_TEMPERATURE) > 0)) {
            throw invalid("temperature must be between 0 and 2");
        }
        if (maxTokens != null && maxTokens < 1) throw invalid("maxTokens must be positive");
        if (timeoutSeconds == null || timeoutSeconds < 1) throw invalid("timeoutSeconds must be positive");
        String cleanApiKey = apiKey;
        if (cleanApiKey != null && cleanApiKey.isBlank()) throw invalid("API key must not be blank");
        return new Normalized(cleanName, cleanModelName, cleanBaseUrl, cleanApiKey);
    }

    private String validateBaseUrl(URI baseUrl) {
        if (baseUrl == null || baseUrl.getScheme() == null || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw invalid("baseUrl must be a valid HTTP or HTTPS URI");
        }
        if (baseUrl.getUserInfo() != null) throw invalid("baseUrl must not contain user information");
        return baseUrl.toString();
    }

    private void validateStored(AiModelEntity entity) {
        try {
            validate(entity.getName(), entity.getModelName(), URI.create(entity.getApiBaseUrl()), entity.getProvider(),
                    entity.getModelType(), entity.getProtocol(), entity.getAuthType(), entity.getTemperature(),
                    entity.getMaxTokens(), entity.getTimeoutSeconds(), null);
        } catch (IllegalArgumentException ex) {
            throw invalid("saved AI model configuration is invalid");
        }
    }

    private AiModelDto toDto(AiModelEntity entity) {
        return converter.toDto(entity, secrets.view(entity.getApiKeyRef()));
    }

    private AiModelEntity require(Long id) {
        AiModelEntity entity = models.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("AI model not found");
        return entity;
    }

    private void requireVersion(AiModelEntity entity, Integer version) {
        if (version == null || !version.equals(entity.getVersion())) throw versionConflict();
    }

    private String required(String value, String field, int maxLength) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) throw invalid(field + " is required");
        if (clean.length() > maxLength) throw invalid(field + " exceeds " + maxLength + " characters");
        return clean;
    }

    private BusinessException invalid(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "AI model version conflict", HttpStatus.CONFLICT);
    }

    private record Normalized(String name, String modelName, String baseUrl, String apiKey) { }
}

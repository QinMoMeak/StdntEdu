package com.stdntedu.ai.model.converter;

import java.net.URI;
import java.time.ZoneId;

import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.service.AiSecretService.SecretView;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiModelCreateRequest;
import com.stdntedu.generated.model.AiModelDto;
import com.stdntedu.generated.model.AiModelUpdateRequest;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.stereotype.Component;

@Component
public class AiModelConverter {
    private final IdConverter ids;
    private final SystemTimezoneProvider timezones;

    public AiModelConverter(IdConverter ids, SystemTimezoneProvider timezones) {
        this.ids = ids;
        this.timezones = timezones;
    }

    public AiModelEntity fromCreate(AiModelCreateRequest request, String name, String modelName, String baseUrl) {
        AiModelEntity entity = new AiModelEntity();
        apply(request, entity, name, modelName, baseUrl);
        entity.setVersion(0);
        return entity;
    }

    public void applyUpdate(AiModelUpdateRequest request, AiModelEntity entity,
            String name, String modelName, String baseUrl) {
        entity.setName(name);
        entity.setProvider(request.getProvider());
        entity.setModelType(request.getModelType());
        entity.setModelName(modelName);
        entity.setProtocol(request.getProtocol());
        entity.setAuthType(request.getAuthType());
        entity.setApiBaseUrl(baseUrl);
        entity.setSupportsVision(request.getSupportsVision());
        entity.setSupportsJson(request.getSupportsJson());
        entity.setLocalFlag(request.getLocal());
        entity.setEnabled(request.getEnabled());
        entity.setPriorityNo(request.getPriority());
        entity.setTimeoutSeconds(request.getTimeoutSeconds());
        entity.setTemperature(request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setRemark(request.getRemark());
    }

    public AiModelDto toDto(AiModelEntity entity, SecretView secret) {
        ZoneId zone = timezones.get();
        return new AiModelDto()
                .id(ids.toString(entity.getId()))
                .name(entity.getName())
                .provider(entity.getProvider())
                .modelName(entity.getModelName())
                .modelType(entity.getModelType())
                .protocol(entity.getProtocol())
                .authType(entity.getAuthType())
                .baseUrl(URI.create(entity.getApiBaseUrl()))
                .apiKeyConfigured(secret.configured())
                .apiKeyMasked(secret.masked())
                .supportsVision(entity.getSupportsVision())
                .supportsJson(entity.getSupportsJson())
                .local(entity.getLocalFlag())
                .enabled(entity.getEnabled())
                .priority(entity.getPriorityNo())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .remark(entity.getRemark())
                .version(entity.getVersion())
                .createdAt(entity.getCreateTime().atZone(zone).toOffsetDateTime())
                .updatedAt(entity.getUpdateTime().atZone(zone).toOffsetDateTime());
    }

    private void apply(AiModelCreateRequest request, AiModelEntity entity,
            String name, String modelName, String baseUrl) {
        entity.setName(name);
        entity.setProvider(request.getProvider());
        entity.setModelType(request.getModelType());
        entity.setModelName(modelName);
        entity.setProtocol(request.getProtocol());
        entity.setAuthType(request.getAuthType());
        entity.setApiBaseUrl(baseUrl);
        entity.setSupportsVision(request.getSupportsVision());
        entity.setSupportsJson(request.getSupportsJson());
        entity.setLocalFlag(request.getLocal());
        entity.setEnabled(request.getEnabled());
        entity.setPriorityNo(request.getPriority());
        entity.setTimeoutSeconds(request.getTimeoutSeconds());
        entity.setTemperature(request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setRemark(request.getRemark());
    }
}

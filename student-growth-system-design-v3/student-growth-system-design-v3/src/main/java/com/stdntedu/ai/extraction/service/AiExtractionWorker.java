package com.stdntedu.ai.extraction.service;

import java.util.List;

import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import com.stdntedu.ai.extraction.mapper.AiExtractionTaskMapper;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderRequest;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResult;
import com.stdntedu.ai.extraction.provider.AiProviderException;
import com.stdntedu.ai.extraction.resource.NormalizedExtraction;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.ai.extraction.resource.PreparedExtraction;
import com.stdntedu.ai.extraction.resource.StoredMultipartFile;
import com.stdntedu.ai.extraction.resource.UploadPreflightService;
import com.stdntedu.ai.extraction.resource.VisualNormalizationService;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.mapper.AiModelMapper;
import com.stdntedu.ai.model.provider.AiProviderClientRegistry;
import com.stdntedu.ai.model.service.AiSecretService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.AiAuthType;
import com.stdntedu.generated.model.AiModelType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AiExtractionWorker {
    private final AiExtractionTaskMapper tasks;
    private final AiExtractionPersistenceService persistence;
    private final OriginalFileStorage storage;
    private final UploadPreflightService preflight;
    private final VisualNormalizationService normalization;
    private final AiModelMapper models;
    private final AiSecretService secrets;
    private final AiProviderClientRegistry providers;

    public AiExtractionWorker(AiExtractionTaskMapper tasks, AiExtractionPersistenceService persistence,
            OriginalFileStorage storage, UploadPreflightService preflight,
            VisualNormalizationService normalization, AiModelMapper models, AiSecretService secrets,
            AiProviderClientRegistry providers) {
        this.tasks = tasks;
        this.persistence = persistence;
        this.storage = storage;
        this.preflight = preflight;
        this.normalization = normalization;
        this.models = models;
        this.secrets = secrets;
        this.providers = providers;
    }

    public void execute(Long taskId) {
        if (tasks.start(taskId, "PENDING") != 1) return;
        try {
            AiExtractionTaskEntity task = persistence.requireTask(taskId);
            AiModelEntity model = requireModel(task.getModelId());
            List<MultipartFile> uploads = persistentUploads(taskId);
            try (PreparedExtraction prepared = preflight.prepare(uploads)) {
                NormalizedExtraction normalized = normalization.normalize(prepared);
                AiExtractionProviderRequest request = new AiExtractionProviderRequest(
                        AiExtractionService.EXTRACTION_PROMPT, normalized.visuals(), prepared.tempDirectory());
                AiExtractionProviderResult result = extract(model, request);
                persistence.saveProviderResult(taskId, result);
            }
        } catch (AiProviderException ex) {
            fail(taskId, ex.code(), ex.getMessage());
        } catch (BusinessException ex) {
            fail(taskId, ex.getCode(), safeMessage(ex.getMessage()));
        } catch (RuntimeException ex) {
            fail(taskId, "PROCESSING_FAILED", "extraction processing failed");
        }
    }

    private List<MultipartFile> persistentUploads(Long taskId) {
        List<StoredAttachmentView> stored = persistence.storedAttachments(taskId);
        if (stored.isEmpty()) throw missing();
        return stored.stream().map(item -> (MultipartFile) new StoredMultipartFile(item.fileName(), item.mimeType(),
                storage.requireStoredFile(item.storagePath()))).toList();
    }

    private AiExtractionProviderResult extract(AiModelEntity model, AiExtractionProviderRequest request) {
        return model.getAuthType() == AiAuthType.BEARER_API_KEY
                ? secrets.withDecryptedSecret(model.getApiKeyRef(), secret -> providers.extract(model, secret, request))
                : providers.extract(model, null, request);
    }

    private AiModelEntity requireModel(Long modelId) {
        AiModelEntity model = models.selectById(modelId);
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())
                || model.getModelType() != AiModelType.MULTIMODAL
                || (model.getAuthType() == AiAuthType.BEARER_API_KEY && model.getApiKeyRef() == null)) {
            throw new BusinessException("MODEL_UNAVAILABLE", "AI model is unavailable",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return model;
    }

    private void fail(Long taskId, String code, String message) {
        tasks.markFailed(taskId, code, truncate(message, 2000));
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "extraction processing failed" : message;
    }

    private String truncate(String value, int maximum) {
        return value == null || value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private BusinessException missing() {
        return new BusinessException("STORAGE_FILE_MISSING", "stored extraction attachment is unavailable",
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

package com.stdntedu.attachment.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import com.stdntedu.ai.extraction.mapper.AttachmentMapper;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.ai.extraction.resource.PreparedExtraction;
import com.stdntedu.ai.extraction.resource.StoredOriginal;
import com.stdntedu.ai.extraction.resource.UploadPreflightService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Attachment;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    private static final Set<String> PUBLIC_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final UploadPreflightService preflight;
    private final OriginalFileStorage storage;
    private final AttachmentMapper attachments;
    private final TransactionTemplate transactions;
    private final IdConverter ids;
    private final SystemTimezoneProvider time;

    public AttachmentService(UploadPreflightService preflight, OriginalFileStorage storage,
            AttachmentMapper attachments, TransactionTemplate transactions, IdConverter ids,
            SystemTimezoneProvider time) {
        this.preflight = preflight;
        this.storage = storage;
        this.attachments = attachments;
        this.transactions = transactions;
        this.ids = ids;
        this.time = time;
    }

    public Attachment upload(MultipartFile upload) {
        try (PreparedExtraction prepared = preflight.prepareAttachment(upload)) {
            List<StoredOriginal> stored = storage.persist(prepared);
            StoredOriginal file = stored.getFirst();
            AttachmentEntity entity = new AttachmentEntity();
            entity.setFileName(file.source().originalName());
            entity.setStorageType("LOCAL");
            entity.setStoragePath(file.storagePath().toString());
            entity.setMimeType(file.source().mediaType().mimeType());
            entity.setFileSize(file.source().size());
            entity.setSha256(file.source().sha256());
            entity.setDeleted(false);
            try {
                Integer inserted = transactions.execute(ignored -> attachments.insert(entity));
                if (inserted == null || inserted != 1) throw unavailable();
            } catch (RuntimeException ex) {
                storage.cleanup(stored);
                throw ex;
            }
            return dto(require(entity.getId()));
        }
    }

    public Download download(String attachmentId) {
        AttachmentEntity entity = require(ids.toLong(attachmentId));
        Path path = requireAvailableFile(entity);
        try {
            Resource content = new InputStreamResource(Files.newInputStream(path,
                    StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            return new Download(content, entity.getFileName(), entity.getMimeType(), entity.getFileSize());
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException business) throw business;
            throw unavailable();
        }
    }

    public void validateForAssociation(List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) return;
        Map<Long, AttachmentEntity> found = attachments.selectBatchIds(attachmentIds).stream()
                .collect(Collectors.toMap(AttachmentEntity::getId, Function.identity()));
        for (Long id : attachmentIds) {
            AttachmentEntity entity = found.get(id);
            if (entity == null) throw new ResourceNotFoundException("attachment not found");
            requireAvailableFile(entity);
        }
    }

    private AttachmentEntity require(Long id) {
        AttachmentEntity entity = attachments.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("attachment not found");
        return entity;
    }

    private Attachment dto(AttachmentEntity entity) {
        String id = ids.toString(entity.getId());
        return new Attachment(id, entity.getFileName(), entity.getMimeType(), entity.getFileSize(),
                entity.getSha256(), "/api/v1/attachments/" + id + "/content",
                time.toOffsetDateTime(entity.getCreateTime()));
    }

    private Path requireAvailableFile(AttachmentEntity entity) {
        if (!"LOCAL".equals(entity.getStorageType()) || !PUBLIC_MIME_TYPES.contains(entity.getMimeType())) {
            throw unavailable();
        }
        Path path = storage.requireStoredFile(Path.of(entity.getStoragePath()));
        try {
            if (Files.size(path) != entity.getFileSize()) throw unavailable();
            return path;
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException("STORAGE_FILE_MISSING", "stored attachment is unavailable",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public record Download(Resource content, String fileName, String mimeType, long size) { }
}

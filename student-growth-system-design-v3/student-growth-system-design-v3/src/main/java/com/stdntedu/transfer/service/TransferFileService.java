package com.stdntedu.transfer.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;

import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import com.stdntedu.ai.extraction.mapper.AttachmentMapper;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TransferFileService {
    public static final long REGULAR_MAX = 50L * 1024 * 1024;
    public static final long ZIP_MAX = 500L * 1024 * 1024;
    private static final Map<String, String> MIMES = Map.of(
            ".csv", "text/csv",
            ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ".json", "application/json",
            ".zip", "application/zip");

    private final OriginalFileStorage storage;
    private final AttachmentMapper attachments;

    public TransferFileService(OriginalFileStorage storage, AttachmentMapper attachments) {
        this.storage = storage;
        this.attachments = attachments;
    }

    public StoredAttachment storeUpload(MultipartFile upload) {
        String name = safeName(upload.getOriginalFilename());
        String suffix = suffix(name);
        String expectedMime = MIMES.get(suffix);
        if (expectedMime == null || !expectedMime.equals(normalizeMime(upload.getContentType()))) {
            throw new BusinessException("UNSUPPORTED_MEDIA_TYPE", "file type is not supported",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        long max = ".zip".equals(suffix) ? ZIP_MAX : REGULAR_MAX;
        if (upload.isEmpty()) throw invalid("file must not be empty");
        if (upload.getSize() > max) throw tooLarge();
        try (InputStream input = upload.getInputStream()) {
            return persist(input, name, expectedMime, suffix, max);
        } catch (IOException ex) {
            throw invalid("uploaded file could not be read");
        }
    }

    public StoredAttachment storeGenerated(Path source, String fileName, String mimeType) {
        try (InputStream input = Files.newInputStream(source, StandardOpenOption.READ)) {
            return persist(input, safeName(fileName), mimeType, suffix(fileName), Long.MAX_VALUE);
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    private StoredAttachment persist(InputStream input, String name, String mime, String suffix, long max) {
        OriginalFileStorage.ManagedFile file = storage.persist(input, suffix, max);
        AttachmentEntity entity = new AttachmentEntity();
        entity.setFileName(name);
        entity.setStorageType("LOCAL");
        entity.setStoragePath(file.path().toString());
        entity.setMimeType(mime);
        entity.setFileSize(file.size());
        entity.setSha256(file.sha256());
        entity.setDeleted(false);
        try {
            if (attachments.insert(entity) != 1) throw unavailable();
            return new StoredAttachment(entity, file.path());
        } catch (RuntimeException ex) {
            storage.cleanup(file.path());
            throw ex;
        }
    }

    public Path path(Long attachmentId) {
        AttachmentEntity entity = require(attachmentId);
        Path path = storage.requireStoredFile(Path.of(entity.getStoragePath()));
        try {
            if (Files.size(path) != entity.getFileSize()) throw unavailable();
            return path;
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    public Download download(Long attachmentId) {
        AttachmentEntity entity = require(attachmentId);
        Path path = path(attachmentId);
        try {
            return new Download(new InputStreamResource(Files.newInputStream(path, StandardOpenOption.READ)),
                    entity.getFileName(), entity.getMimeType(), entity.getFileSize());
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    public AttachmentEntity require(Long id) {
        AttachmentEntity entity = id == null ? null : attachments.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("attachment not found");
        return entity;
    }

    public void cleanup(StoredAttachment stored) {
        if (stored == null) return;
        attachments.deleteById(stored.entity().getId());
        storage.cleanup(stored.path());
    }

    public void cleanup(Long attachmentId) {
        if (attachmentId == null) return;
        AttachmentEntity entity = attachments.selectById(attachmentId);
        if (entity == null) return;
        attachments.deleteById(attachmentId);
        storage.cleanup(Path.of(entity.getStoragePath()));
    }

    private String safeName(String value) {
        String name = value == null ? "" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || name.length() > 255) throw invalid("file name is invalid");
        return name;
    }

    private String suffix(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) throw new BusinessException("UNSUPPORTED_MEDIA_TYPE", "file extension is required",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String value) {
        if (value == null) return "";
        int semicolon = value.indexOf(';');
        return (semicolon < 0 ? value : value.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException tooLarge() {
        return new BusinessException("PAYLOAD_TOO_LARGE", "file exceeds size limit", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    private BusinessException unavailable() {
        return new BusinessException("STORAGE_FILE_MISSING", "stored attachment is unavailable",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public record StoredAttachment(AttachmentEntity entity, Path path) { }
    public record Download(Resource content, String fileName, String mimeType, long size) { }
}

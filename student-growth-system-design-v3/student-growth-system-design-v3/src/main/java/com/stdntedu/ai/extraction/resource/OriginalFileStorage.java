package com.stdntedu.ai.extraction.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.stdntedu.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OriginalFileStorage {
    private static final Logger LOG = LoggerFactory.getLogger(OriginalFileStorage.class);
    private final Path root;

    public OriginalFileStorage(AttachmentStorageValidator validator) {
        this.root = validator.root();
    }

    public List<StoredOriginal> persist(PreparedExtraction extraction) {
        List<StoredOriginal> stored = new ArrayList<>();
        try {
            for (PreparedFile file : extraction.files()) {
                Path target = root.resolve(UUID.randomUUID().toString().replace("-", "") + extension(file.mediaType()))
                        .normalize();
                if (!target.startsWith(root)) throw new IOException("invalid storage target");
                StoredOriginal original = new StoredOriginal(file, target);
                stored.add(original);
                Files.copy(file.path(), target, StandardCopyOption.COPY_ATTRIBUTES);
            }
            return List.copyOf(stored);
        } catch (IOException | RuntimeException ex) {
            cleanup(stored);
            throw AiExtractionLimits.invalid("original upload could not be persisted");
        }
    }

    public void cleanup(List<StoredOriginal> files) {
        if (files == null) return;
        for (StoredOriginal file : files) {
            try {
                Files.deleteIfExists(file.storagePath());
            } catch (IOException ex) {
                LOG.warn("Extraction attachment compensation cleanup failed");
            }
        }
    }

    public Path requireStoredFile(Path candidate) {
        try {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) throw missing();
            Path real = normalized.toRealPath();
            if (!real.startsWith(root) || !Files.isRegularFile(real)) throw missing();
            return real;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException business) throw business;
            throw missing();
        }
    }

    public Path root() { return root; }

    public boolean isManagedPath(Path candidate) {
        return candidate != null && candidate.toAbsolutePath().normalize().startsWith(root);
    }

    private BusinessException missing() {
        return new BusinessException("STORAGE_FILE_MISSING", "stored extraction attachment is unavailable",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String extension(DetectedMediaType type) {
        return switch (type) {
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case WEBP -> ".webp";
            case PDF -> ".pdf";
        };
    }
}

package com.stdntedu.ai.extraction.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OriginalFileStorage {
    private final Path root;

    public OriginalFileStorage(@Value("${app.ai.extraction.storage-root:#{systemProperties['java.io.tmpdir'] + '/stdntedu-ai-extraction/attachments'}}")
            String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public List<StoredOriginal> persist(PreparedExtraction extraction) {
        List<StoredOriginal> stored = new ArrayList<>();
        try {
            Files.createDirectories(root);
            for (PreparedFile file : extraction.files()) {
                Path target = root.resolve(UUID.randomUUID().toString().replace("-", "") + extension(file.mediaType()))
                        .normalize();
                if (!target.startsWith(root)) throw new IOException("invalid storage target");
                Files.copy(file.path(), target, StandardCopyOption.COPY_ATTRIBUTES);
                stored.add(new StoredOriginal(file, target));
            }
            return List.copyOf(stored);
        } catch (IOException ex) {
            cleanup(stored);
            throw AiExtractionLimits.invalid("original upload could not be persisted");
        }
    }

    public void cleanup(List<StoredOriginal> files) {
        if (files == null) return;
        for (StoredOriginal file : files) {
            try { Files.deleteIfExists(file.storagePath()); } catch (IOException ignored) { }
        }
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

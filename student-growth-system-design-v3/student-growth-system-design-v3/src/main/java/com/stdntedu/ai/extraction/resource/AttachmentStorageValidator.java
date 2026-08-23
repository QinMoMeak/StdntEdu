package com.stdntedu.ai.extraction.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

@Component
public class AttachmentStorageValidator {
    private final Path root;

    public AttachmentStorageValidator(AttachmentStorageProperties properties) {
        this.root = validate(properties.getStorageRoot(), Files::isWritable);
    }

    public Path root() { return root; }

    static Path validate(Path configured, Predicate<Path> writable) {
        if (configured == null) throw unavailable();
        try {
            Path normalized = configured.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            Path realRoot = normalized.toRealPath();
            Path tempRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize().toRealPath();
            if (realRoot.equals(tempRoot) || realRoot.startsWith(tempRoot)
                    || !Files.isDirectory(realRoot) || !writable.test(realRoot)) {
                throw unavailable();
            }
            return realRoot;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalStateException state) throw state;
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("attachment storage is unavailable");
    }
}

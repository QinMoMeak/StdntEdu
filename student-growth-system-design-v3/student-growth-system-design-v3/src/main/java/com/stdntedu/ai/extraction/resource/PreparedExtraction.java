package com.stdntedu.ai.extraction.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import com.stdntedu.generated.model.AiInputType;

public record PreparedExtraction(Path tempDirectory, List<PreparedFile> files, int visualUnits,
        AiInputType inputType) implements AutoCloseable {
    @Override public void close() { deleteRecursively(tempDirectory); }

    public static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}

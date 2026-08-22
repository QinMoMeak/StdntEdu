package com.stdntedu.ai.extraction.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.AiInputType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AttachmentStorageTest {
    private final Path root = Path.of("target", "attachment-storage-test-" + UUID.randomUUID()).toAbsolutePath();

    @AfterEach
    void cleanup() throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void defaultStorageRootIsPersistentAndNotSystemTemporaryDirectory() {
        Path configured = new AttachmentStorageProperties().getStorageRoot().toAbsolutePath().normalize();
        Path temporary = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

        assertThat(configured.startsWith(temporary)).isFalse();
    }

    @Test
    void validatorCreatesAUsableRoot() {
        Path validated = AttachmentStorageValidator.validate(root, Files::isWritable);

        assertThat(validated).isDirectory();
        assertThat(Files.isWritable(validated)).isTrue();
    }

    @Test
    void validatorRejectsARegularFileAndAnUnwritableDirectory() throws Exception {
        Files.createDirectories(root.getParent());
        Files.writeString(root, "not a directory");
        assertThatThrownBy(() -> AttachmentStorageValidator.validate(root, Files::isWritable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI extraction attachment storage is unavailable");

        Files.delete(root);
        Files.createDirectories(root);
        assertThatThrownBy(() -> AttachmentStorageValidator.validate(root, ignored -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI extraction attachment storage is unavailable");
    }

    @Test
    void originalFileNamesCannotControlPhysicalStoragePath() throws Exception {
        Files.createDirectories(root);
        Path source = Files.createTempFile("storage-source-", ".bin");
        Files.write(source, new byte[] {1, 2, 3});
        try {
            OriginalFileStorage storage = storage();
            List<String> names = List.of("../../evil", "..\\..\\evil", "C:\\evil", "/var/evil",
                    "../mixed\\evil");
            List<PreparedFile> files = java.util.stream.IntStream.range(0, names.size())
                    .mapToObj(index -> prepared(index, names.get(index), source)).toList();

            List<StoredOriginal> stored = storage.persist(new PreparedExtraction(null, files, files.size(),
                    AiInputType.IMAGE));

            assertThat(stored).allSatisfy(item -> {
                assertThat(item.storagePath()).startsWith(storage.root());
                assertThat(item.storagePath().getFileName().toString())
                        .matches("[0-9a-f]{32}\\.png");
            });
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void partialMultiFileFailureCompensatesAlreadyStoredFiles() throws Exception {
        Files.createDirectories(root);
        Path source = Files.createTempFile("storage-source-", ".bin");
        Path missing = source.resolveSibling("missing-" + UUID.randomUUID());
        Files.write(source, new byte[] {1, 2, 3});
        try {
            PreparedExtraction extraction = new PreparedExtraction(null, List.of(
                    prepared(0, "first.png", source), prepared(1, "second.png", missing)), 2,
                    AiInputType.IMAGE);

            assertThatThrownBy(() -> storage().persist(extraction)).isInstanceOf(BusinessException.class);
            try (var paths = Files.list(root)) {
                assertThat(paths).isEmpty();
            }
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private OriginalFileStorage storage() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setStorageRoot(root);
        return new OriginalFileStorage(new AttachmentStorageValidator(properties));
    }

    private PreparedFile prepared(int order, String name, Path source) {
        return new PreparedFile(order, name, source, DetectedMediaType.PNG, 3, "sha", 1, 1, List.of());
    }
}

package com.stdntedu.backup.packageformat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.ai.model.security.AiSecretCryptoService;
import com.stdntedu.backup.entity.BackupRecordEntity;
import com.stdntedu.backup.packageformat.BackupManifest.Entry;
import com.stdntedu.common.file.ZipArchiveSafety;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BackupArchiveService {
    public static final String FORMAT = "STDNTEDU_BACKUP_V1";
    public static final int SCHEMA_VERSION = 1;
    public static final String OPENAPI_VERSION = "3.14.0";
    public static final String COMPRESSION = "ZIP_DEFLATE";
    private static final String MANIFEST = "backup-manifest.json";
    private static final long MAX_ARCHIVE_BYTES = 10L * 1024 * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 500L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private static final double MAX_RATIO = 100.0;

    private final LogicalBackupDataService data;
    private final OriginalFileStorage storage;
    private final AiSecretCryptoService secrets;
    private final SystemTimezoneProvider time;
    private final ObjectMapper json;
    private final String applicationVersion;

    public BackupArchiveService(LogicalBackupDataService data, OriginalFileStorage storage,
            AiSecretCryptoService secrets, SystemTimezoneProvider time, ObjectMapper json,
            @Value("${app.version:unknown}") String applicationVersion) {
        this.data = data;
        this.storage = storage;
        this.secrets = secrets;
        this.time = time;
        this.json = json.copy().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.applicationVersion = applicationVersion;
    }

    public Artifact create(long id, boolean includeAttachments, String secretMode) {
        Path temp = tempDirectory("stdntedu-backup-");
        try {
            var snapshot = data.createSnapshot(temp.resolve("data"), includeAttachments,
                    "INCLUDE_ENCRYPTED".equals(secretMode));
            List<LocalEntry> datasets = snapshot.datasets().stream()
                    .map(value -> new LocalEntry(value.entry(), value.path())).toList();
            List<LocalEntry> attachments = copyAttachments(temp.resolve("attachments"), snapshot.attachments());
            long totalBytes = java.util.stream.Stream.concat(datasets.stream(), attachments.stream())
                    .mapToLong(value -> value.entry().size()).sum();
            BackupManifest manifest = new BackupManifest(FORMAT, SCHEMA_VERSION, applicationVersion,
                    OPENAPI_VERSION, snapshot.databaseVersion(), time.offsetDateTime(), time.get().getId(),
                    datasets.size(), snapshot.recordCount(), attachments.size(), totalBytes, COMPRESSION, false,
                    secretMode, "INCLUDE_ENCRYPTED".equals(secretMode) ? secrets.masterKeyFingerprint() : null,
                    "ZIP_SHA256_STORED_IN_BACKUP_RECORD", datasets.stream().map(LocalEntry::entry).toList(),
                    attachments.stream().map(LocalEntry::entry).toList());
            Path manifestFile = temp.resolve(MANIFEST);
            json.writeValue(manifestFile.toFile(), manifest);
            Path zip = temp.resolve("backup.zip");
            writeZip(zip, manifestFile, datasets, attachments);
            OriginalFileStorage.ManagedFile stored;
            try (InputStream input = Files.newInputStream(zip)) {
                stored = storage.persist(input, ".zip", MAX_ARCHIVE_BYTES);
            }
            String fileName = "stdntedu-backup-" + time.localDateTime().toString().replaceAll("[^0-9]", "")
                    + "-" + id + ".zip";
            return new Artifact(stored.path(), fileName, stored.size(), stored.sha256(), manifest,
                    json.writeValueAsString(manifest));
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("backup package generation failed", ex);
        } finally {
            deleteTree(temp);
        }
    }

    private List<LocalEntry> copyAttachments(Path directory,
            List<LogicalBackupDataService.AttachmentSource> sources) throws IOException {
        Files.createDirectories(directory);
        List<LocalEntry> result = new ArrayList<>();
        for (var source : sources) {
            Path original = storage.requireStoredFile(source.path());
            String entryName = "attachments/" + source.id() + "-" + source.sha256() + ".bin";
            Path copy = directory.resolve(source.id() + ".bin");
            Files.copy(original, copy, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(copy);
            String checksum = LogicalBackupDataService.sha256(copy);
            if (size != source.size() || !checksum.equalsIgnoreCase(source.sha256())) {
                throw new IllegalStateException("attachment changed while backup was created");
            }
            result.add(new LocalEntry(new Entry(entryName, "attachment", source.id(), 1, size, checksum), copy));
        }
        return List.copyOf(result);
    }

    private void writeZip(Path target, Path manifest, List<LocalEntry> datasets,
            List<LocalEntry> attachments) throws IOException {
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(target)) {
            output.setMethod(ZipArchiveOutputStream.DEFLATED);
            put(output, MANIFEST, manifest);
            for (LocalEntry entry : datasets) put(output, entry.entry().path(), entry.path());
            for (LocalEntry entry : attachments) put(output, entry.entry().path(), entry.path());
        }
    }

    private void put(ZipArchiveOutputStream output, String name, Path source) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setTime(0);
        output.putArchiveEntry(entry);
        Files.copy(source, output);
        output.closeArchiveEntry();
    }

    public Verification verify(BackupRecordEntity record) {
        if (!"SUCCESS".equals(record.getStatus()) || record.getStoragePath() == null) {
            throw new IllegalStateException("backup artifact is not ready");
        }
        Path archive = storage.requireStoredFile(Path.of(record.getStoragePath()));
        boolean outer = size(archive) == record.getFileSize()
                && LogicalBackupDataService.sha256(archive).equalsIgnoreCase(record.getChecksum());
        if (!outer) throw new IllegalStateException("backup ZIP checksum does not match");
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            Map<String, ZipArchiveEntry> entries = safeEntries(zip);
            ZipArchiveEntry manifestEntry = entries.get(MANIFEST);
            if (manifestEntry == null) throw new IllegalStateException("backup manifest is missing");
            BackupManifest manifest;
            try (InputStream input = zip.getInputStream(manifestEntry)) {
                byte[] bytes = input.readNBytes(1024 * 1024 + 1);
                if (bytes.length > 1024 * 1024) throw new IllegalStateException("backup manifest exceeds size limit");
                manifest = json.readValue(bytes, BackupManifest.class);
            }
            validateManifest(manifest, entries);
            long total = 0;
            for (Entry expected : concat(manifest.datasets(), manifest.attachments())) {
                ZipArchiveEntry actual = entries.get(expected.path());
                Digest digest = digest(zip.getInputStream(actual), MAX_ENTRY_BYTES);
                total += digest.size();
                if (digest.size() != expected.size() || !digest.sha256().equalsIgnoreCase(expected.sha256())) {
                    throw new IllegalStateException("backup payload checksum does not match");
                }
            }
            if (total != manifest.totalBytes()) throw new IllegalStateException("backup total size does not match");
            return new Verification(manifest, archive, true, List.of());
        } catch (IOException ex) {
            throw new IllegalStateException("backup ZIP could not be verified", ex);
        }
    }

    private Map<String, ZipArchiveEntry> safeEntries(ZipFile zip) {
        Map<String, ZipArchiveEntry> result = new LinkedHashMap<>();
        long total = 0;
        var values = zip.getEntries();
        while (values.hasMoreElements()) {
            ZipArchiveEntry entry = values.nextElement();
            if (entry.isDirectory()) continue;
            if (result.size() >= MAX_ENTRIES) throw new IllegalStateException("backup ZIP exceeds entry limit");
            String name;
            try { name = ZipArchiveSafety.safeEntryName(entry, MAX_ENTRY_BYTES, MAX_RATIO); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException(ex.getMessage()); }
            if (result.put(name, entry) != null) throw new IllegalStateException("backup ZIP has duplicate entries");
            if (entry.getSize() > 0) total += entry.getSize();
            if (total > MAX_EXPANDED_BYTES) throw new IllegalStateException("backup ZIP exceeds expanded size limit");
        }
        return result;
    }

    private void validateManifest(BackupManifest manifest, Map<String, ZipArchiveEntry> entries) {
        if (!FORMAT.equals(manifest.format()) || manifest.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalStateException("backup format is not supported");
        }
        int databaseVersion;
        try { databaseVersion = Integer.parseInt(manifest.databaseVersion()); }
        catch (RuntimeException ex) { throw new IllegalStateException("backup database version is invalid"); }
        if (databaseVersion > Integer.parseInt(data.currentDatabaseVersion())) {
            throw new IllegalStateException("backup database version is newer than supported");
        }
        if (!COMPRESSION.equals(manifest.compression()) || manifest.encryption()) {
            throw new IllegalStateException("backup compression or encryption is not supported");
        }
        if (!Set.of("EXCLUDE", "INCLUDE_ENCRYPTED").contains(manifest.secretMode())) {
            throw new IllegalStateException("backup secret mode is invalid");
        }
        if (manifest.datasetCount() != manifest.datasets().size()
                || manifest.attachmentCount() != manifest.attachments().size()) {
            throw new IllegalStateException("backup manifest counts do not match");
        }
        Set<String> declared = new HashSet<>();
        declared.add(MANIFEST);
        for (Entry entry : concat(manifest.datasets(), manifest.attachments())) {
            if (!declared.add(entry.path()) || !entries.containsKey(entry.path())) {
                throw new IllegalStateException("backup manifest entry set is invalid");
            }
        }
        if (!declared.equals(entries.keySet())) throw new IllegalStateException("backup ZIP has undeclared entries");
    }

    public StagedPackage stage(BackupRecordEntity record) {
        Verification verification = verify(record);
        Path root = tempDirectory("stdntedu-restore-");
        try (ZipFile zip = ZipFile.builder().setPath(verification.archive()).get()) {
            Map<String, ZipArchiveEntry> entries = safeEntries(zip);
            for (Entry expected : concat(verification.manifest().datasets(), verification.manifest().attachments())) {
                Path target = root.resolve(expected.path()).normalize();
                if (!target.startsWith(root)) throw new IllegalStateException("backup entry path is unsafe");
                Files.createDirectories(target.getParent());
                MessageDigest digest = sha256Digest();
                long size;
                try (InputStream input = zip.getInputStream(entries.get(expected.path()));
                        OutputStream output = Files.newOutputStream(target)) {
                    size = ZipArchiveSafety.copyBounded(input, output, MAX_ENTRY_BYTES, digest);
                }
                if (size != expected.size() || !HexFormat.of().formatHex(digest.digest()).equals(expected.sha256())) {
                    throw new IllegalStateException("staged backup payload checksum does not match");
                }
            }
            return new StagedPackage(root, verification.manifest());
        } catch (Exception ex) {
            deleteTree(root);
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("backup could not be staged", ex);
        }
    }

    private List<Entry> concat(List<Entry> left, List<Entry> right) {
        List<Entry> all = new ArrayList<>(left.size() + right.size());
        all.addAll(left);
        all.addAll(right);
        return all;
    }

    private Digest digest(InputStream input, long max) throws IOException {
        MessageDigest digest = sha256Digest();
        try (input; OutputStream output = OutputStream.nullOutputStream()) {
            long size = ZipArchiveSafety.copyBounded(input, output, max, digest);
            return new Digest(size, HexFormat.of().formatHex(digest.digest()));
        }
    }

    private MessageDigest sha256Digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private long size(Path path) {
        try { return Files.size(path); }
        catch (IOException ex) { throw new IllegalStateException("backup artifact is unavailable", ex); }
    }

    private Path tempDirectory(String prefix) {
        try { return Files.createTempDirectory(prefix); }
        catch (IOException ex) { throw new IllegalStateException("temporary storage is unavailable", ex); }
    }

    public void deleteArtifact(BackupRecordEntity record) {
        if (record.getStoragePath() == null) return;
        Path path = storage.requireStoredFile(Path.of(record.getStoragePath()));
        try { Files.delete(path); }
        catch (IOException ex) { throw new IllegalStateException("backup artifact could not be deleted", ex); }
    }

    public void cleanup(Path path) { storage.cleanup(path); }

    static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private record LocalEntry(Entry entry, Path path) { }
    private record Digest(long size, String sha256) { }
    public record Artifact(Path path, String fileName, long size, String sha256, BackupManifest manifest,
            String manifestJson) { }
    public record Verification(BackupManifest manifest, Path archive, boolean valid, List<String> warnings) { }
    public record StagedPackage(Path root, BackupManifest manifest) implements AutoCloseable {
        @Override public void close() { deleteTree(root); }
    }
}

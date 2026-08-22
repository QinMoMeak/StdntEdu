package com.stdntedu.ai.extraction.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import com.stdntedu.ai.extraction.mapper.AttachmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AttachmentStorageReconciliationService {
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentStorageReconciliationService.class);
    private static final Pattern SERVER_FILE = Pattern.compile("[0-9a-f]{32}\\.(jpg|png|webp|pdf)");

    private final AttachmentMapper attachments;
    private final OriginalFileStorage storage;

    public AttachmentStorageReconciliationService(AttachmentMapper attachments, OriginalFileStorage storage) {
        this.attachments = attachments;
        this.storage = storage;
    }

    public AttachmentReconciliationReport reconcile() {
        List<Long> missingIds = new ArrayList<>();
        Set<Path> referenced = new HashSet<>();
        for (AttachmentEntity attachment : attachments.selectExtractionAttachments()) {
            try {
                Path path = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
                if (!storage.isManagedPath(path)) {
                    missingIds.add(attachment.getId());
                    continue;
                }
                referenced.add(path);
                storage.requireStoredFile(path);
            } catch (RuntimeException ex) {
                missingIds.add(attachment.getId());
            }
        }

        int orphanCount = 0;
        try (Stream<Path> paths = Files.list(storage.root())) {
            orphanCount = Math.toIntExact(paths
                    .filter(Files::isRegularFile)
                    .filter(path -> SERVER_FILE.matcher(path.getFileName().toString()).matches())
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> !referenced.contains(path))
                    .count());
        } catch (IOException ex) {
            throw new IllegalStateException("AI extraction attachment reconciliation failed");
        }

        AttachmentReconciliationReport report = new AttachmentReconciliationReport(
                missingIds.size(), orphanCount, List.copyOf(missingIds));
        if (report.missingCount() > 0 || report.orphanCount() > 0) {
            LOG.warn("AI extraction attachment reconciliation found missingCount={}, orphanCount={}, missingAttachmentIds={}",
                    report.missingCount(), report.orphanCount(), report.missingAttachmentIds());
        } else {
            LOG.info("AI extraction attachment reconciliation completed without inconsistencies");
        }
        return report;
    }
}

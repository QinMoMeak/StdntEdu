package com.stdntedu.backup.packageformat;

import java.time.OffsetDateTime;
import java.util.List;

public record BackupManifest(
        String format,
        int schemaVersion,
        String applicationVersion,
        String openapiVersion,
        String databaseVersion,
        OffsetDateTime createdAt,
        String timezone,
        int datasetCount,
        long recordCount,
        int attachmentCount,
        long totalBytes,
        String compression,
        boolean encryption,
        String secretMode,
        String masterKeyFingerprint,
        String manifestChecksumStrategy,
        List<Entry> datasets,
        List<Entry> attachments) {

    public record Entry(String path, String table, Long attachmentId, long recordCount, long size, String sha256) { }
}

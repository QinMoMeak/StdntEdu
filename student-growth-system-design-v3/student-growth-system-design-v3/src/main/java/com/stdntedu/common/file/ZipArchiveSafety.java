package com.stdntedu.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

public final class ZipArchiveSafety {
    private ZipArchiveSafety() { }

    public static String safeEntryName(ZipArchiveEntry entry, long maxBytes, double maxRatio) {
        String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
        Path normalized;
        try { normalized = Path.of(name).normalize(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("ZIP entry path is unsafe"); }
        if (name.isBlank() || name.startsWith("/") || name.startsWith("//") || name.matches("^[A-Za-z]:.*")
                || normalized.isAbsolute() || Arrays.asList(name.split("/")).contains("..")
                || normalized.startsWith("..") || entry.isUnixSymlink()) {
            throw new IllegalArgumentException("ZIP entry path is unsafe");
        }
        long size = entry.getSize();
        long compressed = entry.getCompressedSize();
        if (size > maxBytes) throw new IllegalArgumentException("ZIP entry exceeds size limit");
        if (size > 0 && compressed > 0 && (double) size / compressed > maxRatio) {
            throw new IllegalArgumentException("ZIP compression ratio exceeds limit");
        }
        return normalized.toString().replace('\\', '/');
    }

    public static long copyBounded(InputStream input, java.io.OutputStream output, long maxBytes,
            java.security.MessageDigest digest) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long size = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            if (read == 0) continue;
            size += read;
            if (size > maxBytes) throw new IOException("ZIP entry exceeds size limit");
            if (digest != null) digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        return size;
    }
}

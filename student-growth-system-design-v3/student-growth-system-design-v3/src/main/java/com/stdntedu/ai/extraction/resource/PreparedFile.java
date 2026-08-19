package com.stdntedu.ai.extraction.resource;

import java.nio.file.Path;
import java.util.List;

public record PreparedFile(int sortOrder, String originalName, Path path, DetectedMediaType mediaType,
        long size, String sha256, Integer imageWidth, Integer imageHeight, List<RasterSize> pdfPages) {
    public int visualUnits() { return mediaType.pdf() ? pdfPages.size() : 1; }
}

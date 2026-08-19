package com.stdntedu.ai.extraction.provider;

import java.nio.file.Path;

public record ProviderVisualInput(Path path, String mimeType, int sourceOrder, Integer pageNumber, long size) { }

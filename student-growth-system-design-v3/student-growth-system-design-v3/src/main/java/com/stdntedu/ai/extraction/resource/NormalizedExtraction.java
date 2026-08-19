package com.stdntedu.ai.extraction.resource;

import java.util.List;

import com.stdntedu.ai.extraction.provider.ProviderVisualInput;

public record NormalizedExtraction(List<ProviderVisualInput> visuals, long totalBinaryBytes) { }

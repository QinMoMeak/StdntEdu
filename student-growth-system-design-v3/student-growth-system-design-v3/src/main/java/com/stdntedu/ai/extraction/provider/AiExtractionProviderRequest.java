package com.stdntedu.ai.extraction.provider;

import java.nio.file.Path;
import java.util.List;

public record AiExtractionProviderRequest(String prompt, List<ProviderVisualInput> visuals,
        Path workingDirectory) { }

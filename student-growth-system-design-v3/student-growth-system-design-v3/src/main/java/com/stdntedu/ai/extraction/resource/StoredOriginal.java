package com.stdntedu.ai.extraction.resource;

import java.nio.file.Path;

public record StoredOriginal(PreparedFile source, Path storagePath) { }

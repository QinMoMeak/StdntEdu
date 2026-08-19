package com.stdntedu.ai.extraction.service;

import java.nio.file.Path;

public record StoredAttachmentView(int sortOrder, String fileName, String mimeType, Path storagePath) { }

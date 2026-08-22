package com.stdntedu.ai.extraction.resource;

import java.util.List;

public record AttachmentReconciliationReport(int missingCount, int orphanCount,
        List<Long> missingAttachmentIds) {
}

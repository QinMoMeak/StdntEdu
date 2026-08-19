package com.stdntedu.ai.extraction.resource;

public enum DetectedMediaType {
    JPEG("image/jpeg", false),
    PNG("image/png", false),
    WEBP("image/webp", false),
    PDF("application/pdf", true);

    private final String mimeType;
    private final boolean pdf;

    DetectedMediaType(String mimeType, boolean pdf) {
        this.mimeType = mimeType;
        this.pdf = pdf;
    }

    public String mimeType() { return mimeType; }
    public boolean pdf() { return pdf; }
}

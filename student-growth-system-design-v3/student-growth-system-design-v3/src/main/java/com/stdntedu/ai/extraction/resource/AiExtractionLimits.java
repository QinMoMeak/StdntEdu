package com.stdntedu.ai.extraction.resource;

import com.stdntedu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class AiExtractionLimits {
    public static final int MAX_FILES = 20;
    public static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024;
    public static final long MAX_PDF_BYTES = 50L * 1024 * 1024;
    public static final long MAX_RAW_BYTES = 268_435_456L;
    public static final int MAX_VISUAL_UNITS = 20;
    public static final long MAX_WIDTH = 8192L;
    public static final long MAX_HEIGHT = 8192L;
    public static final long MAX_PIXELS = 16_000_000L;
    public static final long BYTES_PER_DECODED_PIXEL = 8L;
    public static final long MAX_RASTER_WORKING_SET = 128L * 1024 * 1024;
    public static final long MAX_NORMALIZED_BYTES = 134_217_728L;

    private AiExtractionLimits() { }

    public static long addRawBytes(long current, long next) {
        return addWithin(current, next, MAX_RAW_BYTES, "raw upload exceeds 256 MiB");
    }

    public static long addNormalizedBytes(long current, long next) {
        return addWithin(current, next, MAX_NORMALIZED_BYTES,
                "normalized provider payload exceeds 128 MiB");
    }

    public static int addVisualUnits(int current, int next) {
        long total = (long) current + next;
        if (next < 0 || total > MAX_VISUAL_UNITS) throw tooLarge("visual units exceed 20");
        return (int) total;
    }

    public static void validateDimensions(long width, long height) {
        if (width <= 0 || height <= 0) throw invalid("image dimensions must be positive");
        if (width > MAX_WIDTH || height > MAX_HEIGHT) throw tooLarge("visual dimensions exceed 8192");
        long pixels = multiply(width, height, "visual pixel count overflow");
        if (pixels > MAX_PIXELS) throw tooLarge("visual pixel count exceeds 16000000");
        long workingSet = multiply(pixels, BYTES_PER_DECODED_PIXEL, "raster working set overflow");
        if (workingSet > MAX_RASTER_WORKING_SET) throw tooLarge("raster working set exceeds 128 MiB");
    }

    public static long pdfRasterEdge(double points) {
        double pixels = Math.ceil(points / 72.0d * 150.0d);
        if (!Double.isFinite(points) || !Double.isFinite(pixels) || points <= 0 || pixels <= 0
                || pixels > Long.MAX_VALUE) {
            throw invalid("PDF page dimensions are invalid");
        }
        return (long) pixels;
    }

    public static BusinessException tooLarge(String message) {
        return new BusinessException("PAYLOAD_TOO_LARGE", message, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    public static BusinessException invalid(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static BusinessException unsupported(String message) {
        return new BusinessException("UNSUPPORTED_MEDIA_TYPE", message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private static long addWithin(long current, long next, long max, String message) {
        if (current < 0 || next < 0 || current > max - next) throw tooLarge(message);
        return current + next;
    }

    private static long multiply(long left, long right, String message) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ex) {
            throw tooLarge(message);
        }
    }
}

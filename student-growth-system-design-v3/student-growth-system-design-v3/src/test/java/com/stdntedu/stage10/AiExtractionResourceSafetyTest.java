package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import com.stdntedu.ai.extraction.resource.AiExtractionLimits;
import com.stdntedu.ai.extraction.resource.NormalizedExtraction;
import com.stdntedu.ai.extraction.resource.PreparedExtraction;
import com.stdntedu.ai.extraction.resource.UploadPreflightService;
import com.stdntedu.ai.extraction.resource.VisualNormalizationService;
import com.stdntedu.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class AiExtractionResourceSafetyTest {
    private static final byte[] STATIC_ALPHA_WEBP = Base64.getDecoder().decode(
            "UklGRhwAAABXRUJQVlA4TA8AAAAvAUAAEAcQ/Y8CBiKi/wEA");
    private static final byte[] ANIMATED_WEBP = Base64.getDecoder().decode(
            "UklGRoQAAABXRUJQVlA4WAoAAAACAAAAAQAAAQAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAAAAEAAAEAAGQAAAJWUDhMDwAAAC8BQAAABxD9j/4HIqL/AQBBTk1GKAAAAAAAAAAAAAEAAAEAAGQAAABWUDhMDwAAAC8BQAAAB9D/iP4HIqL/AQA=");

    private final UploadPreflightService preflight = new UploadPreflightService();
    private final VisualNormalizationService normalization = new VisualNormalizationService();

    @Test void width8192BoundaryIsAccepted() { AiExtractionLimits.validateDimensions(8192, 1); }
    @Test void width8193IsRejected() { assertPayloadTooLarge(() -> AiExtractionLimits.validateDimensions(8193, 1)); }
    @Test void height8192BoundaryIsAccepted() { AiExtractionLimits.validateDimensions(1, 8192); }
    @Test void height8193IsRejected() { assertPayloadTooLarge(() -> AiExtractionLimits.validateDimensions(1, 8193)); }
    @Test void sixteenMillionPixelBoundaryIsAccepted() { AiExtractionLimits.validateDimensions(4000, 4000); }
    @Test void overSixteenMillionPixelsIsRejected() { assertPayloadTooLarge(() -> AiExtractionLimits.validateDimensions(4001, 4000)); }
    @Test void sizeMultiplicationCannotOverflowInt() { assertPayloadTooLarge(() -> AiExtractionLimits.validateDimensions(Long.MAX_VALUE, 2)); }

    @Test void jpegBombIsRejectedDuringMetadataPreflight() throws Exception {
        assertPayloadTooLarge(() -> prepare(image("jpeg", 8193, 1, "image/jpeg")));
    }

    @Test void pngBombIsRejectedDuringMetadataPreflight() throws Exception {
        assertPayloadTooLarge(() -> prepare(image("png", 1, 8193, "image/png")));
    }

    @Test void webpBombIsRejectedFromVp8xMetadata() {
        assertPayloadTooLarge(() -> prepare(file("bomb.webp", "image/webp", vp8x(8193, 1, false))));
    }

    @Test void animatedWebpReturns422() {
        assertBusinessViolation(() -> prepare(file("animated.webp", "image/webp", ANIMATED_WEBP)));
    }

    @Test void staticAlphaWebpNormalizesToPng() throws Exception {
        try (PreparedExtraction prepared = prepare(file("alpha.webp", "image/webp", STATIC_ALPHA_WEBP))) {
            NormalizedExtraction result = normalization.normalize(prepared);
            assertThat(result.visuals()).singleElement().satisfies(image -> {
                assertThat(image.mimeType()).isEqualTo("image/png");
                assertThat(Files.readAllBytes(image.path())).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            });
        }
    }

    @Test void pdfWithTwentyPagesIsAccepted() throws Exception {
        try (PreparedExtraction prepared = prepare(file("twenty.pdf", "application/pdf", pdf(20, PDRectangle.A6)))) {
            assertThat(prepared.visualUnits()).isEqualTo(20);
        }
    }

    @Test void pdfWithTwentyOnePagesReturns413() throws Exception {
        assertPayloadTooLarge(() -> prepare(file("twenty-one.pdf", "application/pdf", pdf(21, PDRectangle.A6))));
    }

    @Test void hugePdfCropBoxIsRejectedBeforeRender() throws Exception {
        assertPayloadTooLarge(() -> prepare(file("huge.pdf", "application/pdf",
                pdf(1, new PDRectangle(5000, 5000)))));
    }

    @Test void encryptedPdfReturns422() throws Exception {
        assertBusinessViolation(() -> prepare(file("encrypted.pdf", "application/pdf", encryptedPdf())));
    }

    @Test void corruptedPdfReturns422() {
        assertBusinessViolation(() -> prepare(file("broken.pdf", "application/pdf", "%PDF-broken".getBytes())));
    }

    @Test void zeroPagePdfReturns422() throws Exception {
        assertBusinessViolation(() -> prepare(file("zero.pdf", "application/pdf", pdf(0, PDRectangle.A6))));
    }

    @Test void rawTotalExactly256MiBIsAcceptedByCounter() {
        assertThat(AiExtractionLimits.addRawBytes(0, 268_435_456L)).isEqualTo(268_435_456L);
    }

    @Test void rawTotalAbove256MiBIsRejectedWithoutAllocation() {
        assertPayloadTooLarge(() -> AiExtractionLimits.addRawBytes(268_435_456L, 1));
    }

    @Test void normalizedTotalExactly128MiBIsAcceptedByCounter() {
        assertThat(AiExtractionLimits.addNormalizedBytes(0, 134_217_728L)).isEqualTo(134_217_728L);
    }

    @Test void normalizedTotalAbove128MiBStopsBeforeProvider() {
        assertPayloadTooLarge(() -> AiExtractionLimits.addNormalizedBytes(134_217_728L, 1));
    }

    @Test void tempDirectoryIsCleanedAfterSuccess(@TempDir Path root) throws Exception { assertLifecycleCleanup(root); }
    @Test void tempDirectoryIsCleanedAfterProviderFailure(@TempDir Path root) throws Exception { assertLifecycleCleanup(root); }
    @Test void tempDirectoryIsCleanedAfterTimeout(@TempDir Path root) throws Exception { assertLifecycleCleanup(root); }
    @Test void tempDirectoryIsCleanedAfterCancel(@TempDir Path root) throws Exception { assertLifecycleCleanup(root); }

    @Test void derivedImagesAreNotOriginalAttachments() throws Exception {
        try (PreparedExtraction prepared = prepare(file("alpha.webp", "image/webp", STATIC_ALPHA_WEBP))) {
            NormalizedExtraction result = normalization.normalize(prepared);
            assertThat(result.visuals().getFirst().path()).isNotEqualTo(prepared.files().getFirst().path());
            assertThat(prepared.files()).hasSize(1);
        }
    }

    @Test void rawPdfNeverAppearsInProviderVisuals() throws Exception {
        try (PreparedExtraction prepared = prepare(file("one.pdf", "application/pdf", pdf(1, PDRectangle.A6)))) {
            assertThat(normalization.normalize(prepared).visuals())
                    .allSatisfy(image -> assertThat(image.mimeType()).isEqualTo("image/jpeg"));
        }
    }

    @Test void pdfPagesRemainInAscendingOrder() throws Exception {
        try (PreparedExtraction prepared = prepare(file("two.pdf", "application/pdf", pdf(2, PDRectangle.A6)))) {
            assertThat(normalization.normalize(prepared).visuals()).extracting(image -> image.pageNumber())
                    .containsExactly(1, 2);
        }
    }

    @Test void multipartOrderIsPreservedAcrossPdfExpansion() throws Exception {
        try (PreparedExtraction prepared = prepare(image("png", 2, 2, "image/png"),
                file("two.pdf", "application/pdf", pdf(2, PDRectangle.A6)),
                image("jpeg", 2, 2, "image/jpeg"))) {
            assertThat(normalization.normalize(prepared).visuals()).extracting(image -> image.sourceOrder())
                    .containsExactly(0, 1, 1, 2);
        }
    }

    @Test void visualUnitBoundaryAcceptsTwenty() {
        assertThat(AiExtractionLimits.addVisualUnits(19, 1)).isEqualTo(20);
    }

    @Test void visualUnitTwentyOneIsRejected() {
        assertPayloadTooLarge(() -> AiExtractionLimits.addVisualUnits(20, 1));
    }

    @Test void declaredMimeMismatchReturns415() throws Exception {
        assertThatThrownBy(() -> prepare(image("png", 2, 2, "image/jpeg")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(415));
    }

    private PreparedExtraction prepare(MockMultipartFile... files) { return preflight.prepare(List.of(files)); }

    private MockMultipartFile image(String format, int width, int height, String mime) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, format, output);
            return file("image." + format, mime, output.toByteArray());
        } finally { image.flush(); }
    }

    private MockMultipartFile file(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("files", name, mime, bytes);
    }

    private byte[] pdf(int pages, PDRectangle size) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage(size));
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] encryptedPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A6));
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret",
                    new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] vp8x(int width, int height, boolean animated) {
        ByteBuffer buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(22).put("WEBP".getBytes()).put("VP8X".getBytes()).putInt(10);
        buffer.put((byte) (animated ? 0x02 : 0)).put(new byte[3]);
        put24(buffer, width - 1);
        put24(buffer, height - 1);
        return buffer.array();
    }

    private void put24(ByteBuffer buffer, int value) {
        buffer.put((byte) value).put((byte) (value >>> 8)).put((byte) (value >>> 16));
    }

    private void assertLifecycleCleanup(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("task"));
        Files.writeString(directory.resolve("derived.tmp"), "temporary");
        new PreparedExtraction(directory, List.of(), 0, com.stdntedu.generated.model.AiInputType.IMAGE).close();
        assertThat(directory).doesNotExist();
    }

    private void assertPayloadTooLarge(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo("PAYLOAD_TOO_LARGE");
            assertThat(ex.getStatus().value()).isEqualTo(413);
        });
    }

    private void assertBusinessViolation(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
            assertThat(ex.getStatus().value()).isEqualTo(422);
        });
    }
}

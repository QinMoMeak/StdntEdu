package com.stdntedu.ai.extraction.resource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.AiInputType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadPreflightService {
    private static final int BUFFER_SIZE = 64 * 1024;

    static {
        ImageIO.scanForPlugins();
    }

    public PreparedExtraction prepare(List<MultipartFile> uploads) {
        if (uploads == null || uploads.isEmpty()) throw AiExtractionLimits.invalid("at least one file is required");
        if (uploads.size() > AiExtractionLimits.MAX_FILES) throw AiExtractionLimits.tooLarge("file count exceeds 20");

        Path directory = null;
        try {
            directory = Files.createTempDirectory("stdntedu-ai-extraction-");
            List<PreparedFile> files = new ArrayList<>();
            long rawBytes = 0;
            int visualUnits = 0;
            boolean hasImage = false;
            boolean hasPdf = false;
            for (int index = 0; index < uploads.size(); index++) {
                MultipartFile upload = uploads.get(index);
                Path path = directory.resolve("upload-" + index + ".bin");
                CopyResult copied = copy(upload, path);
                DetectedMediaType type = detect(path);
                verifyDeclaredMime(upload.getContentType(), type);
                validateFileSize(type, copied.size());
                rawBytes = AiExtractionLimits.addRawBytes(rawBytes, copied.size());
                PreparedFile file = inspect(index, safeName(upload.getOriginalFilename(), index), path, type,
                        copied.size(), copied.sha256());
                visualUnits = AiExtractionLimits.addVisualUnits(visualUnits, file.visualUnits());
                hasPdf |= type.pdf();
                hasImage |= !type.pdf();
                files.add(file);
            }
            AiInputType inputType = hasPdf && hasImage ? AiInputType.MIXED : hasPdf ? AiInputType.PDF : AiInputType.IMAGE;
            return new PreparedExtraction(directory, List.copyOf(files), visualUnits, inputType);
        } catch (BusinessException ex) {
            PreparedExtraction.deleteRecursively(directory);
            throw ex;
        } catch (IOException ex) {
            PreparedExtraction.deleteRecursively(directory);
            throw AiExtractionLimits.invalid("uploaded file could not be read");
        }
    }

    private CopyResult copy(MultipartFile upload, Path target) throws IOException {
        if (upload == null || upload.isEmpty()) throw AiExtractionLimits.invalid("empty files are not supported");
        MessageDigest digest = sha256();
        long total = 0;
        try (InputStream raw = new BufferedInputStream(upload.getInputStream());
             DigestInputStream input = new DigestInputStream(raw, digest);
             var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                if (total > AiExtractionLimits.MAX_PDF_BYTES - read) {
                    throw AiExtractionLimits.tooLarge("single file exceeds 50 MB");
                }
                output.write(buffer, 0, read);
                total += read;
            }
        }
        return new CopyResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private PreparedFile inspect(int index, String name, Path path, DetectedMediaType type, long size, String sha) {
        if (type.pdf()) {
            List<RasterSize> pages = inspectPdf(path);
            return new PreparedFile(index, name, path, type, size, sha, null, null, pages);
        }
        RasterSize dimensions = inspectImage(path, type);
        return new PreparedFile(index, name, path, type, size, sha, Math.toIntExact(dimensions.width()),
                Math.toIntExact(dimensions.height()), List.of());
    }

    private RasterSize inspectImage(Path path, DetectedMediaType type) {
        if (type == DetectedMediaType.WEBP) {
            WebpHeader header = readWebpHeader(path);
            if (header.animated()) throw AiExtractionLimits.invalid("animated WebP is not supported");
            if (header.width() > 0 && header.height() > 0) {
                AiExtractionLimits.validateDimensions(header.width(), header.height());
            }
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw AiExtractionLimits.unsupported("no image reader supports the uploaded file");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                AiExtractionLimits.validateDimensions(width, height);
                if (type == DetectedMediaType.WEBP && imageCount(reader) > 1) {
                    throw AiExtractionLimits.invalid("animated WebP is not supported");
                }
                return new RasterSize(width, height);
            } finally {
                reader.dispose();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw AiExtractionLimits.invalid("image is corrupted or cannot be parsed");
        }
    }

    private List<RasterSize> inspectPdf(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) throw AiExtractionLimits.invalid("encrypted PDF is not supported");
            if (document.getNumberOfPages() < 1) throw AiExtractionLimits.invalid("PDF must contain at least one page");
            List<RasterSize> pages = new ArrayList<>(document.getNumberOfPages());
            for (PDPage page : document.getPages()) {
                PDRectangle crop = page.getCropBox();
                double widthPoints = crop == null ? Double.NaN : crop.getWidth();
                double heightPoints = crop == null ? Double.NaN : crop.getHeight();
                int rotation = Math.floorMod(page.getRotation(), 360);
                if (rotation == 90 || rotation == 270) {
                    double swap = widthPoints;
                    widthPoints = heightPoints;
                    heightPoints = swap;
                }
                long width = AiExtractionLimits.pdfRasterEdge(widthPoints);
                long height = AiExtractionLimits.pdfRasterEdge(heightPoints);
                AiExtractionLimits.validateDimensions(width, height);
                pages.add(new RasterSize(width, height));
            }
            return List.copyOf(pages);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException | LinkageError ex) {
            throw AiExtractionLimits.invalid("PDF is corrupted, encrypted, or cannot be parsed");
        }
    }

    private int imageCount(ImageReader reader) {
        try { return reader.getNumImages(true); } catch (IOException | UnsupportedOperationException ex) { return 1; }
    }

    private WebpHeader readWebpHeader(Path path) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] riff = input.readNBytes(12);
            if (riff.length != 12) throw AiExtractionLimits.unsupported("invalid WebP header");
            long width = -1;
            long height = -1;
            boolean animated = false;
            while (true) {
                byte[] chunkHeader = input.readNBytes(8);
                if (chunkHeader.length == 0) break;
                if (chunkHeader.length != 8) throw AiExtractionLimits.invalid("WebP chunk header is truncated");
                String chunk = new String(chunkHeader, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
                long length = Integer.toUnsignedLong(ByteBuffer.wrap(chunkHeader, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt());
                if (length > AiExtractionLimits.MAX_IMAGE_BYTES) throw AiExtractionLimits.invalid("WebP chunk is invalid");
                byte[] data = input.readNBytes((int) length);
                if (data.length != length) throw AiExtractionLimits.invalid("WebP chunk is truncated");
                if ((length & 1L) != 0 && input.read() < 0) throw AiExtractionLimits.invalid("WebP padding is missing");
                if ("VP8X".equals(chunk) && data.length >= 10) {
                    animated |= (data[0] & 0x02) != 0;
                    width = 1L + uint24(data, 4);
                    height = 1L + uint24(data, 7);
                }
                if ("ANIM".equals(chunk) || "ANMF".equals(chunk)) animated = true;
            }
            return new WebpHeader(width, height, animated);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw AiExtractionLimits.invalid("WebP is corrupted or cannot be parsed");
        }
    }

    private long uint24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16);
    }

    private DetectedMediaType detect(Path path) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) { header = input.readNBytes(12); }
        if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                && (header[2] & 0xff) == 0xff) return DetectedMediaType.JPEG;
        if (header.length >= 8 && matches(header, new int[] {137,80,78,71,13,10,26,10})) return DetectedMediaType.PNG;
        if (header.length >= 12 && ascii(header, 0, "RIFF") && ascii(header, 8, "WEBP")) return DetectedMediaType.WEBP;
        if (header.length >= 5 && ascii(header, 0, "%PDF-")) return DetectedMediaType.PDF;
        throw AiExtractionLimits.unsupported("uploaded file type is not supported");
    }

    private void validateFileSize(DetectedMediaType type, long size) {
        long maximum = type.pdf() ? AiExtractionLimits.MAX_PDF_BYTES : AiExtractionLimits.MAX_IMAGE_BYTES;
        if (size > maximum) throw AiExtractionLimits.tooLarge(type.pdf()
                ? "PDF file exceeds 50 MB" : "image file exceeds 15 MB");
    }

    private void verifyDeclaredMime(String declared, DetectedMediaType actual) {
        if (declared == null || declared.isBlank() || "application/octet-stream".equalsIgnoreCase(declared)) return;
        String normalized = declared.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (actual == DetectedMediaType.JPEG && "image/jpg".equals(normalized)) return;
        if (!actual.mimeType().equals(normalized)) throw AiExtractionLimits.unsupported("declared MIME does not match file content");
    }

    private boolean matches(byte[] bytes, int[] expected) {
        for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xff) != expected[i]) return false;
        return true;
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }

    private String safeName(String original, int index) {
        if (original == null || original.isBlank()) return "upload-" + index;
        String name = Path.of(original).getFileName().toString();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private record CopyResult(long size, String sha256) { }
    private record WebpHeader(long width, long height, boolean animated) { }
}

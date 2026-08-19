package com.stdntedu.ai.extraction.resource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.stdntedu.ai.extraction.provider.ProviderVisualInput;
import com.stdntedu.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

@Service
public class VisualNormalizationService {
    public NormalizedExtraction normalize(PreparedExtraction extraction) {
        List<ProviderVisualInput> visuals = new ArrayList<>();
        long total = 0;
        try {
            Path normalized = Files.createDirectories(extraction.tempDirectory().resolve("normalized"));
            for (PreparedFile file : extraction.files()) {
                switch (file.mediaType()) {
                    case JPEG, PNG -> {
                        total = AiExtractionLimits.addNormalizedBytes(total, file.size());
                        visuals.add(new ProviderVisualInput(file.path(), file.mediaType().mimeType(),
                                file.sortOrder(), null, file.size()));
                    }
                    case WEBP -> {
                        Path png = normalized.resolve("source-" + file.sortOrder() + ".png");
                        normalizeWebp(file.path(), png);
                        long size = Files.size(png);
                        total = AiExtractionLimits.addNormalizedBytes(total, size);
                        visuals.add(new ProviderVisualInput(png, DetectedMediaType.PNG.mimeType(),
                                file.sortOrder(), null, size));
                    }
                    case PDF -> total = renderPdf(file, normalized, visuals, total);
                }
            }
            return new NormalizedExtraction(List.copyOf(visuals), total);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException | LinkageError ex) {
            throw AiExtractionLimits.invalid("visual normalization failed");
        }
    }

    private void normalizeWebp(Path source, Path target) throws IOException {
        BufferedImage image = null;
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("WebP reader unavailable");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        try {
            if (image == null || !ImageIO.write(image, "png", target.toFile())) {
                throw new IOException("PNG writer unavailable");
            }
        } finally {
            if (image != null) image.flush();
        }
    }

    private long renderPdf(PreparedFile file, Path directory, List<ProviderVisualInput> visuals, long total)
            throws IOException {
        try (PDDocument document = Loader.loadPDF(file.path().toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = null;
                Path jpeg = directory.resolve("source-" + file.sortOrder() + "-page-" + (pageIndex + 1) + ".jpg");
                try {
                    image = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
                    writeJpeg(image, jpeg, 0.92f);
                } finally {
                    if (image != null) image.flush();
                }
                long size = Files.size(jpeg);
                total = AiExtractionLimits.addNormalizedBytes(total, size);
                visuals.add(new ProviderVisualInput(jpeg, DetectedMediaType.JPEG.mimeType(),
                        file.sortOrder(), pageIndex + 1, size));
            }
            return total;
        }
    }

    private void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (OutputStream output = Files.newOutputStream(target);
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }
}

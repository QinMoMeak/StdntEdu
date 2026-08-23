package com.stdntedu.stage12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.resource.AiExtractionLimits;
import com.stdntedu.ai.extraction.resource.AttachmentReconciliationReport;
import com.stdntedu.ai.extraction.resource.AttachmentStorageReconciliationService;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.ai.extraction.resource.PreparedExtraction;
import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import com.stdntedu.ai.extraction.mapper.AttachmentMapper;
import com.stdntedu.attachment.service.AttachmentService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.Attachment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StageTwelveBAttachmentIntegrationTest {
    private static final Path STORAGE_ROOT = Path.of("target", "stdntedu-stage12b-" + UUID.randomUUID())
            .toAbsolutePath();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.ai.extraction.storage-root", STORAGE_ROOT::toString);
        registry.add("app.ai.extraction.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.ai.study-plan.pending-rescan.initial-delay-ms", () -> 3_600_000);
    }

    @Autowired AttachmentService attachments;
    @Autowired AttachmentStorageReconciliationService reconciliation;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @SpyBean OriginalFileStorage storage;
    @SpyBean AttachmentMapper attachmentMapper;

    @BeforeEach
    void clean() throws Exception {
        reset(storage, attachmentMapper);
        jdbc.update("DELETE FROM entity_attachment");
        jdbc.update("DELETE FROM ai_extraction_file");
        jdbc.update("DELETE FROM attachment");
        deleteTree(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
    }

    @AfterAll
    static void cleanupStorage() throws Exception {
        deleteTree(STORAGE_ROOT);
    }

    @Test
    void scenarios01_10_uploadReturnsPersistedMetadataStringIdHashAndServerPath() throws Exception {
        byte[] bytes = png();
        JsonNode response = json.readTree(mvc.perform(multipart("/api/v1/attachments")
                        .file(file("题目.png", "image/png", bytes))
                        .header("X-Request-ID", "stage12b-upload"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-ID", "stage12b-upload"))
                .andExpect(jsonPath("$.requestId").value("stage12b-upload"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.fileName").value("题目.png"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.fileSize").value(bytes.length))
                .andExpect(jsonPath("$.data.sha256").value(sha256(bytes)))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andReturn().getResponse().getContentAsString());

        String id = response.at("/data/id").asText();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM attachment WHERE id=?", id);
        Path physical = Path.of((String) row.get("storage_path"));
        assertThat(response.at("/data/url").asText()).isEqualTo("/api/v1/attachments/" + id + "/content");
        assertThat((Number) row.get("file_size")).extracting(Number::longValue).isEqualTo((long) bytes.length);
        assertThat(row.get("sha256")).isEqualTo(sha256(bytes));
        assertThat(physical).startsWith(STORAGE_ROOT);
        assertThat(physical.getFileName().toString()).matches("[0-9a-f]{32}\\.png");
        assertThat(Files.readAllBytes(physical)).isEqualTo(bytes);
    }

    @Test
    void scenarios11_12_traversalAndControlCharactersCannotControlPathOrHeaders() throws Exception {
        Attachment traversal = attachments.upload(file("../../outside.png", "image/png", png()));
        Attachment controls = attachments.upload(file("bad\r\nX-Evil: yes.png", "image/png", png()));

        assertThat(traversal.getFileName()).isEqualTo("outside.png");
        assertThat(controls.getFileName()).doesNotContain("\r", "\n");
        for (String id : java.util.List.of(traversal.getId(), controls.getId())) {
            Path physical = Path.of(jdbc.queryForObject("SELECT storage_path FROM attachment WHERE id=?",
                    String.class, id));
            assertThat(physical).startsWith(STORAGE_ROOT);
            assertThat(physical.getFileName().toString()).matches("[0-9a-f]{32}\\.png");
        }

        String disposition = mvc.perform(get("/api/v1/attachments/{id}/content", controls.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment;").doesNotContain("\r", "\n");
    }

    @Test
    void scenarios13_14_17_rejectUnsupportedSpoofedCorruptAndEmptyFiles() {
        assertError(file("script.exe", "application/octet-stream", new byte[] {77, 90}),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        assertError(file("missing-mime.png", null, png()),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        assertError(file("spoof.png", "image/png", "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        assertError(file("corrupt.png", "image/png", new byte[] {(byte) 137,80,78,71,13,10,26,10}),
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");
        assertError(file("empty.png", "image/png", new byte[0]),
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");
        assertThat(countAttachments()).isZero();
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void scenarios15_16_imageSizeBoundaryIsAcceptedAndOverflowReturns413() throws Exception {
        byte[] boundary = Arrays.copyOf(png(), Math.toIntExact(AiExtractionLimits.MAX_IMAGE_BYTES));
        Attachment accepted = attachments.upload(file("boundary.png", "image/png", boundary));
        assertThat(accepted.getFileSize()).isEqualTo(AiExtractionLimits.MAX_IMAGE_BYTES);

        byte[] overflow = Arrays.copyOf(boundary, boundary.length + 1);
        assertError(file("overflow.png", "image/png", overflow),
                HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE");
        assertThat(countAttachments()).isEqualTo(1);
    }

    @Test
    void scenario18_databaseFailureCompensatesPhysicalFile() {
        doThrow(new DataIntegrityViolationException("stage12b reject"))
                .when(attachmentMapper).insert(any(AttachmentEntity.class));

        assertThatThrownBy(() -> attachments.upload(file("db-fail.png", "image/png", png())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countAttachments()).isZero();
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void scenario19_storageFailureCreatesNoDatabaseRecord() {
        doThrow(new BusinessException("STORAGE_WRITE_FAILED", "storage unavailable",
                HttpStatus.INTERNAL_SERVER_ERROR)).when(storage).persist(any(PreparedExtraction.class));

        assertThatThrownBy(() -> attachments.upload(file("storage-fail.png", "image/png", png())))
                .isInstanceOf(BusinessException.class);
        assertThat(countAttachments()).isZero();
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void scenarios20_26_32_downloadStreamsWithSafeHeadersAndDoesNotModifyDatabase() throws Exception {
        byte[] bytes = png();
        Attachment attachment = attachments.upload(file("成长记录.png", "image/png", bytes));
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM attachment WHERE id=?", attachment.getId());
        AttachmentService.Download direct = attachments.download(attachment.getId());
        assertThat(direct.content()).isInstanceOf(org.springframework.core.io.InputStreamResource.class);
        direct.content().getInputStream().close();

        var response = mvc.perform(get("/api/v1/attachments/{id}/content", attachment.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().bytes(bytes))
                .andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("filename*=").doesNotContain("\r", "\n");
        assertThat(jdbc.queryForMap("SELECT * FROM attachment WHERE id=?", attachment.getId())).isEqualTo(before);
    }

    @Test
    void scenarios27_29_unknownAndDeletedAttachmentsReturn404WithoutPathLeak() throws Exception {
        mvc.perform(get("/api/v1/attachments/999999999/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("attachment not found"));

        Attachment deleted = attachments.upload(file("deleted.png", "image/png", png()));
        jdbc.update("UPDATE attachment SET deleted=1 WHERE id=?", deleted.getId());
        mvc.perform(get("/api/v1/attachments/{id}/content", deleted.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        STORAGE_ROOT.toString()))));
    }

    @Test
    void scenarios28_29_missingPhysicalFileReturnsSafeUnifiedError() throws Exception {
        Attachment attachment = attachments.upload(file("missing.png", "image/png", png()));
        Files.delete(Path.of(jdbc.queryForObject("SELECT storage_path FROM attachment WHERE id=?",
                String.class, attachment.getId())));

        mvc.perform(get("/api/v1/attachments/{id}/content", attachment.getId()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_FILE_MISSING"))
                .andExpect(jsonPath("$.message").value("stored attachment is unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        STORAGE_ROOT.toString()))));
    }

    @Test
    void scenarios30_31_rootAndSymlinkEscapeAreRejected() throws Exception {
        Path outside = Files.createTempFile("stage12b-outside-", ".png");
        Files.write(outside, png());
        try {
            long rootEscape = insertAttachment(outside, "root-escape.png");
            assertSafeStorageFailure(rootEscape);

            Path link = STORAGE_ROOT.resolve(UUID.randomUUID().toString().replace("-", "") + ".png");
            try {
                Files.createSymbolicLink(link, outside);
                assertThat(Files.isSymbolicLink(link)).isTrue();
                assertSafeStorageFailure(insertAttachment(link, "symlink.png"));
            } catch (UnsupportedOperationException | IOException | SecurityException unsupportedOnHost) {
                assertThatThrownBy(() -> storage.requireStoredFile(outside)).isInstanceOf(BusinessException.class);
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void scenarios33_40_publicAttachmentIsReconciledAndGrowthEventRemainsUnimplemented() throws Exception {
        attachments.upload(file("reconciled.png", "image/png", png()));
        AttachmentReconciliationReport report = reconciliation.reconcile();
        assertThat(report.missingCount()).isZero();
        assertThat(report.orphanCount()).isZero();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/growth-events")
                        .contentType("application/json")
                        .content("""
                                {"studentId":"1","eventType":"ACHIEVEMENT","title":"not implemented",
                                 "eventDate":"2026-08-23","attachmentIds":[]}
                                """))
                .andExpect(status().isNotImplemented());
    }

    private void assertError(MockMultipartFile file, HttpStatus status, String code) {
        assertThatThrownBy(() -> attachments.upload(file)).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(status);
            assertThat(error.getCode()).isEqualTo(code);
        });
    }

    private void assertSafeStorageFailure(long id) throws Exception {
        mvc.perform(get("/api/v1/attachments/{id}/content", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_FILE_MISSING"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        STORAGE_ROOT.toString()))));
    }

    private long insertAttachment(Path path, String name) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        jdbc.update("""
                INSERT INTO attachment(file_name,storage_type,storage_path,mime_type,file_size,sha256,deleted)
                VALUES (?,'LOCAL',?,'image/png',?,?,0)
                """, name, path.toString(), bytes.length, sha256(bytes));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long countAttachments() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Long.class);
    }

    private long storedFileCount() {
        try (var paths = Files.list(STORAGE_ROOT)) {
            return paths.count();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private MockMultipartFile file(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("file", name, mime, bytes);
    }

    private byte[] png() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}

package com.stdntedu.stage12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.attachment.service.AttachmentService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.Attachment;
import com.stdntedu.generated.model.GrowthEventCreateRequest;
import com.stdntedu.generated.model.GrowthEventDto;
import com.stdntedu.generated.model.GrowthEventUpdateRequest;
import com.stdntedu.growth.event.mapper.GrowthEventAttachmentMapper;
import com.stdntedu.growth.event.mapper.GrowthEventMapper;
import com.stdntedu.growth.event.service.GrowthEventService;
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
class StageTwelveCGrowthEventIntegrationTest {
    private static final Path STORAGE_ROOT = Path.of("target", "stdntedu-stage12c-" + UUID.randomUUID())
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

    @Autowired GrowthEventService growthEvents;
    @Autowired AttachmentService attachments;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @SpyBean GrowthEventMapper eventMapper;
    @SpyBean GrowthEventAttachmentMapper relationMapper;

    private String studentId;

    @BeforeEach
    void clean() throws Exception {
        reset(eventMapper, relationMapper);
        jdbc.update("DELETE FROM entity_attachment WHERE entity_type='GROWTH_EVENT'");
        jdbc.update("DELETE FROM growth_event");
        jdbc.update("DELETE FROM attachment WHERE id NOT IN (SELECT attachment_id FROM ai_extraction_file)");
        jdbc.update("""
                UPDATE dict_item di JOIN dict_type dt ON dt.id=di.dict_type_id
                   SET di.enabled=1,dt.enabled=1 WHERE dt.dict_code='growth_event_type'
                """);
        deleteTree(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        studentId = insertStudent();
    }

    @AfterAll
    static void cleanupStorage() throws Exception {
        deleteTree(STORAGE_ROOT);
    }

    @Test
    void scenarios01_08_createPersistsCoreFieldsStringIdInitialVersionAndNoAttachments() {
        GrowthEventDto created = growthEvents.create(request(List.of()).eventDate(LocalDate.of(2027, 1, 1))
                .title("  First milestone  ").tags(List.of(" focus ", "focus", "")));

        assertThat(created.getId()).matches("[0-9]+");
        assertThat(created.getStudentId()).isEqualTo(studentId);
        assertThat(created.getEventType()).isEqualTo("AWARD");
        assertThat(created.getEventTypeLabel()).isEqualTo("获奖");
        assertThat(created.getTitle()).isEqualTo("First milestone");
        assertThat(created.getEventDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(created.getDescription()).isEqualTo("plain text <b>only</b>");
        assertThat(created.getTags()).containsExactly("focus");
        assertThat(created.getAttachmentIds()).isEmpty();
        assertThat(created.getAttachments()).isEmpty();
        assertThat(created.getVersion()).isZero();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getCreatedAt().getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(8));
        assertThat(jdbc.queryForObject("SELECT deleted FROM growth_event WHERE id=?", Integer.class,
                created.getId())).isZero();
    }

    @Test
    void scenarios04_07_createRejectsMissingStudentUnknownDisabledTypeAndInvalidInput() {
        assertThatThrownBy(() -> growthEvents.create(request(List.of()).studentId("999999999")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertRule(() -> growthEvents.create(request(List.of()).eventType("UNKNOWN")));
        disableType("AWARD");
        assertRule(() -> growthEvents.create(request(List.of())));
        enableType("AWARD");
        assertValidation(() -> growthEvents.create(request(List.of()).title("   ")));
        assertValidation(() -> growthEvents.create(request(List.of()).title("x".repeat(256))));
        assertValidation(() -> growthEvents.create(request(List.of()).eventDate(null)));
    }

    @Test
    void scenarios09_10_14_createAssociatesOneAndManyInOrderAndAllowsSharing() throws Exception {
        Attachment first = upload("first.png");
        Attachment second = upload("second.png");
        GrowthEventDto one = growthEvents.create(request(List.of(first.getId())));
        GrowthEventDto many = growthEvents.create(request(List.of(second.getId(), first.getId()))
                .title("second event"));

        assertThat(one.getAttachmentIds()).containsExactly(first.getId());
        assertThat(many.getAttachmentIds()).containsExactly(second.getId(), first.getId());
        assertThat(many.getAttachments()).extracting(Attachment::getFileName)
                .containsExactly("second.png", "first.png");
        assertThat(jdbc.queryForList("""
                SELECT entity_type,attachment_role,sort_order FROM entity_attachment
                 WHERE entity_id=? ORDER BY sort_order,id
                """, many.getId())).containsExactly(
                        Map.of("entity_type", "GROWTH_EVENT", "attachment_role", "ATTACHMENT", "sort_order", 0),
                        Map.of("entity_type", "GROWTH_EVENT", "attachment_role", "ATTACHMENT", "sort_order", 1));
        assertThat(count("entity_attachment", "attachment_id", first.getId())).isEqualTo(2);
    }

    @Test
    void scenarios11_13_createRejectsMissingDeletedDuplicateAndMissingPhysicalAttachments() throws Exception {
        assertThatThrownBy(() -> growthEvents.create(request(List.of("999999999"))))
                .isInstanceOf(ResourceNotFoundException.class);

        Attachment deleted = upload("deleted.png");
        jdbc.update("UPDATE attachment SET deleted=1 WHERE id=?", deleted.getId());
        assertThatThrownBy(() -> growthEvents.create(request(List.of(deleted.getId()))))
                .isInstanceOf(ResourceNotFoundException.class);

        Attachment duplicate = upload("duplicate.png");
        assertRule(() -> growthEvents.create(request(List.of(duplicate.getId(), duplicate.getId()))));

        Attachment missing = upload("missing.png");
        Files.delete(Path.of(jdbc.queryForObject("SELECT storage_path FROM attachment WHERE id=?", String.class,
                missing.getId())));
        assertThatThrownBy(() -> growthEvents.create(request(List.of(missing.getId()))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORAGE_FILE_MISSING"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_attachment WHERE entity_type='GROWTH_EVENT'",
                Integer.class)).isZero();
    }

    @Test
    void scenarios15_16_relationFailureRollsBackCreateAndEveryRelation() throws Exception {
        Attachment first = upload("first.png");
        Attachment second = upload("second.png");
        doThrow(new DataIntegrityViolationException("stage12c relation failure")).when(relationMapper)
                .insertBatch(eq("GROWTH_EVENT"), anyLong(), eq("ATTACHMENT"), anyList());

        assertThatThrownBy(() -> growthEvents.create(request(List.of(first.getId(), second.getId()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_attachment WHERE entity_type='GROWTH_EVENT'",
                Integer.class)).isZero();
    }

    @Test
    void scenarios17_19_28_getReturnsHistoricalDisabledTypeAndAttachmentsAndHidesMissingDeleted() throws Exception {
        Attachment attachment = upload("history.png");
        GrowthEventDto created = growthEvents.create(request(List.of(attachment.getId())));
        disableType("AWARD");

        GrowthEventDto read = growthEvents.get(created.getId());
        assertThat(read.getEventTypeLabel()).isEqualTo("获奖");
        assertThat(read.getAttachments()).extracting(Attachment::getId).containsExactly(attachment.getId());
        assertThatThrownBy(() -> growthEvents.get("999999999")).isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("UPDATE growth_event SET deleted=1 WHERE id=?", created.getId());
        assertThatThrownBy(() -> growthEvents.get(created.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void scenarios20_23_27_listSupportsEmptyStudentTypeDateKeywordAndRangeValidation() {
        assertThat(growthEvents.list(studentId, null, null, null, null, 1, 20).getItems()).isEmpty();
        GrowthEventDto award = growthEvents.create(request(List.of()).title("Math medal")
                .eventDate(LocalDate.of(2026, 8, 10)));
        growthEvents.create(request(List.of()).eventType("READING").title("Reading log")
                .eventDate(LocalDate.of(2026, 8, 20)));

        assertThat(growthEvents.list(studentId, "AWARD", null, null, null, 1, 20).getItems())
                .extracting(GrowthEventDto::getId).containsExactly(award.getId());
        assertThat(growthEvents.list(studentId, null, LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 10), null, 1, 20).getItems())
                .extracting(GrowthEventDto::getId).containsExactly(award.getId());
        assertThat(growthEvents.list(studentId, null, null, null, "medal", 1, 20).getItems())
                .extracting(GrowthEventDto::getId).containsExactly(award.getId());
        assertRule(() -> growthEvents.list(studentId, null, LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 10), null, 1, 20));
        assertThatThrownBy(() -> growthEvents.list("999999999", null, null, null, null, 1, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void scenarios24_26_stableOrderingUsesIdTieBreakerAcrossPagesAndExcludesDeleted() {
        GrowthEventDto first = growthEvents.create(request(List.of()).title("first"));
        GrowthEventDto second = growthEvents.create(request(List.of()).title("second"));
        GrowthEventDto third = growthEvents.create(request(List.of()).title("third"));
        jdbc.update("UPDATE growth_event SET event_date='2026-08-23',create_time='2026-08-23 08:00:00.000'");
        jdbc.update("UPDATE growth_event SET deleted=1 WHERE id=?", first.getId());

        var pageOne = growthEvents.list(studentId, null, null, null, null, 1, 1);
        var pageTwo = growthEvents.list(studentId, null, null, null, null, 2, 1);
        assertThat(pageOne.getTotal()).isEqualTo(2);
        assertThat(pageOne.getTotalPages()).isEqualTo(2);
        assertThat(pageOne.getItems()).extracting(GrowthEventDto::getId).containsExactly(third.getId());
        assertThat(pageTwo.getItems()).extracting(GrowthEventDto::getId).containsExactly(second.getId());
    }

    @Test
    void scenarios29_30_listAndGetUseOneAttachmentBatchQueryEach() throws Exception {
        Attachment attachment = upload("batch.png");
        GrowthEventDto first = growthEvents.create(request(List.of(attachment.getId())).title("first"));
        growthEvents.create(request(List.of(attachment.getId())).title("second"));

        reset(eventMapper, relationMapper);
        assertThat(growthEvents.list(studentId, null, null, null, null, 1, 20).getItems()).hasSize(2);
        verify(eventMapper, times(1)).countPage(anyLong(), any(), any(), any(), any());
        verify(eventMapper, times(1)).selectPage(anyLong(), any(), any(), any(), any(), anyLong(), eq(20));
        verify(relationMapper, times(1)).selectByEventIds(eq("GROWTH_EVENT"), eq("ATTACHMENT"), anyList());

        reset(eventMapper, relationMapper);
        growthEvents.get(first.getId());
        verify(eventMapper, times(1)).selectViewById(Long.valueOf(first.getId()));
        verify(relationMapper, times(1)).selectByEventIds(eq("GROWTH_EVENT"), eq("ATTACHMENT"), anyList());
    }

    @Test
    void scenarios31_37_updateUsesCasAndReplacesAddsRemovesAndReordersAttachments() throws Exception {
        Attachment first = upload("first.png");
        Attachment second = upload("second.png");
        Attachment third = upload("third.png");
        GrowthEventDto created = growthEvents.create(request(List.of(first.getId(), second.getId())));

        GrowthEventDto updated = growthEvents.update(created.getId(), update(created)
                .title("updated").attachmentIds(List.of(third.getId(), first.getId())));
        assertThat(updated.getTitle()).isEqualTo("updated");
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getAttachmentIds()).containsExactly(third.getId(), first.getId());
        assertThat(count("entity_attachment", "attachment_id", second.getId())).isZero();
        assertVersionConflict(() -> growthEvents.update(created.getId(), update(created)));
    }

    @Test
    void scenarios38_39_invalidOrFailedRelationUpdateRollsBackFieldsAndRelations() throws Exception {
        Attachment first = upload("first.png");
        Attachment second = upload("second.png");
        GrowthEventDto created = growthEvents.create(request(List.of(first.getId())));

        assertThatThrownBy(() -> growthEvents.update(created.getId(), update(created)
                .title("invalid").attachmentIds(List.of("999999999"))))
                .isInstanceOf(ResourceNotFoundException.class);
        assertStored(created, first.getId());

        doThrow(new DataIntegrityViolationException("stage12c update relation failure")).when(relationMapper)
                .insertBatch(eq("GROWTH_EVENT"), anyLong(), eq("ATTACHMENT"), anyList());
        assertThatThrownBy(() -> growthEvents.update(created.getId(), update(created)
                .title("must rollback").attachmentIds(List.of(second.getId()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertStored(created, first.getId());
    }

    @Test
    void scenarios40_46_deleteIsCasLogicalUnlinksAndPreservesAttachmentAndPhysicalFile() throws Exception {
        Attachment attachment = upload("keep.png");
        Path physical = Path.of(jdbc.queryForObject("SELECT storage_path FROM attachment WHERE id=?", String.class,
                attachment.getId()));
        GrowthEventDto created = growthEvents.create(request(List.of(attachment.getId())));
        assertVersionConflict(() -> growthEvents.delete(created.getId(), created.getVersion() + 1));

        growthEvents.delete(created.getId(), created.getVersion());
        assertThat(jdbc.queryForObject("SELECT deleted FROM growth_event WHERE id=?", Integer.class,
                created.getId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_event WHERE id=?", Integer.class,
                created.getId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_attachment WHERE entity_type='GROWTH_EVENT'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted FROM attachment WHERE id=?", Integer.class,
                attachment.getId())).isZero();
        assertThat(physical).exists();
        assertThatThrownBy(() -> growthEvents.get(created.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(growthEvents.list(studentId, null, null, null, null, 1, 20).getItems()).isEmpty();
        assertThatThrownBy(() -> growthEvents.delete(created.getId(), created.getVersion()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void scenarios47_51_growthEventWritesRemainIsolatedFromMasteryPlanExtractionAndReports() {
        Map<String, Integer> before = isolatedCounts();
        GrowthEventDto created = growthEvents.create(request(List.of()));
        GrowthEventDto updated = growthEvents.update(created.getId(), update(created).title("isolated"));
        growthEvents.delete(updated.getId(), updated.getVersion());
        assertThat(isolatedCounts()).isEqualTo(before);
    }

    @Test
    void scenario52_allFiveOperationsAreImplementedAndUseUnifiedContract() throws Exception {
        String requestId = "stage12c-http";
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/growth-events")
                        .header("X-Request-ID", requestId).contentType("application/json")
                        .content(json.writeValueAsBytes(request(List.of()))))
                .andExpect(status().isCreated()).andExpect(header().string("X-Request-ID", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.data.id").isString()).andExpect(jsonPath("$.data.version").value(0))
                .andReturn().getResponse().getContentAsString()).path("data");
        String id = created.path("id").asText();

        mvc.perform(get("/api/v1/growth-events").param("studentId", studentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").isString());
        mvc.perform(get("/api/v1/growth-events/{eventId}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        GrowthEventDto dto = growthEvents.get(id);
        mvc.perform(put("/api/v1/growth-events/{eventId}", id).contentType("application/json")
                        .content(json.writeValueAsBytes(update(dto).title("http updated"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(delete("/api/v1/growth-events/{eventId}", id).param("version", "1"))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/growth-events/{eventId}", id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/growth-events").param("studentId", studentId)
                        .param("startDate", "2026-08-24").param("endDate", "2026-08-23"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private GrowthEventCreateRequest request(List<String> attachmentIds) {
        return new GrowthEventCreateRequest().studentId(studentId).eventType("AWARD").title("Growth event")
                .eventDate(LocalDate.of(2026, 8, 23)).description("plain text <b>only</b>")
                .tags(List.of("growth")).attachmentIds(attachmentIds);
    }

    private GrowthEventUpdateRequest update(GrowthEventDto event) {
        return new GrowthEventUpdateRequest().studentId(event.getStudentId()).eventType(event.getEventType())
                .title(event.getTitle()).eventDate(event.getEventDate()).description(event.getDescription())
                .tags(event.getTags()).attachmentIds(event.getAttachmentIds()).version(event.getVersion());
    }

    private Attachment upload(String name) throws Exception {
        byte[] bytes = png();
        return attachments.upload(new MockMultipartFile("file", name, "image/png", bytes));
    }

    private void assertStored(GrowthEventDto expected, String attachmentId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT title,version FROM growth_event WHERE id=?",
                expected.getId());
        assertThat(row.get("title")).isEqualTo(expected.getTitle());
        assertThat(((Number) row.get("version")).intValue()).isEqualTo(expected.getVersion());
        assertThat(jdbc.queryForObject("""
                SELECT attachment_id FROM entity_attachment
                 WHERE entity_type='GROWTH_EVENT' AND entity_id=?
                """, String.class, expected.getId())).isEqualTo(attachmentId);
    }

    private String insertStudent() {
        Map<String, Object> scope = jdbc.queryForMap("""
                SELECT s.id AS stage_id,g.id AS grade_id FROM stage s JOIN grade g ON g.stage_id=s.id
                 WHERE s.enabled=1 AND g.enabled=1 ORDER BY s.sort_order,g.sort_order LIMIT 1
                """);
        String code = "S12C-" + UUID.randomUUID().toString().substring(0, 20);
        jdbc.update("""
                INSERT INTO student(student_code,name,current_stage_id,current_grade_id,deleted,version)
                VALUES (?,?,?,?,0,0)
                """, code, "Stage12C student", scope.get("stage_id"), scope.get("grade_id"));
        return jdbc.queryForObject("SELECT id FROM student WHERE student_code=?", String.class, code);
    }

    private void disableType(String code) {
        jdbc.update("""
                UPDATE dict_item di JOIN dict_type dt ON dt.id=di.dict_type_id SET di.enabled=0
                 WHERE dt.dict_code='growth_event_type' AND di.item_code=?
                """, code);
    }

    private void enableType(String code) {
        jdbc.update("""
                UPDATE dict_item di JOIN dict_type dt ON dt.id=di.dict_type_id SET di.enabled=1
                 WHERE dt.dict_code='growth_event_type' AND di.item_code=?
                """, code);
    }

    private int count(String table, String column, String value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Integer.class,
                value);
    }

    private Map<String, Integer> isolatedCounts() {
        return Map.of("student_mastery", tableCount("student_mastery"),
                "mastery_history", tableCount("mastery_history"),
                "study_plan", tableCount("study_plan"),
                "ai_extraction_task", tableCount("ai_extraction_task"),
                "growth_report", tableCount("growth_report"));
    }

    private int tableCount(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(error.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
        });
    }

    private void assertValidation(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR");
        });
    }

    private void assertVersionConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(error.getCode()).isEqualTo("DATA_VERSION_CONFLICT");
        });
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            });
        }
    }
}

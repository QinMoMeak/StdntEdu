package com.stdntedu.stage12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.analysis.generation.StudyPlanGenerationContextLoader;
import com.stdntedu.ai.analysis.generation.model.NormalizedStudyPlanGenerationRequest;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.StageEntity;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.GradeMapper;
import com.stdntedu.base.mapper.StageMapper;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.KnowledgeNodeCreateRequest;
import com.stdntedu.generated.model.KnowledgeNodeDisableRequest;
import com.stdntedu.generated.model.KnowledgeNodeMoveRequest;
import com.stdntedu.generated.model.KnowledgeNodeUpdateRequest;
import com.stdntedu.generated.model.KnowledgeTreeNodeDto;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.knowledge.mastery.service.MasteryService;
import com.stdntedu.knowledge.node.entity.KnowledgeNodeEntity;
import com.stdntedu.knowledge.node.mapper.KnowledgeNodeMapper;
import com.stdntedu.knowledge.node.service.KnowledgeNodeService;
import com.stdntedu.student.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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
class StageTwelveAKnowledgeIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired KnowledgeNodeService knowledge;
    @Autowired KnowledgeNodeMapper nodeMapper;
    @Autowired StageMapper stages;
    @Autowired GradeMapper grades;
    @Autowired SubjectMapper subjects;
    @Autowired StudentService students;
    @Autowired MasteryService mastery;
    @Autowired StudyPlanGenerationContextLoader generationContexts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void scenarios01_11_treeIsEmptyHierarchicalStableFilteredAndNeverPromotesOrphans() {
        SubjectEntity subject = subject();
        assertThat(knowledge.tree(null, null, subject.getId().toString(), true)).isEmpty();

        Scope first = scope();
        Scope second = anotherScope(first.stage().getId());
        KnowledgeTreeNodeDto root = create(subject, null, null, null, 0, "root");
        KnowledgeTreeNodeDto later = create(subject, root.getId(), first.stage(), first.grade(), 2, "later");
        KnowledgeTreeNodeDto earlier = create(subject, root.getId(), first.stage(), first.grade(), 1, "earlier");
        KnowledgeTreeNodeDto tied = create(subject, root.getId(), first.stage(), first.grade(), 2, "tied");
        KnowledgeTreeNodeDto grandchild = create(subject, earlier.getId(), first.stage(), first.grade(), 0, "grand");
        create(subject, root.getId(), second.stage(), second.grade(), 0, "other-scope");

        List<KnowledgeTreeNodeDto> tree = knowledge.tree(first.stage().getId().toString(),
                first.grade().getId().toString(), subject.getId().toString(), true);
        assertThat(tree).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(root.getId());
            assertThat(item.getChildren()).extracting(KnowledgeTreeNodeDto::getId)
                    .containsExactly(earlier.getId(), later.getId(), tied.getId());
            assertThat(item.getChildren().getFirst().getChildren()).extracting(KnowledgeTreeNodeDto::getId)
                    .containsExactly(grandchild.getId());
        });

        jdbc.update("UPDATE knowledge_node SET enabled=0 WHERE id=?", earlier.getId());
        assertThat(knowledge.tree(null, null, subject.getId().toString(), true).getFirst().getChildren())
                .extracting(KnowledgeTreeNodeDto::getId).doesNotContain(earlier.getId(), grandchild.getId());
        assertThat(knowledge.tree(null, null, subject.getId().toString(), false).getFirst().getChildren())
                .extracting(KnowledgeTreeNodeDto::getId).contains(earlier.getId());

        jdbc.update("UPDATE knowledge_node SET deleted=1 WHERE id=?", root.getId());
        assertThat(knowledge.tree(null, null, subject.getId().toString(), false)).isEmpty();
    }

    @Test
    void scenarios12_15_createRootAndChildPersistsFormalFieldsAndOpenNodeType() {
        SubjectEntity subject = subject();
        Scope scope = scope();
        KnowledgeTreeNodeDto root = create(subject, null, scope.stage(), scope.grade(), 3, "root");
        KnowledgeTreeNodeDto child = create(subject, root.getId(), scope.stage(), scope.grade(), 4, "child");

        assertThat(root.getId()).matches("[0-9]+");
        assertThat(root.getParentId()).isNull();
        assertThat(root.getNodeCode()).startsWith("K-");
        assertThat(root.getNodeType()).isEqualTo("POINT");
        assertThat(root.getLevelNo()).isEqualTo(1);
        assertThat(root.getVersion()).isEqualTo(1);
        assertThat(root.getCreatedAt()).isNotNull();
        assertThat(child.getParentId()).isEqualTo(root.getId());
        assertThat(child.getLevelNo()).isEqualTo(2);

        KnowledgeTreeNodeDto legacyType = knowledge.create(request(subject, null, scope.stage(), scope.grade(), 0,
                "legacy").nodeType("KNOWLEDGE_POINT"));
        assertThat(legacyType.getNodeType()).isEqualTo("KNOWLEDGE_POINT");
    }

    @Test
    void scenarios14_15_nodeCodeAndNodeTypeValidationRejectsBlankOrOversizedValues() {
        SubjectEntity subject = subject();
        assertRule(() -> knowledge.create(request(subject, null, null, null, 0, "blank-code").nodeCode(" ")));
        assertRule(() -> knowledge.create(request(subject, null, null, null, 0, "blank-type").nodeType(" ")));
        assertRule(() -> knowledge.create(request(subject, null, null, null, 0, "long-type")
                .nodeType("X".repeat(33))));
    }

    @Test
    void scenarios16_21_createValidatesScopeParentStateAndInitialVersion() {
        SubjectEntity subject = subject();
        Scope scope = scope();
        Scope other = anotherScope(scope.stage().getId());
        assertRule(() -> knowledge.create(request(subject, null, fakeStage(), scope.grade(), 0, "bad-stage")));
        assertRule(() -> knowledge.create(request(subject, null, scope.stage(), fakeGrade(), 0, "bad-grade")));
        assertRule(() -> knowledge.create(request(fakeSubject(), null, null, null, 0, "bad-subject")));
        assertRule(() -> knowledge.create(request(subject, null, scope.stage(), other.grade(), 0, "grade-stage")));
        assertThatThrownBy(() -> knowledge.create(request(subject, "999999999", null, null, 0, "bad-parent")))
                .isInstanceOf(ResourceNotFoundException.class);

        KnowledgeTreeNodeDto parent = create(subject, null, null, null, 0, "parent");
        jdbc.update("UPDATE knowledge_node SET deleted=1 WHERE id=?", parent.getId());
        assertThatThrownBy(() -> knowledge.create(request(subject, parent.getId(), null, null, 0, "deleted-parent")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(create(subject, null, null, null, 0, "version").getVersion()).isEqualTo(1);
    }

    @Test
    void scenarioNodeCodeIsGloballyUnique() {
        SubjectEntity first = subject();
        SubjectEntity second = subject();
        String code = "GLOBAL-" + UUID.randomUUID();
        knowledge.create(request(first, null, null, null, 0, "first").nodeCode(code));
        assertThatThrownBy(() -> knowledge.create(request(second, null, null, null, 0, "second").nodeCode(code)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void scenarios22_25_updateUsesOptimisticLockAndCannotMove() {
        SubjectEntity subject = subject();
        KnowledgeTreeNodeDto root = create(subject, null, null, null, 0, "root");
        KnowledgeTreeNodeDto child = create(subject, root.getId(), null, null, 0, "child");
        KnowledgeTreeNodeDto updated = knowledge.update(child.getId(), update(child).name("renamed").sortOrder(9));
        assertThat(updated.getName()).isEqualTo("renamed");
        assertThat(updated.getSortOrder()).isEqualTo(9);
        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getParentId()).isEqualTo(root.getId());
        assertThat(KnowledgeNodeUpdateRequest.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .doesNotContain("getParentId", "setParentId", "parentId");
        assertVersionConflict(() -> knowledge.update(child.getId(), update(child)));
    }

    @Test
    void scenario26_updateRejectsScopeThatBreaksParentOrChildren() {
        SubjectEntity first = subject();
        SubjectEntity second = subject();
        KnowledgeTreeNodeDto root = create(first, null, null, null, 0, "root");
        KnowledgeTreeNodeDto child = create(first, root.getId(), null, null, 0, "child");
        assertRule(() -> knowledge.update(child.getId(), update(child).subjectId(second.getId().toString())));
        assertRule(() -> knowledge.update(root.getId(), update(root).subjectId(second.getId().toString())));
    }

    @Test
    void scenarios27_28_35_moveChangesOnlyTargetPlacementAndMaintainsSubtreeLevels() {
        SubjectEntity subject = subject();
        KnowledgeTreeNodeDto left = create(subject, null, null, null, 0, "left");
        KnowledgeTreeNodeDto right = create(subject, null, null, null, 0, "right");
        KnowledgeTreeNodeDto child = create(subject, left.getId(), null, null, 0, "child");
        KnowledgeTreeNodeDto grandchild = create(subject, child.getId(), null, null, 0, "grandchild");

        KnowledgeTreeNodeDto moved = knowledge.move(child.getId(), move(right.getId(), 7, child.getVersion()));
        assertThat(moved.getParentId()).isEqualTo(right.getId());
        assertThat(moved.getSortOrder()).isEqualTo(7);
        assertThat(moved.getLevelNo()).isEqualTo(2);
        assertThat(moved.getVersion()).isEqualTo(2);
        assertThat(nodeMapper.selectById(Long.valueOf(grandchild.getId())).getLevelNo()).isEqualTo(3);

        KnowledgeTreeNodeDto rootAgain = knowledge.move(child.getId(), move(null, 1, moved.getVersion()));
        assertThat(rootAgain.getParentId()).isNull();
        assertThat(rootAgain.getLevelNo()).isEqualTo(1);
        assertThat(nodeMapper.selectById(Long.valueOf(grandchild.getId())).getLevelNo()).isEqualTo(2);
    }

    @Test
    void scenarios29_30_moveRejectsSelfAndEveryDescendantCycle() {
        SubjectEntity subject = subject();
        KnowledgeTreeNodeDto root = create(subject, null, null, null, 0, "root");
        KnowledgeTreeNodeDto child = create(subject, root.getId(), null, null, 0, "child");
        KnowledgeTreeNodeDto grandchild = create(subject, child.getId(), null, null, 0, "grandchild");
        assertRule(() -> knowledge.move(root.getId(), move(root.getId(), 0, root.getVersion())));
        assertRule(() -> knowledge.move(root.getId(), move(grandchild.getId(), 0, root.getVersion())));
    }

    @Test
    void scenarios31_34_moveValidatesTargetStateScopeAndVersion() {
        SubjectEntity first = subject();
        SubjectEntity second = subject();
        KnowledgeTreeNodeDto node = create(first, null, null, null, 0, "node");
        assertThatThrownBy(() -> knowledge.move(node.getId(), move("999999999", 0, node.getVersion())))
                .isInstanceOf(ResourceNotFoundException.class);

        KnowledgeTreeNodeDto deleted = create(first, null, null, null, 0, "deleted");
        jdbc.update("UPDATE knowledge_node SET deleted=1 WHERE id=?", deleted.getId());
        assertThatThrownBy(() -> knowledge.move(node.getId(), move(deleted.getId(), 0, node.getVersion())))
                .isInstanceOf(ResourceNotFoundException.class);

        KnowledgeTreeNodeDto disabled = create(first, null, null, null, 0, "disabled");
        disabled = knowledge.disable(disabled.getId(), disable(disabled.getVersion()));
        KnowledgeTreeNodeDto finalDisabled = disabled;
        assertRule(() -> knowledge.move(node.getId(), move(finalDisabled.getId(), 0, node.getVersion())));

        KnowledgeTreeNodeDto other = create(second, null, null, null, 0, "other-subject");
        assertRule(() -> knowledge.move(node.getId(), move(other.getId(), 0, node.getVersion())));
        assertVersionConflict(() -> knowledge.move(node.getId(), move(null, 0, node.getVersion() - 1)));
    }

    @Test
    void scenarios36_41_disableIsOptimisticNonCascadingAndPreservesHistory() {
        SubjectEntity subject = subject();
        KnowledgeTreeNodeDto parent = create(subject, null, null, null, 0, "parent");
        KnowledgeTreeNodeDto child = create(subject, parent.getId(), null, null, 0, "child");
        String student = student();
        jdbc.update("INSERT INTO student_mastery(student_id,knowledge_id,mastery_score) VALUES (?,?,?)",
                student, parent.getId(), new BigDecimal("42.00"));
        jdbc.update("""
                INSERT INTO mastery_history(student_id,knowledge_id,event_type,score_before,change_value,score_after)
                VALUES (?,?, 'MANUAL_ADJUST', 40.00, 2.00, 42.00)
                """, student, parent.getId());
        int historyBefore = count("mastery_history", "knowledge_id", parent.getId());

        KnowledgeTreeNodeDto disabled = knowledge.disable(parent.getId(), disable(parent.getVersion()));
        assertThat(disabled.getEnabled()).isFalse();
        assertThat(disabled.getVersion()).isEqualTo(parent.getVersion() + 1);
        KnowledgeNodeEntity stored = nodeMapper.selectById(Long.valueOf(parent.getId()));
        assertThat(stored.getDeleted()).isFalse();
        assertThat(nodeMapper.selectById(Long.valueOf(child.getId())).getEnabled()).isTrue();
        assertThat(count("student_mastery", "knowledge_id", parent.getId())).isOne();
        assertThat(count("mastery_history", "knowledge_id", parent.getId())).isEqualTo(historyBefore);
        assertThat(mastery.history(student, parent.getId(), 1, 20).getItems()).hasSize(1);
        assertVersionConflict(() -> knowledge.disable(parent.getId(), disable(parent.getVersion())));
    }

    @Test
    void scenarios42_44_disabledNodeIsRejectedByFutureAiSelection() {
        SubjectEntity subject = subject();
        KnowledgeTreeNodeDto node = create(subject, null, null, null, 0, "ai-target");
        knowledge.disable(node.getId(), disable(node.getVersion()));
        NormalizedStudyPlanGenerationRequest request = new NormalizedStudyPlanGenerationRequest("1",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), 30,
                List.of(subject.getId().toString()), List.of(node.getId()), false, false, "1");
        assertRule(() -> generationContexts.load(request));
    }

    @Test
    void scenarios45_47_knowledgeWritesDoNotCreateOrDeleteMasteryEvidence() {
        SubjectEntity subject = subject();
        KnowledgeTreeNodeDto node = create(subject, null, null, null, 0, "isolated");
        assertThat(count("student_mastery", "knowledge_id", node.getId())).isZero();
        assertThat(count("mastery_history", "knowledge_id", node.getId())).isZero();
        node = knowledge.update(node.getId(), update(node).name("updated"));
        node = knowledge.move(node.getId(), move(null, 2, node.getVersion()));
        knowledge.disable(node.getId(), disable(node.getVersion()));
        assertThat(count("student_mastery", "knowledge_id", node.getId())).isZero();
        assertThat(count("mastery_history", "knowledge_id", node.getId())).isZero();
    }

    @Test
    void scenarios48_54_allFiveGeneratedOperationsAreImplementedAndIdsAreJsonStrings() throws Exception {
        SubjectEntity subject = subject();
        KnowledgeNodeCreateRequest create = request(subject, null, null, null, 0, "http");
        String createdBody = mvc.perform(post("/api/v1/knowledge/nodes").contentType("application/json")
                        .content(json.writeValueAsBytes(create)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.version").value(1)).andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(createdBody).path("data");
        String id = created.path("id").asText();

        mvc.perform(get("/api/v1/knowledge/tree").param("subjectId", subject.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").isString());
        KnowledgeNodeUpdateRequest update = new KnowledgeNodeUpdateRequest()
                .subjectId(subject.getId().toString()).nodeCode(created.path("nodeCode").asText())
                .name("http-updated").nodeType("POINT").sortOrder(1).version(1);
        mvc.perform(put("/api/v1/knowledge/nodes/{knowledgeId}", id).contentType("application/json")
                        .content(json.writeValueAsBytes(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(post("/api/v1/knowledge/nodes/{knowledgeId}/move", id).contentType("application/json")
                        .content(json.writeValueAsBytes(move(null, 2, 2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3));
        mvc.perform(post("/api/v1/knowledge/nodes/{knowledgeId}/disable", id).contentType("application/json")
                        .content(json.writeValueAsBytes(disable(3))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.version").value(4));
    }

    private KnowledgeTreeNodeDto create(SubjectEntity subject, String parentId, StageEntity stage, GradeEntity grade,
            int sortOrder, String name) {
        return knowledge.create(request(subject, parentId, stage, grade, sortOrder, name));
    }

    private KnowledgeNodeCreateRequest request(SubjectEntity subject, String parentId, StageEntity stage,
            GradeEntity grade, int sortOrder, String name) {
        return new KnowledgeNodeCreateRequest()
                .parentId(parentId)
                .stageId(stage == null ? null : stage.getId().toString())
                .gradeId(grade == null ? null : grade.getId().toString())
                .subjectId(subject.getId().toString())
                .nodeCode("K-" + UUID.randomUUID())
                .name(name)
                .nodeType("POINT")
                .sortOrder(sortOrder);
    }

    private KnowledgeNodeUpdateRequest update(KnowledgeTreeNodeDto node) {
        return new KnowledgeNodeUpdateRequest()
                .stageId(node.getStageId()).gradeId(node.getGradeId()).subjectId(node.getSubjectId())
                .nodeCode(node.getNodeCode()).name(node.getName()).nodeType(node.getNodeType())
                .difficulty(node.getDifficulty()).description(node.getDescription()).keywords(node.getKeywords())
                .sortOrder(node.getSortOrder()).version(node.getVersion());
    }

    private KnowledgeNodeMoveRequest move(String parentId, int sortOrder, int version) {
        return new KnowledgeNodeMoveRequest().parentId(parentId).sortOrder(sortOrder).version(version);
    }

    private KnowledgeNodeDisableRequest disable(int version) {
        return new KnowledgeNodeDisableRequest().version(version);
    }

    private SubjectEntity subject() {
        SubjectEntity subject = new SubjectEntity();
        subject.setCode("S-" + UUID.randomUUID().toString().substring(0, 24));
        subject.setName("Stage12 subject");
        subject.setSortOrder(0);
        subject.setEnabled(true);
        subjects.insert(subject);
        return subject;
    }

    private SubjectEntity fakeSubject() {
        SubjectEntity subject = new SubjectEntity();
        subject.setId(999999999L);
        return subject;
    }

    private StageEntity fakeStage() {
        StageEntity stage = new StageEntity();
        stage.setId(999999999L);
        return stage;
    }

    private GradeEntity fakeGrade() {
        GradeEntity grade = new GradeEntity();
        grade.setId(999999999L);
        return grade;
    }

    private Scope scope() {
        StageEntity stage = stages.selectList(null).stream().filter(StageEntity::getEnabled).findFirst().orElseThrow();
        GradeEntity grade = grades.selectList(null).stream()
                .filter(item -> item.getStageId().equals(stage.getId()) && Boolean.TRUE.equals(item.getEnabled()))
                .findFirst().orElseThrow();
        return new Scope(stage, grade);
    }

    private Scope anotherScope(Long excludedStage) {
        StageEntity stage = stages.selectList(null).stream()
                .filter(item -> !item.getId().equals(excludedStage) && Boolean.TRUE.equals(item.getEnabled()))
                .findFirst().orElseThrow();
        GradeEntity grade = grades.selectList(null).stream()
                .filter(item -> item.getStageId().equals(stage.getId()) && Boolean.TRUE.equals(item.getEnabled()))
                .findFirst().orElseThrow();
        return new Scope(stage, grade);
    }

    private String student() {
        Scope scope = scope();
        return students.create(new StudentCreate().name("Stage12 student")
                .currentStageId(scope.stage().getId().toString())
                .currentGradeId(scope.grade().getId().toString())).getId();
    }

    private int count(String table, String column, String value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Integer.class,
                Long.valueOf(value));
    }

    private void assertRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private void assertVersionConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
            assertThat(error.getCode()).isEqualTo("DATA_VERSION_CONFLICT");
        });
    }

    private record Scope(StageEntity stage, GradeEntity grade) { }
}

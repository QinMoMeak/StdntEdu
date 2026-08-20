package com.stdntedu.ai.analysis.generation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.stdntedu.ai.analysis.generation.model.NormalizedStudyPlanGenerationRequest;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationContext;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.validation.IdConverter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StudyPlanGenerationContextLoader {
    private static final int MAX_REFERENCES_PER_TYPE = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final IdConverter ids;

    public StudyPlanGenerationContextLoader(NamedParameterJdbcTemplate jdbc, IdConverter ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    public StudyPlanGenerationContext load(NormalizedStudyPlanGenerationRequest request) {
        Long studentId = ids.toLong(request.studentId());
        List<Long> subjectIds = ids.toLongs(request.subjectIds());
        List<Long> knowledgeIds = ids.toLongs(request.targetKnowledgeIds());
        validateSubjects(subjectIds);
        validateKnowledge(knowledgeIds, subjectIds);
        MapSqlParameterSource student = new MapSqlParameterSource("studentId", studentId);
        List<String> resources = request.includeResources() ? jdbc.queryForList("""
                SELECT CAST(sra.resource_id AS CHAR) FROM student_resource_assignment sra
                JOIN learning_resource lr ON lr.id=sra.resource_id AND lr.deleted=0
                WHERE sra.student_id=:studentId AND sra.status<>'ARCHIVED'
                ORDER BY sra.id LIMIT 200
                """, student, String.class) : List.of();
        List<String> wrongQuestions = request.includeWrongQuestionReview() ? jdbc.queryForList("""
                SELECT CAST(id AS CHAR) FROM wrong_question
                WHERE student_id=:studentId AND deleted=0 AND status<>'ARCHIVED'
                ORDER BY id LIMIT 200
                """, student, String.class) : List.of();
        List<String> exams = jdbc.queryForList("""
                SELECT CAST(id AS CHAR) FROM exam
                WHERE student_id=:studentId AND deleted=0
                ORDER BY id LIMIT 200
                """, student, String.class);
        return new StudyPlanGenerationContext(request.subjectIds(), request.targetKnowledgeIds(),
                resources, wrongQuestions, exams);
    }

    private void validateSubjects(List<Long> subjectIds) {
        if (subjectIds.isEmpty()) return;
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", subjectIds);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subject WHERE id IN (:ids) AND enabled=1", parameters, Integer.class);
        if (count == null || count != subjectIds.size()) throw rule("subject selection contains an unavailable subject");
    }

    private void validateKnowledge(List<Long> knowledgeIds, List<Long> subjectIds) {
        if (knowledgeIds.isEmpty()) return;
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", knowledgeIds);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,subject_id FROM knowledge_node WHERE id IN (:ids) AND enabled=1 AND deleted=0", parameters);
        if (rows.size() != knowledgeIds.size()) throw rule("knowledge selection contains an unavailable node");
        if (!subjectIds.isEmpty()) {
            Set<Long> allowed = Set.copyOf(subjectIds);
            Set<Long> actual = rows.stream().map(row -> ((Number) row.get("subject_id")).longValue())
                    .collect(Collectors.toSet());
            if (!allowed.containsAll(actual)) throw rule("knowledge selection is outside the selected subjects");
        }
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

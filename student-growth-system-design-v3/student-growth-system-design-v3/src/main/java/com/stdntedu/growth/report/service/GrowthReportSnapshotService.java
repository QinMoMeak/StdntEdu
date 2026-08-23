package com.stdntedu.growth.report.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.stdntedu.generated.model.GrowthReportSnapshotDto;
import com.stdntedu.generated.model.ReportType;
import com.stdntedu.growth.report.entity.GrowthReportEntity;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GrowthReportSnapshotService {
    private final JdbcTemplate jdbc;
    private final SystemTimezoneProvider time;
    private final TransactionTemplate snapshot;

    public GrowthReportSnapshotService(JdbcTemplate jdbc, SystemTimezoneProvider time,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.time = time;
        this.snapshot = new TransactionTemplate(transactions);
        this.snapshot.setReadOnly(true);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public GrowthReportSnapshotDto build(GrowthReportEntity report) {
        return snapshot.execute(status -> collect(report));
    }

    private GrowthReportSnapshotDto collect(GrowthReportEntity report) {
        Long studentId = report.getStudentId();
        LocalDateTime start = report.getStartDate().atStartOfDay();
        LocalDateTime end = report.getEndDate().plusDays(1).atStartOfDay();
        Map<String, Object> student = jdbc.queryForObject("""
                SELECT student_code,name,birthday,school FROM student WHERE id=? AND deleted=0
                """, (rs, row) -> map("studentCode", rs.getString(1), "name", rs.getString(2),
                        "birthday", rs.getObject(3), "school", rs.getString(4)), studentId);

        Map<String, Object> scores = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT e.id),COUNT(sr.id),COALESCE(AVG(sr.score/sr.full_score),0)
                  FROM exam e LEFT JOIN score_record sr ON sr.exam_id=e.id AND sr.deleted=0
                 WHERE e.student_id=? AND e.deleted=0 AND e.exam_date BETWEEN ? AND ?
                """, (rs, row) -> map("examCount", rs.getInt(1), "scoreRecordCount", rs.getInt(2),
                        "averageScoreRate", decimal(rs.getBigDecimal(3))), studentId,
                report.getStartDate(), report.getEndDate());
        scores.put("subjects", jdbc.query("""
                SELECT CAST(sr.subject_id AS CHAR),s.name,COUNT(*),AVG(sr.score/sr.full_score)
                  FROM score_record sr JOIN exam e ON e.id=sr.exam_id JOIN subject s ON s.id=sr.subject_id
                 WHERE sr.student_id=? AND sr.deleted=0 AND e.deleted=0 AND e.exam_date BETWEEN ? AND ?
                 GROUP BY sr.subject_id,s.name ORDER BY sr.subject_id
                """, (rs, row) -> map("subjectId", rs.getString(1), "subjectName", rs.getString(2),
                        "scoreCount", rs.getInt(3), "averageScoreRate", decimal(rs.getBigDecimal(4))),
                studentId, report.getStartDate(), report.getEndDate()));

        Map<String, Object> mastery = jdbc.queryForObject("""
                SELECT COUNT(*),COALESCE(AVG(mastery_score),0),
                       SUM(CASE WHEN mastery_score>=80 THEN 1 ELSE 0 END),
                       SUM(CASE WHEN mastery_score<60 THEN 1 ELSE 0 END),
                       SUM(CASE WHEN next_review_time IS NOT NULL AND next_review_time<? THEN 1 ELSE 0 END)
                  FROM student_mastery WHERE student_id=?
                """, (rs, row) -> map("knowledgeCount", rs.getInt(1), "averageScore", decimal(rs.getBigDecimal(2)),
                        "strongCount", rs.getInt(3), "weakCount", rs.getInt(4), "dueReviewCount", rs.getInt(5)),
                end, studentId);

        Map<String, Object> wrong = jdbc.queryForObject("""
                SELECT COUNT(*),SUM(CASE WHEN status='ARCHIVED' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN next_review_time IS NOT NULL AND next_review_time<? THEN 1 ELSE 0 END),
                       COALESCE(SUM(review_count),0)
                  FROM wrong_question
                 WHERE student_id=? AND deleted=0
                   AND COALESCE(occurred_date,DATE(create_time)) BETWEEN ? AND ?
                """, (rs, row) -> map("totalCount", rs.getInt(1), "archivedCount", rs.getInt(2),
                        "dueReviewCount", rs.getInt(3), "reviewCount", rs.getInt(4)),
                end, studentId, report.getStartDate(), report.getEndDate());
        wrong.put("statusDistribution", jdbc.query("""
                SELECT status,COUNT(*) FROM wrong_question
                 WHERE student_id=? AND deleted=0
                   AND COALESCE(occurred_date,DATE(create_time)) BETWEEN ? AND ?
                 GROUP BY status ORDER BY status
                """, (rs, row) -> map("status", rs.getString(1), "count", rs.getInt(2)),
                studentId, report.getStartDate(), report.getEndDate()));

        Map<String, Object> learning = jdbc.queryForObject("""
                SELECT COUNT(*),COALESCE(SUM(duration_seconds),0)
                  FROM study_log WHERE student_id=? AND deleted=0 AND study_date BETWEEN ? AND ?
                """, (rs, row) -> map("studyLogCount", rs.getInt(1), "studyDurationSeconds", rs.getLong(2)),
                studentId, report.getStartDate(), report.getEndDate());
        Map<String, Object> resource = jdbc.queryForObject("""
                SELECT COUNT(*),COALESCE(SUM(duration_seconds),0),SUM(CASE WHEN completed=1 THEN 1 ELSE 0 END)
                  FROM resource_history WHERE student_id=? AND create_time>=? AND create_time<?
                """, (rs, row) -> map("historyCount", rs.getInt(1), "durationSeconds", rs.getLong(2),
                        "completedCount", rs.getInt(3)), studentId, start, end);
        Map<String, Object> tasks = jdbc.queryForObject("""
                SELECT COUNT(*),SUM(CASE WHEN spt.status='COMPLETED' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN spt.status NOT IN('COMPLETED','SKIPPED','CANCELLED') THEN 1 ELSE 0 END)
                  FROM study_plan_task spt JOIN study_plan sp ON sp.id=spt.study_plan_id
                 WHERE sp.student_id=? AND sp.deleted=0 AND spt.task_date BETWEEN ? AND ?
                """, (rs, row) -> map("taskCount", rs.getInt(1), "completedCount", rs.getInt(2),
                        "unfinishedCount", rs.getInt(3)), studentId, report.getStartDate(), report.getEndDate());
        learning.put("resources", resource);
        learning.put("tasks", tasks);

        long eventCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM growth_event
                 WHERE student_id=? AND deleted=0 AND event_date BETWEEN ? AND ?
                """, Long.class, studentId, report.getStartDate(), report.getEndDate());
        List<Map<String, Object>> eventItems = jdbc.query("""
                SELECT event_type,title,event_date FROM growth_event
                 WHERE student_id=? AND deleted=0 AND event_date BETWEEN ? AND ?
                 ORDER BY event_date,id LIMIT 100
                """, (rs, row) -> map("eventType", rs.getString(1), "title", rs.getString(2),
                        "eventDate", rs.getObject(3)), studentId, report.getStartDate(), report.getEndDate());
        Map<String, Object> growthEvents = map("totalCount", eventCount, "items", eventItems,
                "truncated", eventCount > eventItems.size());

        List<String> recommendations = new ArrayList<>();
        if (number(mastery, "weakCount") > 0) recommendations.add("优先复习掌握度低于 60 的知识点。");
        if (number(wrong, "dueReviewCount") > 0) recommendations.add("完成已到期的错题复习。");
        if (number(tasks, "unfinishedCount") > 0) recommendations.add("按计划完成尚未结束的学习任务。");

        GrowthReportSnapshotDto result = new GrowthReportSnapshotDto();
        result.setSchemaVersion(1);
        result.setGenerationVersion("1.0");
        result.setStudentId(studentId.toString());
        result.setReportType(ReportType.fromValue(report.getReportType()));
        result.setPeriodStart(report.getStartDate());
        result.setPeriodEnd(report.getEndDate());
        result.setGeneratedAt(time.offsetDateTime());
        result.setStudent(student);
        result.setScores(scores);
        result.setMastery(mastery);
        result.setWrongQuestions(wrong);
        result.setLearning(learning);
        result.setGrowthEvents(growthEvents);
        result.setRecommendations(recommendations);
        return result;
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    private static long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
}

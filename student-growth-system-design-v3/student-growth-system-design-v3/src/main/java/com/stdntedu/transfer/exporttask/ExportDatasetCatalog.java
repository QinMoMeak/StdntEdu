package com.stdntedu.transfer.exporttask;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.stdntedu.generated.model.ExportType;
import org.springframework.stereotype.Component;

@Component
public class ExportDatasetCatalog {
    public List<Spec> specs(List<ExportType> requested, Long studentId, LocalDate start, LocalDate end,
            boolean includeDeleted, boolean includeAi) {
        List<ExportType> types = requested.contains(ExportType.FULL_DATA)
                ? new ArrayList<>(List.of(ExportType.STUDENT, ExportType.SCORE, ExportType.WRONG_QUESTION,
                        ExportType.WRONG_REVIEW, ExportType.LEARNING_RESOURCE, ExportType.RESOURCE_HISTORY,
                        ExportType.STUDY_LOG, ExportType.KNOWLEDGE, ExportType.MASTERY, ExportType.STUDY_PLAN,
                        ExportType.GROWTH_EVENT, ExportType.REPORT))
                : requested;
        if (requested.contains(ExportType.FULL_DATA) && includeAi) types.add(ExportType.AI_ANALYSIS);
        return types.stream().map(type -> spec(type, studentId, start, end, includeDeleted)).toList();
    }

    private Spec spec(ExportType type, Long studentId, LocalDate start, LocalDate end, boolean includeDeleted) {
        String deleted = includeDeleted ? "" : " AND %s.deleted=0";
        String time = dateFilter(start, end, "create_time");
        List<Object> dates = dateArgs(start, end);
        return switch (type) {
            case STUDENT -> new Spec(type, cols("id,studentCode,name,birthday,school,currentStageId,currentGradeId,remark,deleted,version,createdAt,updatedAt"),
                    "SELECT CAST(s.id AS CHAR) id,s.student_code studentCode,s.name,s.birthday,s.school,CAST(s.current_stage_id AS CHAR) currentStageId,CAST(s.current_grade_id AS CHAR) currentGradeId,s.remark,s.deleted,s.version,s.create_time createdAt,s.update_time updatedAt FROM student s WHERE s.id=?" + deleted.formatted("s") + dateFilter(start,end,"s.create_time"), args(studentId, dates));
            case SCORE -> new Spec(type, cols("id,examId,studentId,subjectId,score,fullScore,classRank,gradeRank,classSize,gradeSize,remark,createdAt,updatedAt"),
                    "SELECT CAST(sr.id AS CHAR) id,CAST(sr.exam_id AS CHAR) examId,CAST(sr.student_id AS CHAR) studentId,CAST(sr.subject_id AS CHAR) subjectId,sr.score,sr.full_score fullScore,sr.class_rank classRank,sr.grade_rank gradeRank,sr.class_size classSize,sr.grade_size gradeSize,sr.remark,sr.create_time createdAt,sr.update_time updatedAt FROM score_record sr WHERE sr.student_id=?" + deleted.formatted("sr") + dateFilter(start,end,"sr.create_time") + " ORDER BY sr.id", args(studentId, dates));
            case WRONG_QUESTION -> new Spec(type, cols("id,studentId,subjectId,examId,sourceType,sourceName,questionType,questionText,studentAnswer,correctAnswer,errorType,difficulty,status,reviewStage,reviewCount,nextReviewTime,occurredDate,remark,createdAt,updatedAt"),
                    "SELECT CAST(w.id AS CHAR) id,CAST(w.student_id AS CHAR) studentId,CAST(w.subject_id AS CHAR) subjectId,CAST(w.exam_id AS CHAR) examId,w.source_type sourceType,w.source_name sourceName,w.question_type questionType,w.question_text questionText,w.student_answer studentAnswer,w.correct_answer correctAnswer,w.error_type errorType,w.difficulty,w.status,w.review_stage reviewStage,w.review_count reviewCount,w.next_review_time nextReviewTime,w.occurred_date occurredDate,w.remark,w.create_time createdAt,w.update_time updatedAt FROM wrong_question w WHERE w.student_id=?" + deleted.formatted("w") + dateFilter(start,end,"w.create_time") + " ORDER BY w.id", args(studentId, dates));
            case WRONG_REVIEW -> new Spec(type, cols("id,wrongQuestionId,reviewTime,result,score,durationSeconds,studentAnswer,remark,nextReviewTime,createdAt"),
                    "SELECT CAST(r.id AS CHAR) id,CAST(r.wrong_question_id AS CHAR) wrongQuestionId,r.review_time reviewTime,r.result,r.score,r.duration_seconds durationSeconds,r.student_answer studentAnswer,r.remark,r.next_review_time nextReviewTime,r.create_time createdAt FROM wrong_review r JOIN wrong_question w ON w.id=r.wrong_question_id WHERE w.student_id=?" + (includeDeleted ? "" : " AND w.deleted=0") + dateFilter(start,end,"r.create_time") + " ORDER BY r.id", args(studentId, dates));
            case LEARNING_RESOURCE -> new Spec(type, cols("id,resourceCode,title,resourceType,sourceType,sourceUrl,subjectId,durationSeconds,difficulty,status,description,tags,deleted,version,createdAt,updatedAt"),
                    "SELECT CAST(r.id AS CHAR) id,r.resource_code resourceCode,r.title,r.resource_type resourceType,r.source_type sourceType,r.source_url sourceUrl,CAST(r.subject_id AS CHAR) subjectId,r.duration_seconds durationSeconds,r.difficulty,r.status,r.description,r.tags,r.deleted,r.version,r.create_time createdAt,r.update_time updatedAt FROM learning_resource r WHERE 1=1" + deleted.formatted("r") + dateFilter(start,end,"r.create_time") + " ORDER BY r.id", dates);
            case RESOURCE_HISTORY -> new Spec(type, cols("id,studentId,resourceId,startTime,endTime,durationSeconds,progressPercent,completed,note,createdAt"),
                    "SELECT CAST(h.id AS CHAR) id,CAST(h.student_id AS CHAR) studentId,CAST(h.resource_id AS CHAR) resourceId,h.start_time startTime,h.end_time endTime,h.duration_seconds durationSeconds,h.progress_percent progressPercent,h.completed,h.note,h.create_time createdAt FROM resource_history h WHERE h.student_id=?" + dateFilter(start,end,"h.create_time") + " ORDER BY h.id", args(studentId, dates));
            case STUDY_LOG -> new Spec(type, cols("id,studentId,subjectId,studyDate,durationSeconds,content,remark,deleted,version,createdAt,updatedAt"),
                    "SELECT CAST(l.id AS CHAR) id,CAST(l.student_id AS CHAR) studentId,CAST(l.subject_id AS CHAR) subjectId,l.study_date studyDate,l.duration_seconds durationSeconds,l.content,l.remark,l.deleted,l.version,l.create_time createdAt,l.update_time updatedAt FROM study_log l WHERE l.student_id=?" + deleted.formatted("l") + dateFilter(start,end,"l.create_time") + " ORDER BY l.id", args(studentId, dates));
            case KNOWLEDGE -> new Spec(type, cols("id,parentId,nodeCode,name,nodeType,stageId,gradeId,subjectId,levelNo,sortOrder,difficulty,description,keywords,enabled,deleted,version,createdAt,updatedAt"),
                    "SELECT CAST(k.id AS CHAR) id,CAST(k.parent_id AS CHAR) parentId,k.node_code nodeCode,k.name,k.node_type nodeType,CAST(k.stage_id AS CHAR) stageId,CAST(k.grade_id AS CHAR) gradeId,CAST(k.subject_id AS CHAR) subjectId,k.level_no levelNo,k.sort_order sortOrder,k.difficulty,k.description,k.keywords,k.enabled,k.deleted,k.version,k.create_time createdAt,k.update_time updatedAt FROM knowledge_node k WHERE 1=1" + deleted.formatted("k") + dateFilter(start,end,"k.create_time") + " ORDER BY k.id", dates);
            case MASTERY -> new Spec(type, cols("id,studentId,knowledgeId,masteryScore,correctCount,wrongCount,partialCount,reviewCount,evidenceCount,lastPracticeTime,lastReviewTime,nextReviewTime,manualLocked,version,createdAt,updatedAt"),
                    "SELECT CAST(m.id AS CHAR) id,CAST(m.student_id AS CHAR) studentId,CAST(m.knowledge_id AS CHAR) knowledgeId,m.mastery_score masteryScore,m.correct_count correctCount,m.wrong_count wrongCount,m.partial_count partialCount,m.review_count reviewCount,m.evidence_count evidenceCount,m.last_practice_time lastPracticeTime,m.last_review_time lastReviewTime,m.next_review_time nextReviewTime,m.manual_locked manualLocked,m.version,m.create_time createdAt,m.update_time updatedAt FROM student_mastery m WHERE m.student_id=?" + dateFilter(start,end,"m.create_time") + " ORDER BY m.id", args(studentId, dates));
            case AI_ANALYSIS -> new Spec(type, cols("id,studentId,businessType,businessId,aiModelId,status,inputSummary,result,errorCode,errorMessage,promptTokens,completionTokens,durationMs,startedAt,finishedAt,createdAt"),
                    "SELECT CAST(a.id AS CHAR) id,CAST(a.student_id AS CHAR) studentId,a.business_type businessType,CAST(a.business_id AS CHAR) businessId,CAST(a.ai_model_id AS CHAR) aiModelId,a.status,a.input_summary inputSummary,a.result_json result,a.error_code errorCode,a.error_message errorMessage,a.prompt_tokens promptTokens,a.completion_tokens completionTokens,a.duration_ms durationMs,a.started_time startedAt,a.finished_time finishedAt,a.create_time createdAt FROM ai_analysis a WHERE a.student_id=?" + dateFilter(start,end,"a.create_time") + " ORDER BY a.id", args(studentId, dates));
            case STUDY_PLAN -> new Spec(type, cols("id,studentId,title,planType,startDate,endDate,status,sourceAnalysisId,dailyAvailableMinutes,description,deleted,version,createdAt,updatedAt"),
                    "SELECT CAST(p.id AS CHAR) id,CAST(p.student_id AS CHAR) studentId,p.title,p.plan_type planType,p.start_date startDate,p.end_date endDate,p.status,CAST(p.source_analysis_id AS CHAR) sourceAnalysisId,p.daily_available_minutes dailyAvailableMinutes,p.description,p.deleted,p.version,p.create_time createdAt,p.update_time updatedAt FROM study_plan p WHERE p.student_id=?" + deleted.formatted("p") + dateFilter(start,end,"p.create_time") + " ORDER BY p.id", args(studentId, dates));
            case GROWTH_EVENT -> new Spec(type, cols("id,studentId,eventType,title,eventDate,description,tags,deleted,version,createdAt,updatedAt"),
                    "SELECT CAST(g.id AS CHAR) id,CAST(g.student_id AS CHAR) studentId,g.event_type eventType,g.title,g.event_date eventDate,g.description,g.tags,g.deleted,g.version,g.create_time createdAt,g.update_time updatedAt FROM growth_event g WHERE g.student_id=?" + deleted.formatted("g") + dateFilter(start,end,"g.create_time") + " ORDER BY g.id", args(studentId, dates));
            case REPORT -> new Spec(type, cols("id,studentId,reportType,title,startDate,endDate,generationType,status,aiAnalysisId,contentMarkdown,deleted,version,createdAt,updatedAt"),
                    "SELECT CAST(r.id AS CHAR) id,CAST(r.student_id AS CHAR) studentId,r.report_type reportType,r.title,r.start_date startDate,r.end_date endDate,r.generation_type generationType,r.status,CAST(r.ai_analysis_id AS CHAR) aiAnalysisId,r.content_markdown contentMarkdown,r.deleted,r.version,r.create_time createdAt,r.update_time updatedAt FROM growth_report r WHERE r.student_id=?" + deleted.formatted("r") + dateFilter(start,end,"r.create_time") + " ORDER BY r.id", args(studentId, dates));
            case FULL_DATA -> throw new IllegalArgumentException("FULL_DATA must be expanded");
        };
    }

    private String dateFilter(LocalDate start, LocalDate end, String column) {
        return (start == null ? "" : " AND " + column + ">=?")
                + (end == null ? "" : " AND " + column + "<?");
    }

    private List<Object> dateArgs(LocalDate start, LocalDate end) {
        List<Object> values = new ArrayList<>();
        if (start != null) values.add(start.atStartOfDay());
        if (end != null) values.add(end.plusDays(1).atStartOfDay());
        return values;
    }

    private List<Object> args(Object first, List<Object> rest) {
        List<Object> values = new ArrayList<>();
        values.add(first);
        values.addAll(rest);
        return values;
    }

    private List<String> cols(String value) { return List.of(value.split(",")); }
    public record Spec(ExportType type, List<String> columns, String sql, List<Object> args) { }
}

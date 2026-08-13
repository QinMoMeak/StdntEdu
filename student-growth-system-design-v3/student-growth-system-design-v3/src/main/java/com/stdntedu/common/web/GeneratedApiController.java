package com.stdntedu.common.web;

import java.time.OffsetDateTime;

import com.stdntedu.base.service.BaseDataService;
import com.stdntedu.generated.api.DefaultApi;
import com.stdntedu.generated.model.AcademicTermCreateRequest;
import com.stdntedu.generated.model.AcademicTermUpdateRequest;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ExamType;
import com.stdntedu.generated.model.ExamUpdate;
import com.stdntedu.generated.model.InlineObject13;
import com.stdntedu.generated.model.InlineObject14;
import com.stdntedu.generated.model.InlineObject15;
import com.stdntedu.generated.model.InlineObject16;
import com.stdntedu.generated.model.InlineObject17;
import com.stdntedu.generated.model.InlineObject18;
import com.stdntedu.generated.model.InlineObject21;
import com.stdntedu.generated.model.InlineObject22;
import com.stdntedu.generated.model.InlineObject23;
import com.stdntedu.generated.model.InlineObject24;
import com.stdntedu.generated.model.InlineObject25;
import com.stdntedu.generated.model.InlineObject26;
import com.stdntedu.generated.model.InlineObject27;
import com.stdntedu.generated.model.InlineObject28;
import com.stdntedu.generated.model.InlineObject29;
import com.stdntedu.generated.model.ReviewCreate;
import com.stdntedu.generated.model.MasteryAdjustRequest;
import com.stdntedu.generated.model.ScorePageResponse;
import com.stdntedu.generated.model.ScoreTrendResponse;
import com.stdntedu.generated.model.ScorePageResponseAllOfData;
import com.stdntedu.score.service.ExamService;
import com.stdntedu.knowledge.mastery.service.MasteryService;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudentUpdate;
import com.stdntedu.generated.model.WrongCreate;
import com.stdntedu.generated.model.WrongUpdate;
import com.stdntedu.student.service.AcademicTermService;
import com.stdntedu.student.service.StudentService;
import com.stdntedu.wrongquestion.service.WrongQuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GeneratedApiController implements DefaultApi {
    private final BaseDataService baseData;
    private final StudentService students;
    private final AcademicTermService terms;
    private final ExamService exams;
    private final WrongQuestionService wrongQuestions;
    private final MasteryService mastery;
    private final RequestIdProvider requestIds;

    public GeneratedApiController(BaseDataService baseData, StudentService students, AcademicTermService terms,
            ExamService exams, WrongQuestionService wrongQuestions, MasteryService mastery,
            RequestIdProvider requestIds) {
        this.baseData = baseData;
        this.students = students;
        this.terms = terms;
        this.exams = exams;
        this.wrongQuestions = wrongQuestions;
        this.mastery = mastery;
        this.requestIds = requestIds;
    }

    @Override public ResponseEntity<InlineObject13> listStages(String stageId, Boolean enabledOnly) {
        return ResponseEntity.ok(new InlineObject13().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(baseData.listStages(stageId, !Boolean.FALSE.equals(enabledOnly))));
    }
    @Override public ResponseEntity<InlineObject14> listGrades(String stageId, Boolean enabledOnly) {
        return ResponseEntity.ok(new InlineObject14().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(baseData.listGrades(stageId, !Boolean.FALSE.equals(enabledOnly))));
    }
    @Override public ResponseEntity<InlineObject15> listSubjects(Boolean enabledOnly) {
        return ResponseEntity.ok(new InlineObject15().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(baseData.listSubjects(!Boolean.FALSE.equals(enabledOnly))));
    }
    @Override public ResponseEntity<InlineObject16> listDictionaryItems(String dictType, Boolean enabledOnly) {
        return ResponseEntity.ok(new InlineObject16().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(baseData.listDictionaryItems(dictType, !Boolean.FALSE.equals(enabledOnly))));
    }
    @Override public ResponseEntity<InlineObject24> listStudents() {
        return ResponseEntity.ok(new InlineObject24().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(students.list()));
    }
    @Override public ResponseEntity<InlineObject25> createStudent(StudentCreate request) {
        return ResponseEntity.status(201).body(new InlineObject25().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(students.create(request)));
    }
    @Override public ResponseEntity<InlineObject25> getStudent(String studentId) {
        return ResponseEntity.ok(new InlineObject25().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(students.get(studentId)));
    }
    @Override public ResponseEntity<InlineObject25> updateStudent(String studentId, StudentUpdate request) {
        return ResponseEntity.ok(new InlineObject25().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(students.update(studentId, request)));
    }
    @Override public ResponseEntity<InlineObject17> listAcademicTerms(String studentId, Boolean currentOnly) {
        return ResponseEntity.ok(new InlineObject17().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(terms.list(studentId, Boolean.TRUE.equals(currentOnly))));
    }
    @Override public ResponseEntity<InlineObject18> createAcademicTerm(AcademicTermCreateRequest request) {
        return ResponseEntity.status(201).body(new InlineObject18().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(terms.create(request)));
    }
    @Override public ResponseEntity<InlineObject18> updateAcademicTerm(String termId, AcademicTermUpdateRequest request) {
        return ResponseEntity.ok(new InlineObject18().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(terms.update(termId, request)));
    }
    @Override public ResponseEntity<InlineObject26> createExam(ExamCreate request) {
        return ResponseEntity.status(201).body(new InlineObject26().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(exams.create(request)));
    }
    @Override public ResponseEntity<InlineObject26> getExam(String examId) {
        return ResponseEntity.ok(new InlineObject26().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(exams.get(examId)));
    }
    @Override public ResponseEntity<InlineObject26> updateExam(String examId, ExamUpdate request) {
        return ResponseEntity.ok(new InlineObject26().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(exams.update(examId, request)));
    }
    @Override public ResponseEntity<Void> deleteExam(String examId) {
        exams.delete(examId);
        return ResponseEntity.noContent().build();
    }
    @Override public ResponseEntity<ScorePageResponse> listScores(String studentId, String academicTermId,
            String subjectId, ExamType examType, java.time.LocalDate startDate, java.time.LocalDate endDate,
            String keyword, Integer page, Integer pageSize) {
        ScorePageResponseAllOfData result = exams.listScores(studentId, academicTermId, subjectId, examType,
                startDate, endDate, keyword, page, pageSize);
        return ResponseEntity.ok(new ScorePageResponse().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(result));
    }
    @Override public ResponseEntity<ScoreTrendResponse> getScoreTrends(String studentId, String subjectId,
            String academicTermId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        return ResponseEntity.ok(new ScoreTrendResponse().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(exams.trends(studentId, subjectId, academicTermId, startDate, endDate)));
    }
    @Override public ResponseEntity<InlineObject27> listWrongQuestions(String studentId, Integer page, Integer pageSize) {
        return ResponseEntity.ok(new InlineObject27().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(wrongQuestions.list(studentId, page, pageSize)));
    }
    @Override public ResponseEntity<InlineObject28> createWrongQuestion(WrongCreate request) {
        return ResponseEntity.status(201).body(new InlineObject28().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(wrongQuestions.create(request)));
    }
    @Override public ResponseEntity<InlineObject28> getWrongQuestion(String wrongQuestionId) {
        return ResponseEntity.ok(new InlineObject28().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(wrongQuestions.get(wrongQuestionId)));
    }
    @Override public ResponseEntity<InlineObject28> updateWrongQuestion(String wrongQuestionId, WrongUpdate request) {
        return ResponseEntity.ok(new InlineObject28().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(wrongQuestions.update(wrongQuestionId, request)));
    }
    @Override public ResponseEntity<Void> deleteWrongQuestion(String wrongQuestionId) { wrongQuestions.delete(wrongQuestionId); return ResponseEntity.noContent().build(); }
    @Override public ResponseEntity<InlineObject27> listDueReviews(String studentId, OffsetDateTime before, Integer page, Integer pageSize) {
        return ResponseEntity.ok(new InlineObject27().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(wrongQuestions.due(studentId, before, page, pageSize)));
    }
    @Override public ResponseEntity<InlineObject29> submitWrongQuestionReview(String wrongQuestionId, String idempotencyKey, ReviewCreate request) {
        return ResponseEntity.status(201).body(new InlineObject29().code("CREATED").message("created").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(wrongQuestions.review(wrongQuestionId, idempotencyKey, request)));
    }

    @Override public ResponseEntity<InlineObject21> listKnowledgeMastery(String studentId, String subjectId,
            String gradeId, java.math.BigDecimal minScore, java.math.BigDecimal maxScore) {
        return ResponseEntity.ok(new InlineObject21().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(mastery.list(studentId, subjectId, gradeId, minScore, maxScore)));
    }

    @Override public ResponseEntity<InlineObject22> listMasteryHistory(String knowledgeId, String studentId,
            Integer page, Integer pageSize) {
        return ResponseEntity.ok(new InlineObject22().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(mastery.history(studentId, knowledgeId, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject23> adjustKnowledgeMastery(String knowledgeId,
            MasteryAdjustRequest body) {
        return ResponseEntity.ok(new InlineObject23().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(mastery.adjust(knowledgeId, body)));
    }

    @Override public ResponseEntity<InlineObject23> unlockKnowledgeMastery(String studentId, String knowledgeId,
            Object body) {
        return ResponseEntity.ok(new InlineObject23().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(mastery.unlock(studentId, knowledgeId)));
    }

}

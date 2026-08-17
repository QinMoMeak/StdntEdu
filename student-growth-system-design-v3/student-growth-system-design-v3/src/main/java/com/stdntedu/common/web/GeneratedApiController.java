package com.stdntedu.common.web;

import java.time.OffsetDateTime;

import com.stdntedu.base.service.BaseDataService;
import com.stdntedu.dashboard.service.DashboardService;
import com.stdntedu.generated.api.DefaultApi;
import com.stdntedu.generated.model.AcademicTermCreateRequest;
import com.stdntedu.generated.model.AcademicTermUpdateRequest;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ExamType;
import com.stdntedu.generated.model.ExamUpdate;
import com.stdntedu.generated.model.DashboardResponse;
import com.stdntedu.generated.model.InlineObject13;
import com.stdntedu.generated.model.InlineObject14;
import com.stdntedu.generated.model.InlineObject15;
import com.stdntedu.generated.model.InlineObject16;
import com.stdntedu.generated.model.InlineObject17;
import com.stdntedu.generated.model.InlineObject18;
import com.stdntedu.generated.model.InlineObject1;
import com.stdntedu.generated.model.InlineObject2;
import com.stdntedu.generated.model.InlineObject21;
import com.stdntedu.generated.model.InlineObject22;
import com.stdntedu.generated.model.InlineObject23;
import com.stdntedu.generated.model.InlineObject24;
import com.stdntedu.generated.model.InlineObject25;
import com.stdntedu.generated.model.InlineObject26;
import com.stdntedu.generated.model.InlineObject27;
import com.stdntedu.generated.model.InlineObject28;
import com.stdntedu.generated.model.InlineObject29;
import com.stdntedu.generated.model.InlineObject30;
import com.stdntedu.generated.model.InlineObject31;
import com.stdntedu.generated.model.InlineObject11;
import com.stdntedu.generated.model.InlineObject12;
import com.stdntedu.generated.model.ReviewCreate;
import com.stdntedu.generated.model.MasteryAdjustRequest;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.ResourceHistoryCreateRequest;
import com.stdntedu.generated.model.ResourceHistoryPageResponse;
import com.stdntedu.generated.model.ResourceUpdate;
import com.stdntedu.generated.model.ScorePageResponse;
import com.stdntedu.generated.model.ScoreTrendResponse;
import com.stdntedu.generated.model.ScorePageResponseAllOfData;
import com.stdntedu.generated.model.StudyLogCreateRequest;
import com.stdntedu.generated.model.StudyLogPageResponse;
import com.stdntedu.generated.model.StudyLogUpdateRequest;
import com.stdntedu.generated.model.StudentResourceCreateRequest;
import com.stdntedu.generated.model.StudentResourcePageResponse;
import com.stdntedu.generated.model.StudentResourceResponse;
import com.stdntedu.generated.model.StudentResourceStatus;
import com.stdntedu.generated.model.StudentResourceUpdateRequest;
import com.stdntedu.generated.model.CompleteStudyPlanTaskRequest;
import com.stdntedu.generated.model.SkipStudyPlanTaskRequest;
import com.stdntedu.generated.model.StudyPlanCreateRequest;
import com.stdntedu.generated.model.StudyPlanPageResponse;
import com.stdntedu.generated.model.StudyPlanStatus;
import com.stdntedu.generated.model.StudyPlanStatusChangeRequest;
import com.stdntedu.generated.model.StudyPlanTaskCreateRequest;
import com.stdntedu.generated.model.StudyPlanTaskPageResponse;
import com.stdntedu.generated.model.StudyPlanTaskStatus;
import com.stdntedu.generated.model.StudyPlanTaskType;
import com.stdntedu.generated.model.StudyPlanTaskUpdateRequest;
import com.stdntedu.generated.model.StudyPlanUpdateRequest;
import com.stdntedu.resource.service.LearningResourceService;
import com.stdntedu.resource.service.ResourceHistoryService;
import com.stdntedu.resource.service.StudyLogService;
import com.stdntedu.resource.service.StudentResourceService;
import com.stdntedu.studyplan.service.StudyPlanService;
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
    private final LearningResourceService resources;
    private final ResourceHistoryService resourceHistory;
    private final StudyLogService studyLogs;
    private final StudentResourceService studentResources;
    private final StudyPlanService studyPlans;
    private final DashboardService dashboard;
    private final RequestIdProvider requestIds;

    public GeneratedApiController(BaseDataService baseData, StudentService students, AcademicTermService terms,
            ExamService exams, WrongQuestionService wrongQuestions, MasteryService mastery,
            LearningResourceService resources, ResourceHistoryService resourceHistory, StudyLogService studyLogs,
            StudentResourceService studentResources, StudyPlanService studyPlans, DashboardService dashboard,
            RequestIdProvider requestIds) {
        this.baseData = baseData;
        this.students = students;
        this.terms = terms;
        this.exams = exams;
        this.wrongQuestions = wrongQuestions;
        this.mastery = mastery;
        this.resources = resources;
        this.resourceHistory = resourceHistory;
        this.studyLogs = studyLogs;
        this.studentResources = studentResources;
        this.studyPlans = studyPlans;
        this.dashboard = dashboard;
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

    @Override public ResponseEntity<InlineObject30> listResources(Integer page, Integer pageSize) {
        return ResponseEntity.ok(new InlineObject30().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(resources.list(page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject31> createResource(ResourceCreate request) {
        return ResponseEntity.status(201).body(new InlineObject31().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(resources.create(request)));
    }

    @Override public ResponseEntity<InlineObject31> getResource(String resourceId) {
        return ResponseEntity.ok(new InlineObject31().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(resources.get(resourceId)));
    }

    @Override public ResponseEntity<InlineObject31> updateResource(String resourceId, ResourceUpdate request) {
        return ResponseEntity.ok(new InlineObject31().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(resources.update(resourceId, request)));
    }

    @Override public ResponseEntity<StudentResourcePageResponse> listStudentResources(String studentId,
            StudentResourceStatus status, String subjectId, Integer page, Integer pageSize) {
        return ResponseEntity.ok(new StudentResourcePageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(studentResources.list(studentId, status, subjectId, page, pageSize)));
    }

    @Override public ResponseEntity<StudentResourceResponse> createStudentResource(
            StudentResourceCreateRequest request) {
        return ResponseEntity.status(201).body(new StudentResourceResponse().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(studentResources.create(request)));
    }

    @Override public ResponseEntity<StudentResourceResponse> getStudentResource(String assignmentId) {
        return ResponseEntity.ok(new StudentResourceResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(studentResources.get(assignmentId)));
    }

    @Override public ResponseEntity<StudentResourceResponse> updateStudentResource(String assignmentId,
            StudentResourceUpdateRequest request) {
        return ResponseEntity.ok(new StudentResourceResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(studentResources.update(assignmentId, request)));
    }

    @Override public ResponseEntity<InlineObject11> createResourceHistory(String resourceId,
            ResourceHistoryCreateRequest request) {
        return ResponseEntity.status(201).body(new InlineObject11().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(resourceHistory.create(resourceId, request)));
    }

    @Override public ResponseEntity<ResourceHistoryPageResponse> listResourceHistory(String resourceId,
            String studentId, Integer page, Integer pageSize) {
        return ResponseEntity.ok(new ResourceHistoryPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(resourceHistory.listForResource(resourceId, studentId, page, pageSize)));
    }

    @Override public ResponseEntity<ResourceHistoryPageResponse> listStudentResourceHistory(String studentId,
            String subjectId, String resourceType, String sourceType, Boolean completed, java.time.LocalDate startDate,
            java.time.LocalDate endDate, Integer page, Integer pageSize) {
        return ResponseEntity.ok(new ResourceHistoryPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(resourceHistory.listForStudent(studentId, subjectId, resourceType, sourceType, completed,
                        startDate, endDate, page, pageSize)));
    }

    @Override public ResponseEntity<StudyLogPageResponse> listStudyLogs(String studentId, String subjectId,
            java.time.LocalDate startDate, java.time.LocalDate endDate, String keyword, Integer page, Integer pageSize) {
        return ResponseEntity.ok(new StudyLogPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(studyLogs.list(studentId, subjectId, startDate, endDate, keyword, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject12> createStudyLog(StudyLogCreateRequest request) {
        return ResponseEntity.status(201).body(new InlineObject12().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(studyLogs.create(request)));
    }

    @Override public ResponseEntity<InlineObject12> getStudyLog(String studyLogId) {
        return ResponseEntity.ok(new InlineObject12().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(studyLogs.get(studyLogId)));
    }

    @Override public ResponseEntity<InlineObject12> updateStudyLog(String studyLogId, StudyLogUpdateRequest request) {
        return ResponseEntity.ok(new InlineObject12().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(studyLogs.update(studyLogId, request)));
    }

    @Override public ResponseEntity<Void> deleteStudyLog(String studyLogId) {
        studyLogs.delete(studyLogId);
        return ResponseEntity.noContent().build();
    }

    @Override public ResponseEntity<StudyPlanPageResponse> listStudyPlans(String studentId, StudyPlanStatus status,
            String planType, java.time.LocalDate startDate, java.time.LocalDate endDate, Integer page,
            Integer pageSize) {
        return ResponseEntity.ok(new StudyPlanPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(studyPlans.list(studentId, status, planType, startDate, endDate, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject1> createStudyPlan(StudyPlanCreateRequest request) {
        return ResponseEntity.status(201).body(new InlineObject1().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(studyPlans.create(request)));
    }

    @Override public ResponseEntity<InlineObject1> getStudyPlan(String planId) {
        return ResponseEntity.ok(new InlineObject1().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(studyPlans.get(planId)));
    }

    @Override public ResponseEntity<InlineObject1> updateStudyPlan(String planId, StudyPlanUpdateRequest request) {
        return ResponseEntity.ok(new InlineObject1().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(studyPlans.update(planId, request)));
    }

    @Override public ResponseEntity<Void> deleteStudyPlan(String planId) {
        studyPlans.delete(planId);
        return ResponseEntity.noContent().build();
    }

    @Override public ResponseEntity<InlineObject1> activateStudyPlan(String planId,
            StudyPlanStatusChangeRequest request) {
        return planResponse(studyPlans.activate(planId, request));
    }

    @Override public ResponseEntity<InlineObject1> pauseStudyPlan(String planId,
            StudyPlanStatusChangeRequest request) {
        return planResponse(studyPlans.pause(planId, request));
    }

    @Override public ResponseEntity<InlineObject1> completeStudyPlan(String planId,
            StudyPlanStatusChangeRequest request) {
        return planResponse(studyPlans.complete(planId, request));
    }

    @Override public ResponseEntity<InlineObject1> cancelStudyPlan(String planId,
            StudyPlanStatusChangeRequest request) {
        return planResponse(studyPlans.cancel(planId, request));
    }

    @Override public ResponseEntity<StudyPlanTaskPageResponse> listStudyPlanTasks(String planId,
            java.time.LocalDate taskDate, StudyPlanTaskStatus status, StudyPlanTaskType taskType, Integer page,
            Integer pageSize) {
        return ResponseEntity.ok(new StudyPlanTaskPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(studyPlans.listTasks(planId, taskDate, status, taskType, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject2> createStudyPlanTask(String planId,
            StudyPlanTaskCreateRequest request) {
        return ResponseEntity.status(201).body(new InlineObject2().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(studyPlans.createTask(planId, request)));
    }

    @Override public ResponseEntity<InlineObject2> updateStudyPlanTask(String planId, String taskId,
            StudyPlanTaskUpdateRequest request) {
        return taskResponse(studyPlans.updateTask(planId, taskId, request));
    }

    @Override public ResponseEntity<InlineObject2> completeStudyPlanTask(String planId, String taskId,
            CompleteStudyPlanTaskRequest request) {
        return taskResponse(studyPlans.completeTask(planId, taskId, request));
    }

    @Override public ResponseEntity<InlineObject2> skipStudyPlanTask(String planId, String taskId,
            SkipStudyPlanTaskRequest request) {
        return taskResponse(studyPlans.skipTask(planId, taskId, request));
    }

    @Override public ResponseEntity<DashboardResponse> getDashboard(String studentId, String academicTermId,
            java.time.LocalDate date, String timezone) {
        return ResponseEntity.ok(new DashboardResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(dashboard.get(studentId, academicTermId, date, timezone)));
    }

    private ResponseEntity<InlineObject1> planResponse(com.stdntedu.generated.model.StudyPlanDto plan) {
        return ResponseEntity.ok(new InlineObject1().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(plan));
    }

    private ResponseEntity<InlineObject2> taskResponse(com.stdntedu.generated.model.StudyPlanTaskDto task) {
        return ResponseEntity.ok(new InlineObject2().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(task));
    }

}

package com.stdntedu.common.web;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import com.stdntedu.attachment.service.AttachmentService;
import com.stdntedu.base.service.BaseDataService;
import com.stdntedu.ai.model.service.AiModelService;
import com.stdntedu.ai.analysis.service.AiAnalysisService;
import com.stdntedu.ai.analysis.generation.AiStudyPlanGenerationService;
import com.stdntedu.ai.extraction.service.AiExtractionConfirmationService;
import com.stdntedu.ai.extraction.service.AiExtractionQuestionService;
import com.stdntedu.ai.extraction.service.AiExtractionService;
import com.stdntedu.ai.extraction.service.CreateExtractionCommand;
import com.stdntedu.dashboard.service.DashboardService;
import com.stdntedu.generated.api.DefaultApi;
import com.stdntedu.generated.model.AcademicTermCreateRequest;
import com.stdntedu.generated.model.AcademicTermUpdateRequest;
import com.stdntedu.generated.model.AiModelCreateRequest;
import com.stdntedu.generated.model.AiModelStatusChangeRequest;
import com.stdntedu.generated.model.AiModelUpdateRequest;
import com.stdntedu.generated.model.AiAnalysisBusinessType;
import com.stdntedu.generated.model.AiAnalysisPageResponse;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.AiConfirm;
import com.stdntedu.generated.model.AiExtractionQuestionUpdateRequest;
import com.stdntedu.generated.model.CancelAiExtractionRequest;
import com.stdntedu.generated.model.CancelExportRequest;
import com.stdntedu.generated.model.CancelImportRequest;
import com.stdntedu.generated.model.RetryAiExtractionRequest;
import com.stdntedu.generated.model.WrongSource;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ExamType;
import com.stdntedu.generated.model.ExamUpdate;
import com.stdntedu.generated.model.DashboardResponse;
import com.stdntedu.generated.model.GrowthEventCreateRequest;
import com.stdntedu.generated.model.GrowthEventPageResponse;
import com.stdntedu.generated.model.GrowthEventUpdateRequest;
import com.stdntedu.generated.model.ExportCreateRequest;
import com.stdntedu.generated.model.ExportFormat;
import com.stdntedu.generated.model.ExportTaskPageResponse;
import com.stdntedu.generated.model.ExportTaskStatus;
import com.stdntedu.generated.model.ImportConfirmRequest;
import com.stdntedu.generated.model.ImportStatus;
import com.stdntedu.generated.model.ImportTaskPageResponse;
import com.stdntedu.generated.model.ImportType;
import com.stdntedu.generated.model.RetryImportRequest;
import com.stdntedu.generated.model.InlineObject13;
import com.stdntedu.generated.model.InlineObject14;
import com.stdntedu.generated.model.InlineObject15;
import com.stdntedu.generated.model.InlineObject16;
import com.stdntedu.generated.model.InlineObject17;
import com.stdntedu.generated.model.InlineObject18;
import com.stdntedu.generated.model.InlineObject19;
import com.stdntedu.generated.model.InlineObject20;
import com.stdntedu.generated.model.InlineObject1;
import com.stdntedu.generated.model.InlineObject2;
import com.stdntedu.generated.model.InlineObject3;
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
import com.stdntedu.generated.model.InlineObject32;
import com.stdntedu.generated.model.InlineObject5;
import com.stdntedu.generated.model.InlineObject6;
import com.stdntedu.generated.model.InlineObject7;
import com.stdntedu.generated.model.InlineObject8;
import com.stdntedu.generated.model.InlineObject9;
import com.stdntedu.generated.model.InlineObject10;
import com.stdntedu.generated.model.InlineObject33;
import com.stdntedu.generated.model.InlineObject34;
import com.stdntedu.generated.model.InlineObject35;
import com.stdntedu.generated.model.InlineObject36;
import com.stdntedu.generated.model.InlineObject11;
import com.stdntedu.generated.model.InlineObject12;
import com.stdntedu.generated.model.ReviewCreate;
import com.stdntedu.generated.model.MasteryAdjustRequest;
import com.stdntedu.generated.model.KnowledgeNodeCreateRequest;
import com.stdntedu.generated.model.KnowledgeNodeDisableRequest;
import com.stdntedu.generated.model.KnowledgeNodeMoveRequest;
import com.stdntedu.generated.model.KnowledgeNodeUpdateRequest;
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
import com.stdntedu.generated.model.StudyPlanGenerateRequest;
import com.stdntedu.resource.service.LearningResourceService;
import com.stdntedu.resource.service.ResourceHistoryService;
import com.stdntedu.resource.service.StudyLogService;
import com.stdntedu.resource.service.StudentResourceService;
import com.stdntedu.studyplan.service.StudyPlanService;
import com.stdntedu.score.service.ExamService;
import com.stdntedu.knowledge.mastery.service.MasteryService;
import com.stdntedu.knowledge.node.service.KnowledgeNodeService;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.StudentUpdate;
import com.stdntedu.generated.model.WrongCreate;
import com.stdntedu.generated.model.WrongUpdate;
import com.stdntedu.growth.event.service.GrowthEventService;
import com.stdntedu.transfer.exporttask.ExportTaskService;
import com.stdntedu.transfer.importtask.ImportTaskService;
import com.stdntedu.student.service.AcademicTermService;
import com.stdntedu.student.service.StudentService;
import com.stdntedu.wrongquestion.service.WrongQuestionService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class GeneratedApiController implements DefaultApi {
    private final BaseDataService baseData;
    private final StudentService students;
    private final AcademicTermService terms;
    private final ExamService exams;
    private final WrongQuestionService wrongQuestions;
    private final MasteryService mastery;
    private final KnowledgeNodeService knowledgeNodes;
    private final LearningResourceService resources;
    private final ResourceHistoryService resourceHistory;
    private final StudyLogService studyLogs;
    private final StudentResourceService studentResources;
    private final StudyPlanService studyPlans;
    private final DashboardService dashboard;
    private final AiModelService aiModels;
    private final AiAnalysisService aiAnalyses;
    private final AiStudyPlanGenerationService aiStudyPlanGeneration;
    private final AiExtractionService aiExtractions;
    private final AiExtractionQuestionService extractionQuestions;
    private final AiExtractionConfirmationService extractionConfirmations;
    private final AttachmentService attachments;
    private final GrowthEventService growthEvents;
    private final ImportTaskService importTasks;
    private final ExportTaskService exportTasks;
    private final RequestIdProvider requestIds;

    public GeneratedApiController(BaseDataService baseData, StudentService students, AcademicTermService terms,
            ExamService exams, WrongQuestionService wrongQuestions, MasteryService mastery,
            KnowledgeNodeService knowledgeNodes,
            LearningResourceService resources, ResourceHistoryService resourceHistory, StudyLogService studyLogs,
            StudentResourceService studentResources, StudyPlanService studyPlans, DashboardService dashboard,
            AiModelService aiModels, AiAnalysisService aiAnalyses,
            AiStudyPlanGenerationService aiStudyPlanGeneration, AiExtractionService aiExtractions,
            AiExtractionQuestionService extractionQuestions,
            AiExtractionConfirmationService extractionConfirmations, AttachmentService attachments,
            GrowthEventService growthEvents, ImportTaskService importTasks, ExportTaskService exportTasks,
            RequestIdProvider requestIds) {
        this.baseData = baseData;
        this.students = students;
        this.terms = terms;
        this.exams = exams;
        this.wrongQuestions = wrongQuestions;
        this.mastery = mastery;
        this.knowledgeNodes = knowledgeNodes;
        this.resources = resources;
        this.resourceHistory = resourceHistory;
        this.studyLogs = studyLogs;
        this.studentResources = studentResources;
        this.studyPlans = studyPlans;
        this.dashboard = dashboard;
        this.aiModels = aiModels;
        this.aiAnalyses = aiAnalyses;
        this.aiStudyPlanGeneration = aiStudyPlanGeneration;
        this.aiExtractions = aiExtractions;
        this.extractionQuestions = extractionQuestions;
        this.extractionConfirmations = extractionConfirmations;
        this.attachments = attachments;
        this.growthEvents = growthEvents;
        this.importTasks = importTasks;
        this.exportTasks = exportTasks;
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

    @Override public ResponseEntity<InlineObject19> getKnowledgeTree(String stageId, String gradeId,
            String subjectId, Boolean enabledOnly) {
        return ResponseEntity.ok(new InlineObject19().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now())
                .data(knowledgeNodes.tree(stageId, gradeId, subjectId, !Boolean.FALSE.equals(enabledOnly))));
    }

    @Override public ResponseEntity<InlineObject20> createKnowledgeNode(KnowledgeNodeCreateRequest request) {
        return ResponseEntity.status(201).body(new InlineObject20().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(knowledgeNodes.create(request)));
    }

    @Override public ResponseEntity<InlineObject20> updateKnowledgeNode(String knowledgeId,
            KnowledgeNodeUpdateRequest request) {
        return knowledgeNodeResponse(knowledgeNodes.update(knowledgeId, request));
    }

    @Override public ResponseEntity<InlineObject20> moveKnowledgeNode(String knowledgeId,
            KnowledgeNodeMoveRequest request) {
        return knowledgeNodeResponse(knowledgeNodes.move(knowledgeId, request));
    }

    @Override public ResponseEntity<InlineObject20> disableKnowledgeNode(String knowledgeId,
            KnowledgeNodeDisableRequest request) {
        return knowledgeNodeResponse(knowledgeNodes.disable(knowledgeId, request));
    }

    @Override public ResponseEntity<InlineObject32> uploadAttachment(MultipartFile file) {
        return ResponseEntity.status(201).body(new InlineObject32().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(attachments.upload(file)));
    }

    @Override public ResponseEntity<Resource> downloadAttachment(String attachmentId) {
        AttachmentService.Download download = attachments.download(attachmentId);
        String disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(download.content());
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

    @Override public ResponseEntity<GrowthEventPageResponse> listGrowthEvents(String studentId, String eventType,
            java.time.LocalDate startDate, java.time.LocalDate endDate, String keyword, Integer page,
            Integer pageSize) {
        return ResponseEntity.ok(new GrowthEventPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(growthEvents.list(studentId, eventType, startDate, endDate, keyword, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject3> createGrowthEvent(GrowthEventCreateRequest request) {
        return ResponseEntity.status(201).body(new InlineObject3().code("CREATED").message("created")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(growthEvents.create(request)));
    }

    @Override public ResponseEntity<InlineObject3> getGrowthEvent(String eventId) {
        return ResponseEntity.ok(new InlineObject3().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(growthEvents.get(eventId)));
    }

    @Override public ResponseEntity<InlineObject3> updateGrowthEvent(String eventId,
            GrowthEventUpdateRequest request) {
        return ResponseEntity.ok(new InlineObject3().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(growthEvents.update(eventId, request)));
    }

    @Override public ResponseEntity<Void> deleteGrowthEvent(Integer version, String eventId) {
        growthEvents.delete(eventId, version);
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

    @Override public ResponseEntity<InlineObject10> generateStudyPlan(String idempotencyKey,
            StudyPlanGenerateRequest request) {
        return ResponseEntity.accepted().body(new InlineObject10().code("ACCEPTED").message("accepted")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(aiStudyPlanGeneration.generate(idempotencyKey, request)));
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

    @Override public ResponseEntity<InlineObject5> listAiModels() {
        return ResponseEntity.ok(new InlineObject5().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(aiModels.list()));
    }

    @Override public ResponseEntity<AiAnalysisPageResponse> listAiAnalyses(String studentId,
            AiAnalysisBusinessType businessType, String businessId, String modelId, AiTaskStatus status,
            OffsetDateTime startTime, OffsetDateTime endTime, Integer page, Integer pageSize) {
        return ResponseEntity.ok(new AiAnalysisPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(aiAnalyses.list(studentId, businessType, businessId, modelId, status,
                        startTime, endTime, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject10> getAiAnalysis(String analysisId) {
        return ResponseEntity.ok(new InlineObject10().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(aiAnalyses.get(analysisId)));
    }

    @Override public ResponseEntity<InlineObject6> createAiModel(AiModelCreateRequest request) {
        return ResponseEntity.status(201).body(modelResponse(aiModels.create(request), "CREATED", "created"));
    }

    @Override public ResponseEntity<InlineObject6> getAiModel(String modelId) {
        return ResponseEntity.ok(modelResponse(aiModels.get(modelId), "OK", "success"));
    }

    @Override public ResponseEntity<InlineObject6> updateAiModel(String modelId, AiModelUpdateRequest request) {
        return ResponseEntity.ok(modelResponse(aiModels.update(modelId, request), "OK", "success"));
    }

    @Override public ResponseEntity<InlineObject6> enableAiModel(String modelId,
            AiModelStatusChangeRequest request) {
        return ResponseEntity.ok(modelResponse(aiModels.enable(modelId, request), "OK", "success"));
    }

    @Override public ResponseEntity<InlineObject6> disableAiModel(String modelId,
            AiModelStatusChangeRequest request) {
        return ResponseEntity.ok(modelResponse(aiModels.disable(modelId, request), "OK", "success"));
    }

    @Override public ResponseEntity<InlineObject7> testAiModelConnection(String modelId) {
        return ResponseEntity.ok(new InlineObject7().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(aiModels.testConnection(modelId)));
    }

    @Override public ResponseEntity<InlineObject33> createAiExtractionTask(List<MultipartFile> files,
            String studentId, WrongSource sourceType, String modelId, String subjectId, String gradeId,
            String sourceName, String examId, Boolean recognizeAnalysis, Boolean matchKnowledge,
            Boolean maskPersonalInfo) {
        var task = aiExtractions.create(files, new CreateExtractionCommand(studentId, subjectId, gradeId,
                sourceType, sourceName, examId, modelId, Boolean.TRUE.equals(recognizeAnalysis),
                !Boolean.FALSE.equals(matchKnowledge), Boolean.TRUE.equals(maskPersonalInfo)));
        return ResponseEntity.accepted().body(taskResponse(task, "ACCEPTED", "accepted"));
    }

    @Override public ResponseEntity<InlineObject33> getAiExtractionTask(String taskId) {
        return ResponseEntity.ok(taskResponse(aiExtractions.get(taskId), "OK", "success"));
    }

    @Override public ResponseEntity<InlineObject8> listAiExtractionQuestions(String taskId) {
        return ResponseEntity.ok(new InlineObject8().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(extractionQuestions.list(taskId)));
    }

    @Override public ResponseEntity<InlineObject9> updateAiExtractionQuestion(String taskId, String questionId,
            AiExtractionQuestionUpdateRequest request) {
        return ResponseEntity.ok(new InlineObject9().code("OK").message("success").requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(extractionQuestions.update(taskId, questionId, request)));
    }

    @Override public ResponseEntity<InlineObject34> confirmAiExtractionTask(String taskId, String idempotencyKey,
            AiConfirm request) {
        return ResponseEntity.ok(new InlineObject34().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(extractionConfirmations.confirm(taskId, idempotencyKey, request)));
    }

    @Override public ResponseEntity<InlineObject33> retryAiWrongQuestionExtraction(String taskId,
            RetryAiExtractionRequest request) {
        return ResponseEntity.accepted().body(taskResponse(aiExtractions.retry(taskId, request),
                "ACCEPTED", "accepted"));
    }

    @Override public ResponseEntity<InlineObject33> cancelAiWrongQuestionExtraction(String taskId,
            CancelAiExtractionRequest request) {
        return ResponseEntity.ok(taskResponse(aiExtractions.cancel(taskId, request), "OK", "success"));
    }

    @Override public ResponseEntity<ImportTaskPageResponse> listImportTasks(ImportType importType,
            ImportStatus status, String studentId, OffsetDateTime startTime, OffsetDateTime endTime,
            Integer page, Integer pageSize) {
        return ResponseEntity.ok(new ImportTaskPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(importTasks.list(importType, status, studentId, startTime, endTime, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject35> createImportTask(MultipartFile file, ImportType importType,
            String studentId, Boolean dryRun, String encoding, String sheetName, Boolean hasHeader) {
        return importResponse(importTasks.create(file, importType, studentId,
                dryRun == null ? true : dryRun, encoding == null ? "UTF-8" : encoding,
                sheetName, hasHeader == null ? true : hasHeader), "ACCEPTED", "accepted", 202);
    }

    @Override public ResponseEntity<InlineObject35> getImportTask(String taskId) {
        return importResponse(importTasks.get(taskId), "OK", "success", 200);
    }

    @Override public ResponseEntity<InlineObject35> confirmImportTask(String taskId, String idempotencyKey,
            ImportConfirmRequest request) {
        return importResponse(importTasks.confirm(taskId, idempotencyKey, request),
                "ACCEPTED", "accepted", 202);
    }

    @Override public ResponseEntity<InlineObject35> cancelImportTask(String taskId, CancelImportRequest request) {
        return importResponse(importTasks.cancel(taskId), "OK", "success", 200);
    }

    @Override public ResponseEntity<InlineObject35> retryImportTask(String taskId, RetryImportRequest request) {
        return importResponse(importTasks.retry(taskId, request), "ACCEPTED", "accepted", 202);
    }

    @Override public ResponseEntity<Resource> downloadImportErrorReport(String taskId, String format) {
        var download = importTasks.errorReport(taskId);
        return fileResponse(download.content(), download.fileName(), download.mimeType(), download.size());
    }

    @Override public ResponseEntity<ExportTaskPageResponse> listExportTasks(String studentId,
            ExportTaskStatus status, ExportFormat format, OffsetDateTime startTime, OffsetDateTime endTime,
            Integer page, Integer pageSize) {
        return ResponseEntity.ok(new ExportTaskPageResponse().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now())
                .data(exportTasks.list(studentId, status, format, startTime, endTime, page, pageSize)));
    }

    @Override public ResponseEntity<InlineObject36> createExportTask(ExportCreateRequest request) {
        return exportResponse(exportTasks.create(request), "ACCEPTED", "accepted", 202);
    }

    @Override public ResponseEntity<InlineObject36> getExportTask(String taskId) {
        return exportResponse(exportTasks.get(taskId), "OK", "success", 200);
    }

    @Override public ResponseEntity<InlineObject36> cancelExportTask(String taskId, CancelExportRequest request) {
        return exportResponse(exportTasks.cancel(taskId), "OK", "success", 200);
    }

    @Override public ResponseEntity<Resource> downloadExportFile(String taskId) {
        var download = exportTasks.download(taskId);
        return fileResponse(download.content(), download.fileName(), download.mimeType(), download.size());
    }

    private ResponseEntity<InlineObject35> importResponse(com.stdntedu.generated.model.ImportTaskDto task,
            String code, String message, int status) {
        InlineObject35 body = new InlineObject35().code(code).message(message).requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(task);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<InlineObject36> exportResponse(com.stdntedu.generated.model.ExportTask task,
            String code, String message, int status) {
        InlineObject36 body = new InlineObject36().code(code).message(message).requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(task);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Resource> fileResponse(Resource content, String fileName, String mimeType, long size) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(size)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(content);
    }

    private ResponseEntity<InlineObject1> planResponse(com.stdntedu.generated.model.StudyPlanDto plan) {
        return ResponseEntity.ok(new InlineObject1().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(plan));
    }

    private ResponseEntity<InlineObject20> knowledgeNodeResponse(
            com.stdntedu.generated.model.KnowledgeTreeNodeDto node) {
        return ResponseEntity.ok(new InlineObject20().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(node));
    }

    private ResponseEntity<InlineObject2> taskResponse(com.stdntedu.generated.model.StudyPlanTaskDto task) {
        return ResponseEntity.ok(new InlineObject2().code("OK").message("success")
                .requestId(requestIds.current()).timestamp(OffsetDateTime.now()).data(task));
    }

    private InlineObject6 modelResponse(com.stdntedu.generated.model.AiModelDto model,
            String code, String message) {
        return new InlineObject6().code(code).message(message).requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(model);
    }

    private InlineObject33 taskResponse(com.stdntedu.generated.model.AiTask task, String code, String message) {
        return new InlineObject33().code(code).message(message).requestId(requestIds.current())
                .timestamp(OffsetDateTime.now()).data(task);
    }

}

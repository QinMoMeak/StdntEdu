package com.stdntedu.ai.extraction.service;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.ai.extraction.converter.AiExtractionConverter;
import com.stdntedu.ai.extraction.entity.AiExtractionFileEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import com.stdntedu.ai.extraction.mapper.AiExtractionFileMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionQuestionMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionTaskMapper;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.ai.extraction.resource.PreparedExtraction;
import com.stdntedu.ai.extraction.resource.StoredOriginal;
import com.stdntedu.ai.extraction.resource.UploadPreflightService;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.mapper.AiModelMapper;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.GradeMapper;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiAuthType;
import com.stdntedu.generated.model.AiModelType;
import com.stdntedu.generated.model.AiTask;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.CancelAiExtractionRequest;
import com.stdntedu.generated.model.RetryAiExtractionRequest;
import com.stdntedu.score.entity.ExamEntity;
import com.stdntedu.score.mapper.ExamMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiExtractionService {
    static final String EXTRACTION_PROMPT = """
            Analyze the supplied images in order and return JSON only. The root object must contain a questions array.
            For each independently recognizable question return pageNumber, questionNumber, questionType,
            questionText, studentAnswer, correctAnswer, answerSource, analysisText, analysisSource, errorType,
            difficulty, confidence, and knowledgeCandidates. Use null when content cannot be recognized.
            Never invent question text or answers. knowledgeCandidates must contain only system-parseable fields.
            Do not wrap the JSON in Markdown.
            """;

    private final UploadPreflightService preflight;
    private final OriginalFileStorage storage;
    private final AiExtractionPersistenceService persistence;
    private final AiExtractionSchedulingService scheduling;
    private final AiExtractionTaskMapper tasks;
    private final AiExtractionFileMapper files;
    private final AiExtractionQuestionMapper questions;
    private final AiModelMapper models;
    private final StudentMapper students;
    private final SubjectMapper subjects;
    private final GradeMapper grades;
    private final ExamMapper exams;
    private final AiExtractionConverter converter;
    private final IdConverter ids;

    public AiExtractionService(UploadPreflightService preflight, OriginalFileStorage storage,
            AiExtractionPersistenceService persistence,
            AiExtractionSchedulingService scheduling, AiExtractionTaskMapper tasks,
            AiExtractionFileMapper files, AiExtractionQuestionMapper questions, AiModelMapper models,
            StudentMapper students, SubjectMapper subjects, GradeMapper grades, ExamMapper exams,
            AiExtractionConverter converter, IdConverter ids) {
        this.preflight = preflight;
        this.storage = storage;
        this.persistence = persistence;
        this.scheduling = scheduling;
        this.tasks = tasks;
        this.files = files;
        this.questions = questions;
        this.models = models;
        this.students = students;
        this.subjects = subjects;
        this.grades = grades;
        this.exams = exams;
        this.converter = converter;
        this.ids = ids;
    }

    public AiTask create(List<MultipartFile> uploads, CreateExtractionCommand command) {
        validate(command);
        try (PreparedExtraction prepared = preflight.prepare(uploads)) {
            List<StoredOriginal> stored = storage.persist(prepared);
            CreatedExtraction created;
            try {
                created = scheduling.create(command, prepared, stored);
            } catch (RuntimeException ex) {
                storage.cleanup(stored);
                throw ex;
            }
            return converter.task(created.task(), stored.size(), 0);
        }
    }

    public AiTask get(String taskId) {
        Long id = ids.toLong(taskId);
        AiExtractionTaskEntity task = persistence.requireTask(id);
        int fileCount = Math.toIntExact(files.selectCount(Wrappers.<AiExtractionFileEntity>lambdaQuery()
                .eq(AiExtractionFileEntity::getTaskId, id)));
        int questionCount = Math.toIntExact(questions.selectCount(Wrappers.<AiExtractionQuestionEntity>lambdaQuery()
                .eq(AiExtractionQuestionEntity::getTaskId, id)));
        return converter.task(task, fileCount, questionCount);
    }

    public AiTask retry(String taskId, RetryAiExtractionRequest request) {
        Long id = ids.toLong(taskId);
        AiExtractionTaskEntity task = persistence.requireTask(id);
        if (!List.of(AiTaskStatus.FAILED.getValue(), AiTaskStatus.EXPIRED.getValue()).contains(task.getStatus())) {
            throw conflict("only FAILED or EXPIRED extraction tasks can be retried");
        }
        Long modelId = request.getModelId() == null ? task.getModelId() : ids.toLong(request.getModelId());
        validateModel(modelId);
        AiExtractionTaskEntity pending = scheduling.retry(id, task.getStatus(), modelId,
                Boolean.TRUE.equals(request.getResetTemporaryQuestions()));
        return converter.task(pending, persistence.fileCount(id), persistence.questionCount(id));
    }

    public AiTask cancel(String taskId, CancelAiExtractionRequest request) {
        Long id = ids.toLong(taskId);
        persistence.requireTask(id);
        String reason = request == null || request.getReason() == null ? "cancelled by user"
                : truncate(request.getReason(), 1000);
        if (tasks.cancel(id, reason) == 0) throw conflict("extraction task cannot be cancelled in its current status");
        return get(taskId);
    }

    private AiModelEntity validate(CreateExtractionCommand command) {
        Long studentId = ids.toLong(command.studentId());
        if (students.selectById(studentId) == null) throw new ResourceNotFoundException("student not found");
        if (command.sourceType() == null) throw invalid("sourceType is required");
        if (command.subjectId() != null) {
            SubjectEntity subject = subjects.selectById(ids.toLong(command.subjectId()));
            if (subject == null || !Boolean.TRUE.equals(subject.getEnabled())) throw invalid("subject is invalid or disabled");
        }
        if (command.gradeId() != null) {
            GradeEntity grade = grades.selectById(ids.toLong(command.gradeId()));
            if (grade == null || !Boolean.TRUE.equals(grade.getEnabled())) throw invalid("grade is invalid or disabled");
        }
        if (command.examId() != null) {
            ExamEntity exam = exams.selectById(ids.toLong(command.examId()));
            if (exam == null || !studentId.equals(exam.getStudentId())) throw invalid("exam does not belong to student");
        }
        return validateModel(ids.toLong(command.modelId()));
    }

    private AiModelEntity validateModel(Long modelId) {
        AiModelEntity model = models.selectById(modelId);
        if (model == null) throw new ResourceNotFoundException("AI model not found");
        if (!Boolean.TRUE.equals(model.getEnabled())) throw invalid("AI model is disabled");
        if (model.getModelType() != AiModelType.MULTIMODAL) throw invalid("AI model must be MULTIMODAL");
        if (model.getAuthType() == AiAuthType.BEARER_API_KEY && model.getApiKeyRef() == null) {
            throw invalid("AI model has no configured API key");
        }
        return model;
    }

    private String truncate(String value, int maximum) {
        return value == null || value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private BusinessException invalid(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException conflict(String message) {
        return new BusinessException("TASK_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }

}

package com.stdntedu.transfer.importtask;

import java.util.List;
import java.util.Set;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ImportType;
import com.stdntedu.generated.model.KnowledgeNodeCreateRequest;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.WrongCreate;
import com.stdntedu.knowledge.node.service.KnowledgeNodeService;
import com.stdntedu.resource.service.LearningResourceService;
import com.stdntedu.score.service.ExamService;
import com.stdntedu.student.service.StudentService;
import com.stdntedu.wrongquestion.service.WrongQuestionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportDomainWriter {
    private final StudentService students;
    private final KnowledgeNodeService knowledge;
    private final LearningResourceService resources;
    private final ExamService exams;
    private final WrongQuestionService wrongQuestions;

    public ImportDomainWriter(StudentService students, KnowledgeNodeService knowledge,
            LearningResourceService resources, ExamService exams, WrongQuestionService wrongQuestions) {
        this.students = students;
        this.knowledge = knowledge;
        this.resources = resources;
        this.exams = exams;
        this.wrongQuestions = wrongQuestions;
    }

    @Transactional
    public int write(ImportType type, Long studentId, List<ImportFileParser.ParsedRow> rows,
            Set<Integer> selectedRows) {
        int written = 0;
        for (ImportFileParser.ParsedRow row : rows) {
            if (!selectedRows.isEmpty() && !selectedRows.contains(row.rowNumber())) continue;
            switch (type) {
                case STUDENT -> students.create((StudentCreate) row.value());
                case KNOWLEDGE -> knowledge.create((KnowledgeNodeCreateRequest) row.value());
                case LEARNING_RESOURCE -> resources.create((ResourceCreate) row.value());
                case SCORE -> {
                    ExamCreate request = (ExamCreate) row.value();
                    requireOwner(studentId, request.getStudentId());
                    exams.create(request);
                }
                case WRONG_QUESTION -> {
                    WrongCreate request = (WrongCreate) row.value();
                    requireOwner(studentId, request.getStudentId());
                    wrongQuestions.create(request);
                }
            }
            written++;
        }
        return written;
    }

    private void requireOwner(Long studentId, String rowStudentId) {
        if (studentId == null || !studentId.toString().equals(rowStudentId)) {
            throw new BusinessException("STUDENT_SCOPE_MISMATCH", "row studentId does not match import task",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}

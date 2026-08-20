package com.stdntedu.ai.analysis.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.analysis.entity.AiAnalysisEntity;
import com.stdntedu.ai.analysis.generation.model.NormalizedStudyPlanGenerationRequest;
import com.stdntedu.ai.analysis.generation.model.StudyPlanGenerationProposal;
import com.stdntedu.ai.analysis.generation.model.StudyPlanTaskProposal;
import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.StudyPlanCreateRequest;
import com.stdntedu.generated.model.StudyPlanDto;
import com.stdntedu.generated.model.StudyPlanTaskCreateRequest;
import com.stdntedu.studyplan.service.StudyPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiStudyPlanGenerationCompletionService {
    private final AiAnalysisMapper analyses;
    private final StudyPlanService studyPlans;
    private final ObjectMapper objectMapper;

    public AiStudyPlanGenerationCompletionService(AiAnalysisMapper analyses, StudyPlanService studyPlans,
            ObjectMapper objectMapper) {
        this.analyses = analyses;
        this.studyPlans = studyPlans;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StudyPlanDto complete(Long analysisId, NormalizedStudyPlanGenerationRequest request,
            StudyPlanGenerationProposal proposal, Integer promptTokens, Integer completionTokens) {
        AiAnalysisEntity analysis = analyses.selectById(analysisId);
        if (analysis == null || analysis.getStatus() != AiTaskStatus.RUNNING) {
            throw new IllegalStateException("analysis is not running");
        }
        StudyPlanCreateRequest create = new StudyPlanCreateRequest()
                .studentId(request.studentId()).title(proposal.title()).planType(proposal.planType())
                .startDate(request.startDate()).endDate(request.endDate())
                .dailyAvailableMinutes(request.dailyAvailableMinutes()).description(proposal.description())
                .tasks(proposal.tasks().stream().map(this::task).toList());
        StudyPlanDto plan = studyPlans.createFromAnalysis(analysisId, create);
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("study plan snapshot could not be encoded", ex);
        }
        if (analyses.markSuccess(analysisId, snapshot, promptTokens, completionTokens) != 1) {
            throw new IllegalStateException("analysis success transition failed");
        }
        return plan;
    }

    private StudyPlanTaskCreateRequest task(StudyPlanTaskProposal proposal) {
        return new StudyPlanTaskCreateRequest()
                .taskDate(proposal.taskDate()).taskType(proposal.taskType()).title(proposal.title())
                .resourceId(proposal.resourceId()).wrongQuestionId(proposal.wrongQuestionId())
                .knowledgeId(proposal.knowledgeId()).examId(proposal.examId())
                .expectedDurationSeconds(proposal.expectedDurationSeconds())
                .sortOrder(proposal.sortOrder() == null ? 0 : proposal.sortOrder()).remark(proposal.remark());
    }
}

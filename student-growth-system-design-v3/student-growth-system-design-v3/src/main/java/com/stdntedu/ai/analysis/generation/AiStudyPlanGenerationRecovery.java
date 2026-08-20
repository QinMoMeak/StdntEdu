package com.stdntedu.ai.analysis.generation;

import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.studyplan.mapper.StudyPlanMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AiStudyPlanGenerationRecovery {
    private final AiAnalysisMapper analyses;
    private final StudyPlanMapper plans;
    private final AiStudyPlanGenerationDispatcher dispatcher;

    public AiStudyPlanGenerationRecovery(AiAnalysisMapper analyses, StudyPlanMapper plans,
            AiStudyPlanGenerationDispatcher dispatcher) {
        this.analyses = analyses;
        this.plans = plans;
        this.dispatcher = dispatcher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { recover(); }

    public void recover() {
        for (Long id : analyses.selectIdsByStatus("RUNNING")) {
            if (plans.countBySourceAnalysisId(id) > 0) {
                analyses.markRecoveryConflict(id);
            } else if (analyses.resetRunning(id) == 1) {
                dispatcher.dispatch(id);
            }
        }
        for (Long id : analyses.selectIdsByStatus("PENDING")) {
            if (plans.countBySourceAnalysisId(id) > 0) analyses.markRecoveryConflict(id);
            else dispatcher.dispatch(id);
        }
    }
}

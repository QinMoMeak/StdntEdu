package com.stdntedu.ai;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.stdntedu.ai.analysis.generation.AiStudyPlanGenerationDispatcher;
import com.stdntedu.ai.analysis.generation.AiStudyPlanGenerationRecovery;
import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionTaskMapper;
import com.stdntedu.ai.extraction.service.AiExtractionDispatcher;
import com.stdntedu.ai.extraction.service.AiExtractionPersistenceService;
import com.stdntedu.ai.extraction.service.AiExtractionRecoveryService;
import com.stdntedu.studyplan.mapper.StudyPlanMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AiPendingRescanTest {
    @Test
    void studyPlanRescanUsesBoundedStablePendingBatch() {
        AiAnalysisMapper analyses = mock(AiAnalysisMapper.class);
        StudyPlanMapper plans = mock(StudyPlanMapper.class);
        AiStudyPlanGenerationDispatcher dispatcher = mock(AiStudyPlanGenerationDispatcher.class);
        when(analyses.selectIdsByStatusAfter("PENDING", 0L, 2)).thenReturn(List.of(7L, 9L));
        when(plans.countBySourceAnalysisId(7L)).thenReturn(0L);
        when(plans.countBySourceAnalysisId(9L)).thenReturn(0L);
        AiStudyPlanGenerationRecovery recovery = new AiStudyPlanGenerationRecovery(
                analyses, plans, dispatcher, 2);

        recovery.rescanPending();

        InOrder order = inOrder(dispatcher);
        order.verify(dispatcher).dispatch(7L);
        order.verify(dispatcher).dispatch(9L);
        verify(analyses).selectIdsByStatusAfter("PENDING", 0L, 2);
        verify(analyses, never()).selectIdsByStatusAfter("RUNNING", 0L, 2);
    }

    @Test
    void extractionRescanUsesItsOwnBoundedStablePendingBatch() {
        AiExtractionTaskMapper tasks = mock(AiExtractionTaskMapper.class);
        AiExtractionDispatcher dispatcher = mock(AiExtractionDispatcher.class);
        when(tasks.selectIdsByStatusAfter("PENDING", 0L, 3)).thenReturn(List.of(4L, 6L, 8L));
        AiExtractionRecoveryService recovery = new AiExtractionRecoveryService(
                tasks, mock(AiExtractionPersistenceService.class), dispatcher, 3);

        recovery.rescanPending();

        InOrder order = inOrder(dispatcher);
        order.verify(dispatcher).dispatch(4L);
        order.verify(dispatcher).dispatch(6L);
        order.verify(dispatcher).dispatch(8L);
        verify(tasks).selectIdsByStatusAfter("PENDING", 0L, 3);
        verify(tasks, never()).selectIdsByStatusAfter("RUNNING", 0L, 3);
    }

    @Test
    void failedScanDoesNotPreventTheNextCycle() {
        AiAnalysisMapper analyses = mock(AiAnalysisMapper.class);
        StudyPlanMapper plans = mock(StudyPlanMapper.class);
        AiStudyPlanGenerationDispatcher dispatcher = mock(AiStudyPlanGenerationDispatcher.class);
        when(analyses.selectIdsByStatusAfter("PENDING", 0L, 1))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of(12L));
        when(plans.countBySourceAnalysisId(12L)).thenReturn(0L);
        AiStudyPlanGenerationRecovery recovery = new AiStudyPlanGenerationRecovery(
                analyses, plans, dispatcher, 1);

        recovery.rescanPending();
        recovery.rescanPending();

        verify(dispatcher).dispatch(12L);
    }
}

package com.stdntedu.ai.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class AiExtractionDispatcherTest {
    @Test
    void rejectedDispatchLeavesTheDatabaseStateForStartupRecovery() {
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        AiExtractionWorker worker = mock(AiExtractionWorker.class);
        org.mockito.Mockito.doThrow(new TaskRejectedException("queue full"))
                .when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        AiExtractionDispatcher dispatcher = new AiExtractionDispatcher(executor, worker);

        assertThat(dispatcher.dispatch(41L)).isFalse();
        assertThat(dispatcher.isScheduled(41L)).isFalse();
        verifyNoInteractions(worker);
    }
}

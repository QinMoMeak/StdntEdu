package com.stdntedu.ai.analysis.generation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiStudyPlanExecutorConfig {
    @Bean(name = "aiStudyPlanExecutor")
    ThreadPoolTaskExecutor aiStudyPlanExecutor(
            @Value("${app.ai.study-plan.executor.core-size:2}") int coreSize,
            @Value("${app.ai.study-plan.executor.max-size:4}") int maxSize,
            @Value("${app.ai.study-plan.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-study-plan-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

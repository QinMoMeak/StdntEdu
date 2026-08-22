package com.stdntedu.ai.extraction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiExtractionExecutorConfig {
    @Bean(name = "aiExtractionExecutor")
    ThreadPoolTaskExecutor aiExtractionExecutor(
            @Value("${app.ai.extraction.executor.core-size:1}") int coreSize,
            @Value("${app.ai.extraction.executor.max-size:2}") int maxSize,
            @Value("${app.ai.extraction.executor.queue-capacity:20}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-extraction-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

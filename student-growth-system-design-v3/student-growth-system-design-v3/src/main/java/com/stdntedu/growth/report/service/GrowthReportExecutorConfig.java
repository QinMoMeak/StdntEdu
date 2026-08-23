package com.stdntedu.growth.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class GrowthReportExecutorConfig {
    @Bean(name = "growthReportExecutor")
    ThreadPoolTaskExecutor growthReportExecutor(
            @Value("${app.growth-report.executor.queue-capacity:20}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Math.max(1, queue));
        executor.setThreadNamePrefix("growth-report-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

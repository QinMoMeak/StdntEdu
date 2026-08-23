package com.stdntedu.transfer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TransferExecutorConfig {
    @Bean(name = "transferExecutor")
    ThreadPoolTaskExecutor transferExecutor(
            @Value("${app.transfer.executor.core-size:1}") int core,
            @Value("${app.transfer.executor.max-size:2}") int max,
            @Value("${app.transfer.executor.queue-capacity:20}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("transfer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

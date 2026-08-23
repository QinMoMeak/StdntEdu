package com.stdntedu.backup.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BackupRestoreExecutorConfig {
    @Bean(name = "backupRestoreExecutor")
    ThreadPoolTaskExecutor backupRestoreExecutor(
            @Value("${app.backup-restore.executor.queue-capacity:10}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Math.max(1, queue));
        executor.setThreadNamePrefix("backup-restore-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

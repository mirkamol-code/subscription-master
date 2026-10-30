package com.mirkamolcode.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    Executor gmailSyncExecutor(@Value("${app.gmail.sync.async-enabled}") boolean asyncEnabled,
                               @Value("${app.gmail.sync.max-concurrent}") int maxConcurrent) {
        if (!asyncEnabled)
            return new SyncTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrent);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("gmail-sync-");
        executor.initialize();
        return executor;
    }
}

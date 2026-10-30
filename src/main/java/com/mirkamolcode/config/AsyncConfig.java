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
        return createExecutor(asyncEnabled, maxConcurrent, "gmail-sync-");
    }

    @Bean
    Executor notificationExecutor(@Value("${app.notifications.async-enabled}") boolean asyncEnabled,
                                  @Value("${app.notifications.max-concurrent}") int maxConcurrent) {
        return createExecutor(asyncEnabled, maxConcurrent, "notify-");
    }

    private static Executor createExecutor(boolean asyncEnabled, int maxConcurrent, String threadNamePrefix) {
        if (!asyncEnabled)
            return new SyncTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrent);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}

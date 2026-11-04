package com.mirkamolcode.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redetection.apple.dry-run-on-startup", havingValue = "true")
public class AppleRedetectionDryRunLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AppleRedetectionDryRunLogger.class);

    private final AppleRedetectionDryRun dryRun;
    private final ConfigurableApplicationContext context;
    private final boolean exitAfterDryRun;

    public AppleRedetectionDryRunLogger(AppleRedetectionDryRun dryRun,
                                        ConfigurableApplicationContext context,
                                        @Value("${app.redetection.apple.exit-after-dry-run:false}") boolean exitAfterDryRun) {
        this.dryRun = dryRun;
        this.context = context;
        this.exitAfterDryRun = exitAfterDryRun;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        dryRun.run().summaryLines().forEach(line -> log.info("Apple re-detection dry run: {}", line));
        if (exitAfterDryRun)
            System.exit(SpringApplication.exit(context, () -> 0));
    }
}

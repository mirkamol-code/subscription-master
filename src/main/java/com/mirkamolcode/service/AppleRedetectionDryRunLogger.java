package com.mirkamolcode.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redetection.apple.dry-run-on-startup", havingValue = "true")
public class AppleRedetectionDryRunLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AppleRedetectionDryRunLogger.class);

    private final AppleRedetectionDryRun dryRun;

    public AppleRedetectionDryRunLogger(AppleRedetectionDryRun dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        dryRun.run().summaryLines().forEach(line -> log.info("Apple re-detection dry run: {}", line));
    }
}

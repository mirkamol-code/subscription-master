package com.mirkamolcode.service;

import com.mirkamolcode.service.AppleCleanUp.UserOutcome;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.redetection.apple.apply-on-startup", havingValue = "true")
public class AppleCleanUpRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AppleCleanUpRunner.class);

    private final AppleCleanUp cleanUp;
    private final ConfigurableApplicationContext context;

    public AppleCleanUpRunner(AppleCleanUp cleanUp, ConfigurableApplicationContext context) {
        this.cleanUp = cleanUp;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Map<UserOutcome, Long> counts = cleanUp.apply().values().stream()
                .collect(Collectors.groupingBy(outcome -> outcome, TreeMap::new, Collectors.counting()));
        log.info("Apple clean-up finished: {}", counts);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}

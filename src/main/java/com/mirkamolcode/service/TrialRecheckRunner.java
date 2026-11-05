package com.mirkamolcode.service;

import com.mirkamolcode.service.TrialRecheck.UserCounts;

import java.util.Collection;

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
@ConditionalOnProperty(name = "app.trial-recheck.on-startup", havingValue = "true")
public class TrialRecheckRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TrialRecheckRunner.class);

    private final TrialRecheck recheck;
    private final ConfigurableApplicationContext context;
    private final boolean apply;

    public TrialRecheckRunner(TrialRecheck recheck, ConfigurableApplicationContext context,
                              @Value("${app.trial-recheck.apply:false}") boolean apply) {
        this.recheck = recheck;
        this.context = context;
        this.apply = apply;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Collection<UserCounts> perUser = recheck.run(apply).values();
        log.info("Trial re-check ({}) for {} users: found {}, would record {}, recorded {}, skipped {}",
                apply ? "apply" : "dry run", perUser.size(),
                perUser.stream().mapToInt(UserCounts::found).sum(),
                perUser.stream().mapToInt(UserCounts::wouldRecord).sum(),
                perUser.stream().mapToInt(UserCounts::recorded).sum(),
                perUser.stream().mapToInt(UserCounts::skipped).sum());
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}

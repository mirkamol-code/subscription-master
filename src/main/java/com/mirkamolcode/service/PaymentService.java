package com.mirkamolcode.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final ExchangeRateService exchangeRates;
    private final boolean refreshOnStartup;

    public PaymentService(ExchangeRateService exchangeRates,
                          @Value("${app.exchange-rates.refresh-on-startup}") boolean refreshOnStartup) {
        this.exchangeRates = exchangeRates;
        this.refreshOnStartup = refreshOnStartup;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "refreshExchangeRates", lockAtMostFor = "PT30M")
    public void refreshExchangeRates() {
        exchangeRates.refreshDailyRates();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshExchangeRatesOnStartup() {
        if (!refreshOnStartup) return;
        try {
            exchangeRates.refreshDailyRates();
        } catch (RuntimeException exception) {
            log.error("Exchange-rate refresh on startup failed", exception);
        }
    }
}

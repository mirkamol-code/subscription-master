package com.mirkamolcode.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final ExchangeRateService exchangeRates;

    public PaymentService(ExchangeRateService exchangeRates) {
        this.exchangeRates = exchangeRates;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "refreshExchangeRates", lockAtMostFor = "PT30M")
    public void refreshExchangeRates() {
        exchangeRates.refreshDailyRates();
    }
}

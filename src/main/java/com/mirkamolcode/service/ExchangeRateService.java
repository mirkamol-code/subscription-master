package com.mirkamolcode.service;

import com.mirkamolcode.dto.CbuRate;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.integration.CbuRateClient;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.repository.ExchangeRateRepository;

import java.math.BigDecimal;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateService {
    private final CbuRateClient client;
    private final ExchangeRateRepository rates;

    public ExchangeRateService(CbuRateClient client, ExchangeRateRepository rates) {
        this.client = client;
        this.rates = rates;
    }

    @Cacheable(cacheNames = "exchange-rates", key = "#currency.name()")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal rateToUzs(CurrencyCode currency) {
        if (currency == CurrencyCode.UZS) return BigDecimal.ONE;
        try {
            CbuRate rate = client.find(currency);
            return rates.findByCurrencyAndRateDate(currency, rate.effectiveDate())
                    .map(ExchangeRate::getRateToUzs)
                    .orElseGet(
                            () -> rates.save(new ExchangeRate(currency, rate.rate(), rate.effectiveDate())).getRateToUzs());
        } catch (CbuRateClient.ExchangeRateUnavailableException exception) {
            return rates.findFirstByCurrencyOrderByRateDateDesc(currency)
                    .map(ExchangeRate::getRateToUzs)
                    .orElseThrow(() -> new IllegalStateException("No cached exchange rate available for " + currency, exception));
        }
    }

    @Transactional
    public void refreshDailyRates() {
        for (CurrencyCode currency : CurrencyCode.values()){
            rateToUzs(currency);
        }
    }
}

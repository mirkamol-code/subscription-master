package com.mirkamolcode.service;

import com.mirkamolcode.dto.CbuRate;
import com.mirkamolcode.dto.response.ExchangeRatesResponse;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.integration.CbuRateClient;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.repository.ExchangeRateRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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

    public ExchangeRatesResponse getCurrentRates() {
        Map<String, BigDecimal> ratesMap = new LinkedHashMap<>();
        ratesMap.put(CurrencyCode.USD.name(), rateToUzs(CurrencyCode.USD));
        ratesMap.put(CurrencyCode.EUR.name(), rateToUzs(CurrencyCode.EUR));
        ratesMap.put(CurrencyCode.UZS.name(), BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));

        Instant updatedAt = rates.findAll().stream()
                .map(ExchangeRate::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        return new ExchangeRatesResponse("UZS", ratesMap, updatedAt);
    }
}

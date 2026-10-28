package com.mirkamolcode.service;

import com.mirkamolcode.dto.CbuRate;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.integration.CbuRateClient;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.exception.ExchangeRatesUnavailableException;
import com.mirkamolcode.repository.ExchangeRateRepository;

import java.math.BigDecimal;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateService {
    static final String CACHE_NAME = "exchange-rates";
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final CbuRateClient client;
    private final ExchangeRateRepository rates;
    private final Cache liveRates;

    public ExchangeRateService(CbuRateClient client, ExchangeRateRepository rates, CacheManager cacheManager) {
        this.client = client;
        this.rates = rates;
        this.liveRates = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME), "Missing cache " + CACHE_NAME);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal rateToUzs(CurrencyCode currency) {
        if (currency == CurrencyCode.UZS) return new BigDecimal("1.00");
        BigDecimal cached = liveRates.get(currency.name(), BigDecimal.class);
        if (cached != null) return cached;
        try {
            return fetchLiveRate(currency);
        } catch (CbuRateClient.ExchangeRateUnavailableException exception) {
            return rates.findFirstByCurrencyOrderByRateDateDesc(currency)
                    .map(ExchangeRate::getRateToUzs)
                    .orElseThrow(() -> new ExchangeRatesUnavailableException("No cached exchange rate available for " + currency, exception));
        }
    }

    @Transactional
    public void refreshDailyRates() {
        for (CurrencyCode currency : CurrencyCode.values()) {
            if (currency == CurrencyCode.UZS) continue;
            try {
                fetchLiveRate(currency);
            } catch (CbuRateClient.ExchangeRateUnavailableException exception) {
                log.warn("Daily exchange rate refresh failed for {}", currency, exception);
            }
        }
    }

    private BigDecimal fetchLiveRate(CurrencyCode currency) {
        CbuRate rate = client.find(currency);
        BigDecimal rateToUzs = rates.findByCurrencyAndRateDate(currency, rate.effectiveDate())
                .map(ExchangeRate::getRateToUzs)
                .orElseGet(
                        () -> rates.save(new ExchangeRate(currency, rate.rate(), rate.effectiveDate())).getRateToUzs());
        liveRates.put(currency.name(), rateToUzs);
        return rateToUzs;
    }
}

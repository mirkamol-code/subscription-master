package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.ExchangeRatesResponse;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.repository.ExchangeRateRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CurrencyRatesService {
    private static final Map<CurrencyCode, BigDecimal> DEFAULT_RATES = Map.of(
            CurrencyCode.USD, new BigDecimal("12850.00"),
            CurrencyCode.EUR, new BigDecimal("13967.50"),
            CurrencyCode.UZS, new BigDecimal("1.00")
    );

    private final ExchangeRateService exchangeRates;
    private final ExchangeRateRepository rates;

    public CurrencyRatesService(ExchangeRateService exchangeRates, ExchangeRateRepository rates) {
        this.exchangeRates = exchangeRates;
        this.rates = rates;
    }

    public ExchangeRatesResponse getCurrentRates() {
        Map<String, BigDecimal> ratesMap = new LinkedHashMap<>();
        for (CurrencyCode currency : CurrencyCode.values()) {
            BigDecimal rate;
            try {
                rate = exchangeRates.rateToUzs(currency);
            } catch (Exception ex) {
                rate = rates.findFirstByCurrencyOrderByRateDateDesc(currency)
                        .map(ExchangeRate::getRateToUzs)
                        .orElseGet(() -> DEFAULT_RATES.getOrDefault(currency, new BigDecimal("1.00")));
            }
            ratesMap.put(currency.name(), rate);
        }

        Instant updatedAt = rates.findAll().stream()
                .map(ExchangeRate::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        return new ExchangeRatesResponse("UZS", ratesMap, updatedAt);
    }
}

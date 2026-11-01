package com.mirkamolcode.service;

import com.mirkamolcode.dto.SourceRate;
import com.mirkamolcode.integration.ExchangeRateSource;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.exception.ExchangeRatesUnavailableException;
import com.mirkamolcode.repository.ExchangeRateRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private static final String SOURCES_PAUSED_UNTIL = "SOURCES_PAUSED_UNTIL";
    private static final Duration SOURCES_PAUSE = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final List<ExchangeRateSource> sources;
    private final ExchangeRateRepository rates;
    private final Cache liveRates;
    private final Clock clock;

    public ExchangeRateService(List<ExchangeRateSource> sources, ExchangeRateRepository rates, CacheManager cacheManager, Clock clock) {
        this.sources = sources;
        this.rates = rates;
        this.liveRates = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME), "Missing cache " + CACHE_NAME);
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal rateToUzs(CurrencyCode currency) {
        if (currency == CurrencyCode.UZS) return new BigDecimal("1.00");
        BigDecimal cached = liveRates.get(currency.name(), BigDecimal.class);
        if (cached != null) return cached;
        if (!areSourcesPaused()) {
            Optional<BigDecimal> live = fetchLiveRate(currency);
            if (live.isPresent()) return live.get();
        }
        return rates.findFirstByCurrencyOrderByRateDateDesc(currency)
                .map(saved -> {
                    log.warn("Using saved {} rate from {}: no exchange-rate source answered", currency, saved.getRateDate());
                    return saved.getRateToUzs();
                })
                .orElseThrow(() -> new ExchangeRatesUnavailableException("No saved exchange rate available for " + currency));
    }

    @Transactional
    public void refreshDailyRates() {
        for (ExchangeRateSource source : sources) {
            if (fetchAndSave(source).isPresent()) {
                liveRates.evict(SOURCES_PAUSED_UNTIL);
                return;
            }
        }
        log.error("Exchange rates not refreshed: every source failed");
    }

    private Optional<BigDecimal> fetchLiveRate(CurrencyCode currency) {
        boolean anySourceAnswered = false;
        for (ExchangeRateSource source : sources) {
            Optional<Map<CurrencyCode, BigDecimal>> fetched = fetchAndSave(source);
            if (fetched.isEmpty()) continue;
            anySourceAnswered = true;
            BigDecimal rate = fetched.get().get(currency);
            if (rate != null) return Optional.of(rate);
        }
        if (!anySourceAnswered) pauseSources();
        return Optional.empty();
    }

    private boolean areSourcesPaused() {
        Instant pausedUntil = liveRates.get(SOURCES_PAUSED_UNTIL, Instant.class);
        return pausedUntil != null && clock.instant().isBefore(pausedUntil);
    }

    private void pauseSources() {
        Instant pausedUntil = clock.instant().plus(SOURCES_PAUSE);
        liveRates.put(SOURCES_PAUSED_UNTIL, pausedUntil);
        log.warn("Every exchange-rate source failed; using saved rates until {}", pausedUntil);
    }

    private Optional<Map<CurrencyCode, BigDecimal>> fetchAndSave(ExchangeRateSource source) {
        List<SourceRate> fetched;
        try {
            fetched = source.fetchAll();
        } catch (RuntimeException exception) {
            log.warn("Exchange-rate source {} failed: {}: {}", source.name(),
                    exception.getClass().getSimpleName(), exception.getMessage());
            return Optional.empty();
        }
        Map<CurrencyCode, BigDecimal> saved = new EnumMap<>(CurrencyCode.class);
        fetched.forEach(rate -> saved.put(rate.currency(), save(rate, source)));
        log.info("Fetched {} exchange rates from {}", saved.size(), source.name());
        return Optional.of(saved);
    }

    private BigDecimal save(SourceRate rate, ExchangeRateSource source) {
        ExchangeRate saved = rates.findByCurrencyAndRateDate(rate.currency(), rate.rateDate())
                .map(existing -> {
                    if (source.isOfficial()) existing.replaceRate(rate.rateToUzs());
                    return existing;
                })
                .orElseGet(() -> rates.save(new ExchangeRate(rate.currency(), rate.rateToUzs(), rate.rateDate())));
        liveRates.put(rate.currency().name(), saved.getRateToUzs());
        return saved.getRateToUzs();
    }
}

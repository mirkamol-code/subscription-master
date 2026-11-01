package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.dto.SourceRate;
import com.mirkamolcode.integration.CbuRateClient;
import com.mirkamolcode.integration.OpenErApiRateClient;
import com.mirkamolcode.model.CurrencyCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CurrencyControllerIT extends AbstractTestConfig {
    private static final SourceRate USD_12850 = new SourceRate(CurrencyCode.USD, new BigDecimal("12850.00"), LocalDate.of(2026, 10, 28));

    @MockitoBean
    private CbuRateClient cbuRateClient;
    @MockitoBean
    private OpenErApiRateClient openErApiRateClient;
    @Autowired
    private CacheManager cacheManager;

    @Test
    void getRates_shouldReturnCurrentRatesAndBaseCurrency() {
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12850.00"), LocalDate.now()));
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.EUR, new BigDecimal("13967.50"), LocalDate.now()));

        webTestClient.get()
                .uri(API + "/currencies/rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.baseCurrency").isEqualTo("UZS")
                .jsonPath("$.rates.USD").exists()
                .jsonPath("$.rates.EUR").exists()
                .jsonPath("$.rates.UZS").isEqualTo(1.0)
                .jsonPath("$.updatedAt").exists();
    }

    @Test
    void getRates_shouldCallCbuOnce_whenRequestedRepeatedly() {
        // given
        cacheManager.getCache("exchange-rates").clear();
        given(cbuRateClient.fetchAll()).willReturn(Arrays.stream(CurrencyCode.values())
                .filter(currency -> currency != CurrencyCode.UZS)
                .map(currency -> currency == CurrencyCode.USD ? USD_12850
                        : new SourceRate(currency, BigDecimal.TEN, LocalDate.of(2026, 10, 28)))
                .toList());

        // when
        for (int i = 0; i < 2; i++) {
            webTestClient.get()
                    .uri(API + "/currencies/rates")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.rates.USD").isEqualTo(12850.00);
        }

        // then
        verify(cbuRateClient, times(1)).fetchAll();
    }

    @Test
    void getRates_shouldUseFallbackSource_whenCbuIsDown() {
        // given
        cacheManager.getCache("exchange-rates").clear();
        given(cbuRateClient.fetchAll()).willThrow(new IllegalStateException("CBU down"));
        given(openErApiRateClient.fetchAll()).willReturn(List.of(
                new SourceRate(CurrencyCode.USD, new BigDecimal("12048.19"), LocalDate.of(2026, 11, 1))));

        // when / then
        webTestClient.get()
                .uri(API + "/currencies/rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rates.USD").isEqualTo(12048.19);
    }

    @Test
    void getRates_shouldServeSavedRates_withoutAskingSourcesAgain_whileTheyAreDown() {
        // given
        cacheManager.getCache("exchange-rates").clear();
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12000.00"), LocalDate.of(2026, 10, 27)));
        given(cbuRateClient.fetchAll()).willThrow(new IllegalStateException("CBU down"));
        given(openErApiRateClient.fetchAll()).willThrow(new IllegalStateException("open.er-api.com down"));

        // when
        for (int i = 0; i < 2; i++) {
            webTestClient.get()
                    .uri(API + "/currencies/rates")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.rates.USD").isEqualTo(12000.00);
        }

        // then
        verify(cbuRateClient, times(1)).fetchAll();
        verify(openErApiRateClient, times(1)).fetchAll();
    }
}

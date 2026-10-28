package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.CbuRate;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.integration.CbuRateClient;
import com.mirkamolcode.model.CurrencyCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CurrencyControllerIT extends AbstractTestConfig {

    @MockitoBean
    private CbuRateClient cbuRateClient;
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
    void getRates_shouldCallCbuOncePerCurrency_whenRequestedRepeatedly() {
        // given
        cacheManager.getCache("exchange-rates").clear();
        given(cbuRateClient.find(CurrencyCode.USD)).willReturn(new CbuRate("USD", new BigDecimal("12850.00"), "28.10.2026"));
        given(cbuRateClient.find(CurrencyCode.EUR)).willReturn(new CbuRate("EUR", new BigDecimal("13967.50"), "28.10.2026"));

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
        verify(cbuRateClient, times(1)).find(CurrencyCode.USD);
        verify(cbuRateClient, times(1)).find(CurrencyCode.EUR);
    }

    @Test
    void getRates_shouldUseLiveRateAgain_onceCbuRecovers() {
        // given CBU is down for the first request and a USD rate was stored earlier
        cacheManager.getCache("exchange-rates").clear();
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12000.00"), LocalDate.of(2026, 10, 27)));
        given(cbuRateClient.find(CurrencyCode.USD))
                .willThrow(new CbuRateClient.ExchangeRateUnavailableException(new RuntimeException("CBU down")))
                .willReturn(new CbuRate("USD", new BigDecimal("12850.00"), "28.10.2026"));
        given(cbuRateClient.find(CurrencyCode.EUR)).willReturn(new CbuRate("EUR", new BigDecimal("13967.50"), "28.10.2026"));

        webTestClient.get()
                .uri(API + "/currencies/rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rates.USD").isEqualTo(12000.00);

        // when / then CBU has recovered
        webTestClient.get()
                .uri(API + "/currencies/rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rates.USD").isEqualTo(12850.00);
    }
}

package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.model.CurrencyCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

class CurrencyControllerIT extends AbstractTestConfig {

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
}

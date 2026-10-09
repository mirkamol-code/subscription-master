package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import org.junit.jupiter.api.Test;

class CurrencyControllerIT extends AbstractTestConfig {

    @Test
    void getRates_shouldReturnCurrentRatesAndBaseCurrency() {
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

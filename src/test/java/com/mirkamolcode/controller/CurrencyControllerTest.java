package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.ExchangeRatesResponse;
import com.mirkamolcode.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyControllerTest {

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private CurrencyController underTest;

    @Test
    void getRates_shouldReturnExchangeRatesResponse() {
        // Given
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        ExchangeRatesResponse response = new ExchangeRatesResponse(
                "UZS",
                Map.of("USD", new BigDecimal("12850.00"), "EUR", new BigDecimal("13967.50"), "UZS", BigDecimal.ONE),
                now
        );
        when(exchangeRateService.getCurrentRates()).thenReturn(response);

        // When
        ExchangeRatesResponse result = underTest.getRates();

        // Then
        assertThat(result.baseCurrency()).isEqualTo("UZS");
        assertThat(result.rates()).containsEntry("USD", new BigDecimal("12850.00"));
        assertThat(result.rates()).containsEntry("EUR", new BigDecimal("13967.50"));
        assertThat(result.rates()).containsEntry("UZS", BigDecimal.ONE);
        assertThat(result.updatedAt()).isEqualTo(now);
        verify(exchangeRateService).getCurrentRates();
    }
}

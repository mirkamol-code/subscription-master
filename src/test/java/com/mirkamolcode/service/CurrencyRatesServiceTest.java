package com.mirkamolcode.service;

import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyRatesServiceTest {
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private ExchangeRateRepository rateRepository;
    @InjectMocks
    private CurrencyRatesService underTest;

    @Test
    void getCurrentRates_shouldReturnAllRatesAndBaseCurrency() {
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenReturn(new BigDecimal("12850.00"));
        when(exchangeRateService.rateToUzs(CurrencyCode.EUR)).thenReturn(new BigDecimal("13967.50"));
        when(exchangeRateService.rateToUzs(CurrencyCode.UZS)).thenReturn(new BigDecimal("1.00"));

        var result = underTest.getCurrentRates();

        assertThat(result.baseCurrency()).isEqualTo("UZS");
        assertThat(result.rates()).containsEntry("USD", new BigDecimal("12850.00"));
        assertThat(result.rates()).containsEntry("EUR", new BigDecimal("13967.50"));
        assertThat(result.rates()).containsEntry("UZS", new BigDecimal("1.00"));
        assertThat(result.updatedAt()).isNotNull();
    }

    @Test
    void getCurrentRates_shouldFallBackToLatestStoredRate_whenLiveRateFails() {
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenThrow(new IllegalStateException("CBU down"));
        when(exchangeRateService.rateToUzs(CurrencyCode.EUR)).thenReturn(new BigDecimal("13967.50"));
        when(exchangeRateService.rateToUzs(CurrencyCode.UZS)).thenReturn(new BigDecimal("1.00"));
        when(rateRepository.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.USD))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12700.00"), LocalDate.of(2026, 10, 27))));
        when(rateRepository.findAll()).thenReturn(List.of());

        var result = underTest.getCurrentRates();

        assertThat(result.rates()).containsEntry("USD", new BigDecimal("12700.00"));
    }
}

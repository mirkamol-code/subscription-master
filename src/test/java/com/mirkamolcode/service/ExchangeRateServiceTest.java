package com.mirkamolcode.service;

import com.mirkamolcode.dto.CbuRate;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.integration.CbuRateClient;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {
    @Mock
    private CbuRateClient client;
    @Mock
    private ExchangeRateRepository rateRepository;
    @InjectMocks
    private ExchangeRateService underTest;

    @Test
    void rateToUzs_shouldReturnOne_withoutExternalCallForBaseCurrency() {
        assertThat(underTest.rateToUzs(CurrencyCode.UZS)).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(client, rateRepository);
    }

    @Test
    void rateToUzs_shouldReusePersistedRate_whenEffectiveDateAlreadyExists() {
        // Given
        CbuRate cbuRate = new CbuRate("USD", new BigDecimal("12500"), "21.08.2026");
        ExchangeRate persisted = new ExchangeRate(CurrencyCode.USD, new BigDecimal("12490"), LocalDate.of(2026, 8, 21));
        when(client.find(CurrencyCode.USD)).thenReturn(cbuRate);
        when(rateRepository.findByCurrencyAndRateDate(CurrencyCode.USD, LocalDate.of(2026, 8, 21)))
                .thenReturn(Optional.of(persisted));

        // When
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // Then
        assertThat(result).isEqualByComparingTo("12490");
        verify(rateRepository, never()).save(any());
    }

    @Test
    void rateToUzs_shouldPersistRate_whenExternalRateIsNew() {
        // Given
        CbuRate cbuRate = new CbuRate("EUR", new BigDecimal("14500"), "21.08.2026");
        when(client.find(CurrencyCode.EUR)).thenReturn(cbuRate);
        when(rateRepository.findByCurrencyAndRateDate(CurrencyCode.EUR, LocalDate.of(2026, 8, 21)))
                .thenReturn(Optional.empty());
        when(rateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BigDecimal result = underTest.rateToUzs(CurrencyCode.EUR);

        // Then
        assertThat(result).isEqualByComparingTo("14500");
        verify(rateRepository).save(argThat(rate ->
                rate.getCurrency() == CurrencyCode.EUR
                        && rate.getRateDate().equals(LocalDate.of(2026, 8, 21))));
    }

    @Test
    void rateToUzs_shouldUseLatestPersistedRate_whenExternalApiFails() {
        // Given
        when(client.find(CurrencyCode.USD)).thenThrow(
                new CbuRateClient.ExchangeRateUnavailableException(new RuntimeException("CBU unavailable")));
        when(rateRepository.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.USD))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12345"), LocalDate.now())));

        // When
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // Then
        assertThat(result).isEqualByComparingTo("12345");
    }

    @Test
    void rateToUzs_shouldFailClearly_whenExternalAndPersistedRatesAreUnavailable() {
        // Given
        when(client.find(CurrencyCode.USD)).thenThrow(
                new CbuRateClient.ExchangeRateUnavailableException(new RuntimeException("CBU unavailable")));
        when(rateRepository.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.USD)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> underTest.rateToUzs(CurrencyCode.USD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No cached exchange rate available for USD");
    }

    @Test
    void refreshDailyRates_shouldRefreshEveryNonBaseCurrency() {
        // Given
        when(client.find(CurrencyCode.USD)).thenReturn(new CbuRate("USD", BigDecimal.TEN, "21.08.2026"));
        when(client.find(CurrencyCode.EUR)).thenReturn(new CbuRate("EUR", BigDecimal.TEN, "21.08.2026"));
        when(rateRepository.findByCurrencyAndRateDate(any(), any())).thenReturn(Optional.of(
                new ExchangeRate(CurrencyCode.USD, BigDecimal.TEN, LocalDate.of(2026, 8, 21))));

        // When
        underTest.refreshDailyRates();

        // Then
        verify(client).find(CurrencyCode.USD);
        verify(client).find(CurrencyCode.EUR);
    }

    @Test
    void getCurrentRates_shouldReturnAllRatesAndBaseCurrency() {
        // Given
        CbuRate usdRate = new CbuRate("USD", new BigDecimal("12850.00"), "21.08.2026");
        CbuRate eurRate = new CbuRate("EUR", new BigDecimal("13967.50"), "21.08.2026");
        when(client.find(CurrencyCode.USD)).thenReturn(usdRate);
        when(client.find(CurrencyCode.EUR)).thenReturn(eurRate);
        when(rateRepository.findByCurrencyAndRateDate(eq(CurrencyCode.USD), any()))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12850.00"), LocalDate.of(2026, 8, 21))));
        when(rateRepository.findByCurrencyAndRateDate(eq(CurrencyCode.EUR), any()))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.EUR, new BigDecimal("13967.50"), LocalDate.of(2026, 8, 21))));

        // When
        var result = underTest.getCurrentRates();

        // Then
        assertThat(result.baseCurrency()).isEqualTo("UZS");
        assertThat(result.rates()).containsEntry("USD", new BigDecimal("12850.00"));
        assertThat(result.rates()).containsEntry("EUR", new BigDecimal("13967.50"));
        assertThat(result.rates()).containsEntry("UZS", new BigDecimal("1.00"));
        assertThat(result.updatedAt()).isNotNull();
    }
}

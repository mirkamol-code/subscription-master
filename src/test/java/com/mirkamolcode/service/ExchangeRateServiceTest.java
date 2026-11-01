package com.mirkamolcode.service;

import com.mirkamolcode.MutableClock;
import com.mirkamolcode.dto.SourceRate;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.exception.ExchangeRatesUnavailableException;
import com.mirkamolcode.integration.ExchangeRateSource;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 10, 31);

    @Mock
    private ExchangeRateSource primary;
    @Mock
    private ExchangeRateSource fallback;
    @Mock
    private ExchangeRateRepository rateRepository;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T09:00:00Z"));
    private ExchangeRateService underTest;

    @BeforeEach
    void setUp() {
        underTest = new ExchangeRateService(List.of(primary, fallback), rateRepository,
                new ConcurrentMapCacheManager(ExchangeRateService.CACHE_NAME), clock);
    }

    @Test
    void rateToUzs_shouldReturnOne_withoutExternalCallForBaseCurrency() {
        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.UZS);

        // then
        assertThat(result).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(primary, fallback, rateRepository);
    }

    @Test
    void rateToUzs_shouldUsePrimarySource_andNeverAskTheFallback_whenPrimaryWorks() {
        // given
        when(primary.fetchAll()).thenReturn(List.of(usd("12023.46")));
        storesNewRates();

        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // then
        assertThat(result).isEqualByComparingTo("12023.46");
        verify(rateRepository).save(argThat(rate ->
                rate.getCurrency() == CurrencyCode.USD && rate.getRateDate().equals(RATE_DATE)));
        verifyNoInteractions(fallback);
    }

    @Test
    void rateToUzs_shouldUseFallbackSource_whenPrimaryFails() {
        // given
        when(primary.fetchAll()).thenThrow(new IllegalStateException("cbu.uz timed out"));
        when(fallback.fetchAll()).thenReturn(List.of(usd("12048.19")));
        storesNewRates();

        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // then
        assertThat(result).isEqualByComparingTo("12048.19");
        verify(rateRepository).save(argThat(rate -> rate.getRateToUzs().compareTo(new BigDecimal("12048.19")) == 0));
    }

    @Test
    void rateToUzs_shouldUseFallbackSource_whenPrimaryLacksTheCurrency() {
        // given
        when(primary.fetchAll()).thenReturn(List.of());
        when(fallback.fetchAll()).thenReturn(List.of(usd("12048.19")));
        storesNewRates();

        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // then
        assertThat(result).isEqualByComparingTo("12048.19");
    }

    @Test
    void rateToUzs_shouldKeepSavedRate_whenFallbackSendsRateForTheSameDate() {
        // given
        when(primary.fetchAll()).thenThrow(new IllegalStateException("cbu.uz timed out"));
        when(fallback.fetchAll()).thenReturn(List.of(usd("12500")));
        when(rateRepository.findByCurrencyAndRateDate(CurrencyCode.USD, RATE_DATE))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12490"), RATE_DATE)));

        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // then
        assertThat(result).isEqualByComparingTo("12490");
        verify(rateRepository, never()).save(any());
    }

    @Test
    void refreshDailyRates_shouldReplaceFallbackRate_withCbuRateForTheSameDate() {
        // given
        ExchangeRate savedFromFallback = new ExchangeRate(CurrencyCode.USD, new BigDecimal("12048.19"), RATE_DATE);
        when(primary.isOfficial()).thenReturn(true);
        when(primary.fetchAll()).thenReturn(List.of(usd("12023.46")));
        when(rateRepository.findByCurrencyAndRateDate(CurrencyCode.USD, RATE_DATE)).thenReturn(Optional.of(savedFromFallback));

        // when
        underTest.refreshDailyRates();

        // then
        assertThat(savedFromFallback.getRateToUzs()).isEqualByComparingTo("12023.46");
        assertThat(underTest.rateToUzs(CurrencyCode.USD)).isEqualByComparingTo("12023.46");
    }

    @Test
    void rateToUzs_shouldUseLatestSavedRate_whenEverySourceFails() {
        // given
        when(primary.fetchAll()).thenThrow(new IllegalStateException("cbu.uz timed out"));
        when(fallback.fetchAll()).thenThrow(new IllegalStateException("open.er-api.com unreachable"));
        when(rateRepository.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.USD))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12345"), RATE_DATE)));

        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // then
        assertThat(result).isEqualByComparingTo("12345");
    }

    @Test
    void rateToUzs_shouldFailClearly_whenNoSourceAndNoSavedRateIsAvailable() {
        // given
        when(primary.fetchAll()).thenThrow(new IllegalStateException("cbu.uz timed out"));
        when(fallback.fetchAll()).thenThrow(new IllegalStateException("open.er-api.com unreachable"));
        when(rateRepository.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.USD)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> underTest.rateToUzs(CurrencyCode.USD))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("No saved exchange rate available for USD");
    }

    @Test
    void rateToUzs_shouldUseSavedRateWithoutAskingSources_forFiveMinutesAfterEverySourceFailed() {
        // given
        when(primary.fetchAll()).thenThrow(new IllegalStateException("cbu.uz timed out"));
        when(fallback.fetchAll()).thenThrow(new IllegalStateException("open.er-api.com unreachable"));
        when(rateRepository.findFirstByCurrencyOrderByRateDateDesc(any()))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12345"), RATE_DATE)));
        underTest.rateToUzs(CurrencyCode.USD);
        clock.advance(Duration.ofMinutes(4));

        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.EUR);

        // then
        assertThat(result).isEqualByComparingTo("12345");
        verify(primary, times(1)).fetchAll();
        verify(fallback, times(1)).fetchAll();
    }

    @Test
    void rateToUzs_shouldAskSourcesAgain_oncePauseIsOver() {
        // given
        when(primary.fetchAll())
                .thenThrow(new IllegalStateException("cbu.uz timed out"))
                .thenReturn(List.of(usd("12023.46")));
        when(fallback.fetchAll()).thenThrow(new IllegalStateException("open.er-api.com unreachable"));
        when(rateRepository.findFirstByCurrencyOrderByRateDateDesc(CurrencyCode.USD))
                .thenReturn(Optional.of(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12345"), RATE_DATE)));
        storesNewRates();
        underTest.rateToUzs(CurrencyCode.USD);
        clock.advance(Duration.ofMinutes(6));

        // when
        BigDecimal result = underTest.rateToUzs(CurrencyCode.USD);

        // then
        assertThat(result).isEqualByComparingTo("12023.46");
    }

    @Test
    void rateToUzs_shouldServeEveryCurrencyFromOneFetch() {
        // given
        when(primary.fetchAll()).thenReturn(List.of(usd("12023.46"),
                new SourceRate(CurrencyCode.EUR, new BigDecimal("13461.47"), RATE_DATE)));
        storesNewRates();

        // when
        underTest.rateToUzs(CurrencyCode.USD);
        BigDecimal euro = underTest.rateToUzs(CurrencyCode.EUR);

        // then
        assertThat(euro).isEqualByComparingTo("13461.47");
        verify(primary, times(1)).fetchAll();
    }

    @Test
    void refreshDailyRates_shouldSaveRatesFromPrimary_withOneFetch() {
        // given
        when(primary.fetchAll()).thenReturn(List.of(usd("12023.46"),
                new SourceRate(CurrencyCode.EUR, new BigDecimal("13461.47"), RATE_DATE)));
        storesNewRates();

        // when
        underTest.refreshDailyRates();

        // then
        verify(primary, times(1)).fetchAll();
        verify(rateRepository, times(2)).save(any(ExchangeRate.class));
        verifyNoInteractions(fallback);
    }

    @Test
    void refreshDailyRates_shouldSaveRatesFromFallback_whenPrimaryFails() {
        // given
        when(primary.fetchAll()).thenThrow(new IllegalStateException("cbu.uz timed out"));
        when(fallback.fetchAll()).thenReturn(List.of(usd("12048.19")));
        storesNewRates();

        // when
        underTest.refreshDailyRates();

        // then
        verify(rateRepository).save(argThat(rate -> rate.getRateToUzs().compareTo(new BigDecimal("12048.19")) == 0));
    }

    @Test
    void refreshDailyRates_shouldNotThrow_whenEverySourceFails() {
        // given
        when(primary.fetchAll()).thenThrow(new IllegalStateException("cbu.uz timed out"));
        when(fallback.fetchAll()).thenThrow(new IllegalStateException("open.er-api.com unreachable"));

        // when
        underTest.refreshDailyRates();

        // then
        verify(rateRepository, never()).save(any());
    }

    private static SourceRate usd(String rateToUzs) {
        return new SourceRate(CurrencyCode.USD, new BigDecimal(rateToUzs), RATE_DATE);
    }

    private void storesNewRates() {
        when(rateRepository.findByCurrencyAndRateDate(any(), any())).thenReturn(Optional.empty());
        when(rateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}

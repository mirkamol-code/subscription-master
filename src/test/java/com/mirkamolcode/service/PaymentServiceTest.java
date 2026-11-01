package com.mirkamolcode.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private ExchangeRateService exchangeRateService;

    @Test
    void refreshExchangeRates_shouldDelegateToExchangeRateService() {
        // given
        PaymentService underTest = new PaymentService(exchangeRateService, true);

        // when
        underTest.refreshExchangeRates();

        // then
        verify(exchangeRateService).refreshDailyRates();
    }

    @Test
    void refreshExchangeRatesOnStartup_shouldRefresh_soAFreshDatabaseHasRatesToFallBackOn() {
        // given
        PaymentService underTest = new PaymentService(exchangeRateService, true);

        // when
        underTest.refreshExchangeRatesOnStartup();

        // then
        verify(exchangeRateService).refreshDailyRates();
    }

    @Test
    void refreshExchangeRatesOnStartup_shouldNotStopTheApp_whenRefreshFails() {
        // given
        PaymentService underTest = new PaymentService(exchangeRateService, true);
        doThrow(new IllegalStateException("database unavailable")).when(exchangeRateService).refreshDailyRates();

        // when / then
        assertThatCode(underTest::refreshExchangeRatesOnStartup).doesNotThrowAnyException();
    }

    @Test
    void refreshExchangeRatesOnStartup_shouldSkip_whenDisabled() {
        // given
        PaymentService underTest = new PaymentService(exchangeRateService, false);

        // when
        underTest.refreshExchangeRatesOnStartup();

        // then
        verifyNoInteractions(exchangeRateService);
    }
}

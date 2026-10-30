package com.mirkamolcode.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private ExchangeRateService exchangeRateService;
    @InjectMocks
    private PaymentService underTest;

    @Test
    void refreshExchangeRates_shouldDelegateToExchangeRateService() {
        // when
        underTest.refreshExchangeRates();

        // then
        verify(exchangeRateService).refreshDailyRates();
    }
}

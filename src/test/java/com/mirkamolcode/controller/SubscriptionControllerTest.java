package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.CalendarResponse;
import com.mirkamolcode.dto.response.PaymentHistoryResponse;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private SubscriptionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(subscriptionController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleResponse = new SubscriptionResponse(
                1L,
                "Netflix",
                new BigDecimal("12.99"),
                CurrencyCode.USD,
                BillingFrequency.MONTHLY,
                SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 15),
                0L
        );
    }

    @Test
    void list_withNameAndCategoryAndDateRange_shouldReturnFilteredPage() throws Exception {
        when(subscriptionService.list(
                eq(SubscriptionStatus.ACTIVE),
                eq(CurrencyCode.USD),
                eq(SubscriptionCategory.ENTERTAINMENT),
                eq("Net"),
                eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 31)),
                any(),
                any(),
                any()
        )).thenReturn(new PageImpl<>(List.of(sampleResponse), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/subscriptions")
                        .param("status", "ACTIVE")
                        .param("currency", "USD")
                        .param("category", "ENTERTAINMENT")
                        .param("name", "Net")
                        .param("nextPaymentDateFrom", "2026-10-01")
                        .param("nextPaymentDateTo", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Netflix"))
                .andExpect(jsonPath("$.content[0].category").value("ENTERTAINMENT"));
    }

    @Test
    void listAll_shouldReturnListDirectly() throws Exception {
        when(subscriptionService.listAll(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/subscriptions/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Netflix"));
    }

    @Test
    void upcoming_shouldReturnUpcomingRenewals() throws Exception {
        when(subscriptionService.upcoming(7)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/subscriptions/upcoming").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Netflix"));
    }

    @Test
    void calendar_shouldReturnCalendarGroupedData() throws Exception {
        CalendarResponse calendarResponse = new CalendarResponse(
                2026,
                10,
                new BigDecimal("166921.50"),
                "UZS",
                List.of(new CalendarResponse.CalendarDayGroup(
                        LocalDate.of(2026, 10, 15),
                        new BigDecimal("166921.50"),
                        List.of(sampleResponse)
                ))
        );

        when(subscriptionService.calendar(2026, 10)).thenReturn(calendarResponse);

        mockMvc.perform(get("/subscriptions/calendar")
                        .param("year", "2026")
                        .param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(10))
                .andExpect(jsonPath("$.currency").value("UZS"))
                .andExpect(jsonPath("$.days[0].dayTotalUzs").value(166921.50));
    }

    @Test
    void pause_shouldReturnPausedSubscription() throws Exception {
        SubscriptionResponse paused = new SubscriptionResponse(
                1L, "Netflix", new BigDecimal("12.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.PAUSED,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 15), 0L
        );
        when(subscriptionService.pause(1L)).thenReturn(paused);

        mockMvc.perform(post("/subscriptions/1/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void resume_shouldReturnResumedSubscription() throws Exception {
        when(subscriptionService.resume(1L)).thenReturn(sampleResponse);

        mockMvc.perform(post("/subscriptions/1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void history_shouldReturnPaymentHistory() throws Exception {
        PaymentHistoryResponse hist = new PaymentHistoryResponse(
                10L, LocalDate.of(2026, 9, 15), new BigDecimal("12.99"), CurrencyCode.USD,
                new BigDecimal("166921.50"), new BigDecimal("12850.00")
        );
        when(subscriptionService.getHistory(1L)).thenReturn(List.of(hist));

        mockMvc.perform(get("/subscriptions/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(12.99))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }
}

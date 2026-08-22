package com.mirkamolcode.service;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.SubscriptionRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @InjectMocks
    private ReportService underTest;

    private User user;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        user = new User("person@example.com", "hash", Set.of(Role.USER));
        ReflectionTestUtils.setField(user, "id", 1L);
        subscription = new Subscription(user, "Netflix, Premium", new BigDecimal("10"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 10));
    }

    @Test
    void csv_shouldExportOwnedActiveSubscriptionsWithAnnualCost() {
        // Given
        when(currentUserService.requiredUser()).thenReturn(user);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(subscription));
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenReturn(new BigDecimal("12500"));

        // When
        String csv = new String(underTest.csv(), StandardCharsets.UTF_8);

        // Then
        assertThat(csv).contains("Subscription,Monthly price,Base currency equivalent,Annual cost UZS");
        assertThat(csv).contains("\"Netflix, Premium\",10 USD,125000.00,1500000.00");
    }

    @Test
    void csv_shouldReturnOnlyHeader_whenUserHasNoSubscriptions() {
        when(currentUserService.requiredUser()).thenReturn(user);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        String csv = new String(underTest.csv(), StandardCharsets.UTF_8);

        assertThat(csv.lines()).hasSize(1);
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void xlsx_shouldGenerateReadableWorkbookWithCalculatedValues() throws Exception {
        // Given
        when(currentUserService.requiredUser()).thenReturn(user);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(subscription));
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenReturn(new BigDecimal("12500"));

        // When
        byte[] bytes = underTest.xlsx();

        // Then
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Subscription");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Netflix, Premium");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(125000d);
            assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(1500000d);
        }
    }
}

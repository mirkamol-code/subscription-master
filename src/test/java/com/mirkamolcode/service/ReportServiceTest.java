package com.mirkamolcode.service;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.ReportColumn;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.SubscriptionRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        user = new User("person@example.com", Set.of(Role.USER));
        ReflectionTestUtils.setField(user, "id", 1L);
        subscription = new Subscription(user, "Netflix, Premium", new BigDecimal("10"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 10));
    }

    @Test
    void csv_shouldExportEveryColumnWithReadableLabels_whenAllColumnsAreChosen() {
        // given
        givenSubscriptions(subscription);
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenReturn(new BigDecimal("12500"));

        // when
        String csv = new String(underTest.csv(Arrays.asList(ReportColumn.values())), StandardCharsets.UTF_8);

        // then
        assertThat(csv.lines()).containsExactly(
                "Service,Category,Status,Billing,Price,Currency,Yearly cost,Yearly cost (UZS),Started,Next payment",
                "\"Netflix, Premium\",Entertainment,Active,Monthly,10.00,USD,120.00,1500000.00,2026-01-10,2026-02-10");
    }

    @Test
    void csv_shouldExportOnlyChosenColumns_inTheChosenOrder() {
        // given
        givenSubscriptions(subscription);

        // when
        String csv = new String(underTest.csv(List.of(ReportColumn.PRICE, ReportColumn.SERVICE)), StandardCharsets.UTF_8);

        // then
        assertThat(csv.lines()).containsExactly("Price,Service", "10.00,\"Netflix, Premium\"");
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void csv_shouldReturnOnlyHeader_whenUserHasNoSubscriptions() {
        // given
        givenSubscriptions();

        // when
        String csv = new String(underTest.csv(Arrays.asList(ReportColumn.values())), StandardCharsets.UTF_8);

        // then
        assertThat(csv.lines()).hasSize(1);
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void csv_shouldRejectExport_whenNoColumnIsChosen() {
        // when / then
        assertThatThrownBy(() -> underTest.csv(List.of())).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Choose at least one report column");
        verifyNoInteractions(subscriptionRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://example.com\")", "+SUM(1,2)", "-2+3", "@cmd", "\tTAB", "\rCR"})
    void csv_shouldNeutralizeFormulaInjection_inTextCells(String name) {
        // given
        givenSubscriptions(new Subscription(user, name, new BigDecimal("10"), CurrencyCode.USD, BillingFrequency.MONTHLY,
                SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, LocalDate.of(2026, 1, 10)));

        // when
        String csv = new String(underTest.csv(List.of(ReportColumn.SERVICE)), StandardCharsets.UTF_8);

        // then
        assertThat(csv.substring(csv.indexOf('\n') + 1)).startsWith("\"'");
    }

    @Test
    void xlsx_shouldWriteChosenColumns_withNumbersAsNumericCells() throws Exception {
        // given
        givenSubscriptions(subscription);
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenReturn(new BigDecimal("12500"));

        // when
        byte[] bytes = underTest.xlsx(List.of(ReportColumn.SERVICE, ReportColumn.YEARLY_COST_UZS));

        // then
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 2);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Yearly cost (UZS)");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Netflix, Premium");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1500000d);
        }
    }

    @Test
    void xlsx_shouldNeutralizeFormulaInjection_inTextCells() throws Exception {
        // given
        givenSubscriptions(new Subscription(user, "=1+1", new BigDecimal("10"), CurrencyCode.USD, BillingFrequency.MONTHLY,
                SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, LocalDate.of(2026, 1, 10)));

        // when
        byte[] bytes = underTest.xlsx(List.of(ReportColumn.SERVICE));

        // then
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var cell = workbook.getSheetAt(0).getRow(1).getCell(0);
            assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(cell.getStringCellValue()).isEqualTo("'=1+1");
        }
    }

    private void givenSubscriptions(Subscription... found) {
        when(currentUserService.requiredUser()).thenReturn(user);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(found));
    }
}

package com.mirkamolcode.service;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.model.ReportColumn;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.specification.SubscriptionSpecifications;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final String SHEET_NAME = "Subscriptions";
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private final SubscriptionRepository subscriptions;
    private final CurrentUserService currentUser;
    private final ExchangeRateService rates;

    public ReportService(SubscriptionRepository subscriptions, CurrentUserService currentUser, ExchangeRateService rates) {
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
        this.rates = rates;
    }

    public byte[] csv(List<ReportColumn> columns) {
        requireAnyColumn(columns);
        StringBuilder out = new StringBuilder(columns.stream().map(ReportColumn::header).collect(Collectors.joining(","))).append('\n');
        for (Subscription subscription : findReportedSubscriptions())
            out.append(columns.stream().map(column -> toCsvValue(cellOf(column, subscription))).collect(Collectors.joining(","))).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx(List<ReportColumn> columns) {
        requireAnyColumn(columns);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(SHEET_NAME);
            var header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++)
                header.createCell(i).setCellValue(columns.get(i).header());
            int rowIndex = 1;
            for (Subscription subscription : findReportedSubscriptions()) {
                var row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = cellOf(columns.get(i), subscription);
                    if (value instanceof BigDecimal number)
                        row.createCell(i).setCellValue(number.doubleValue());
                    else
                        row.createCell(i).setCellValue(neutralizeFormula(Objects.toString(value, "")));
                }
            }
            for (int i = 0; i < columns.size(); i++)
                sheet.autoSizeColumn(i);
            workbook.write(stream);
            return stream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void requireAnyColumn(List<ReportColumn> columns) {
        if (columns.isEmpty() || columns.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Choose at least one report column");
    }

    private List<Subscription> findReportedSubscriptions() {
        return subscriptions.findAll(SubscriptionSpecifications.ownedBy(currentUser.requiredUser().getId())
                .and(SubscriptionSpecifications.visible()));
    }

    private Object cellOf(ReportColumn column, Subscription subscription) {
        return switch (column) {
            case SERVICE -> subscription.getName();
            case CATEGORY -> subscription.getCategory().label();
            case STATUS -> subscription.getStatus().label();
            case BILLING -> subscription.getFrequency().label();
            case PRICE -> roundMoney(subscription.getPrice());
            case CURRENCY -> subscription.getCurrency().name();
            case YEARLY_COST -> roundMoney(calculateYearlyCost(subscription));
            case YEARLY_COST_UZS -> roundMoney(calculateYearlyCost(subscription).multiply(rates.rateToUzs(subscription.getCurrency())));
            case STARTED -> formatDate(subscription.getStartDate());
            case NEXT_PAYMENT -> formatDate(subscription.getNextPaymentDate());
        };
    }

    private static BigDecimal calculateYearlyCost(Subscription subscription) {
        return subscription.getPrice().multiply(BigDecimal.valueOf(subscription.getFrequency().annualOccurrences()));
    }

    private static BigDecimal roundMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String toCsvValue(Object value) {
        if (value instanceof BigDecimal number)
            return number.toPlainString();
        String text = neutralizeFormula(Objects.toString(value, ""));
        boolean needsQuotes = text.startsWith("'") || text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        return needsQuotes ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static String neutralizeFormula(String text) {
        return !text.isEmpty() && FORMULA_TRIGGERS.indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }
}

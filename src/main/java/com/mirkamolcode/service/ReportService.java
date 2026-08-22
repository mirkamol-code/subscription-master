package com.mirkamolcode.service;

import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.specification.SubscriptionSpecifications;

import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private final SubscriptionRepository subscriptions;
    private final CurrentUserService currentUser;
    private final ExchangeRateService rates;

    public ReportService(SubscriptionRepository subscriptions, CurrentUserService currentUser, ExchangeRateService rates) {
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
        this.rates = rates;
    }

    public byte[] csv() {
        StringBuilder out = new StringBuilder("Subscription,Monthly price,Base currency equivalent,Annual cost UZS\n");
        items().forEach(s -> {
            BigDecimal monthly = monthly(s);
            out.append(escape(s.getName())).append(',').append(s.getPrice()).append(' ').append(s.getCurrency()).append(',').append(monthly).append(',').append(monthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP)).append('\n');
        });
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Annual " + Year.now());
            String[] headers = {"Subscription", "Monthly price", "Base currency equivalent (UZS)", "Annual cost (UZS)"};
            var row = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(headers[i]);
            int n = 1;
            for (Subscription s : items()) {
                var r = row = sheet.createRow(n++);
                BigDecimal monthly = monthly(s);
                r.createCell(0).setCellValue(s.getName());
                r.createCell(1).setCellValue(s.getPrice().doubleValue());
                r.createCell(2).setCellValue(monthly.doubleValue());
                r.createCell(3).setCellValue(monthly.multiply(BigDecimal.valueOf(12)).doubleValue());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(stream);
            return stream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Subscription> items() {
        return subscriptions.findAll(SubscriptionSpecifications.ownedBy(currentUser.requiredUser().getId()).and(SubscriptionSpecifications.visible()).and(SubscriptionSpecifications.hasStatus(SubscriptionStatus.ACTIVE)));
    }

    private BigDecimal monthly(Subscription s) {
        return s.getPrice().multiply(s.getFrequency().monthlyMultiplier()).multiply(rates.rateToUzs(s.getCurrency())).setScale(2, RoundingMode.HALF_UP);
    }

    private String escape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

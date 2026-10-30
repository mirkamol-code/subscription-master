package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class NetflixParser implements EmailParser {
    private static final Pattern AMOUNT = Pattern.compile("(?i)amount charged:?\\s*(?<symbol>[$€£])\\s?(?<number>\\d+(?:\\.\\d{2})?)");
    private static final Pattern NEXT_BILLING = Pattern.compile("(?i)next billing date is\\s+(?<date>[A-Z][a-z]+ \\d{1,2}, \\d{4})");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    @Override
    public boolean supports(GmailMessage message) {
        return message.isFromDomain("netflix.com");
    }

    @Override
    public Optional<ParsedBilling> parse(GmailMessage message) {
        String text = message.bodyText();
        Matcher amount = AMOUNT.matcher(text);
        if (!amount.find())
            return Optional.empty();
        return Optional.of(new ParsedBilling(
                new BigDecimal(amount.group("number")),
                CurrencySymbols.toCurrency(amount.group("symbol")),
                BillingFrequency.MONTHLY,
                null,
                findNextBillingDate(text),
                Confidence.HIGH));
    }

    private static LocalDate findNextBillingDate(String text) {
        Matcher matcher = NEXT_BILLING.matcher(text);
        if (!matcher.find())
            return null;
        try {
            return LocalDate.parse(matcher.group("date"), LONG_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

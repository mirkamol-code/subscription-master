package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.BillingPlatform;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class AppleInvoiceParser implements PlatformInvoiceParser {
    private static final String APPLE_DOMAIN = "apple.com";
    private static final Pattern BILLING_SUBJECT = Pattern.compile("(?i)\\b(?:invoice|receipt)\\b");
    private static final Pattern ORDER_ID = Pattern.compile("(?i)^order id:\\s*(?<id>\\S+)");
    private static final Pattern ITEMS_START = Pattern.compile("(?i)^apple account:");
    private static final Pattern BILLING_SECTION = Pattern.compile("(?i)^billing and payment$");
    private static final Pattern PLAN_WITH_CYCLE = Pattern.compile("^(?<plan>.*?)\\s*\\((?<cycle>[^)]+)\\)$");
    private static final Pattern RENEWS = Pattern.compile("(?i)^renews\\s+(?<date>.+)$");
    private static final Pattern TOTAL_LABEL = Pattern.compile("(?i)\\btotal\\b");
    private static final Pattern ADJUSTMENT_LABEL = Pattern.compile("(?i)\\b(?:sub\\s?total|vat|tax(?:es)?|gst|discount)\\b");
    private static final Pattern LEADING_WITH = Pattern.compile("(?i)^with\\s+");

    @Override
    public BillingPlatform platform() {
        return BillingPlatform.APPLE;
    }

    @Override
    public boolean supports(GmailMessage message) {
        return message.isFromDomain(APPLE_DOMAIN) && BILLING_SUBJECT.matcher(message.subject()).find();
    }

    @Override
    public Optional<PlatformInvoice> parse(GmailMessage message) {
        List<String> lines = message.bodyText().lines().toList();
        int itemsStart = indexOfFirst(lines, ITEMS_START, 0);
        int billingStart = indexOfFirst(lines, BILLING_SECTION, Math.max(itemsStart, 0));
        if (itemsStart < 0 || billingStart < 0)
            return Optional.empty();
        List<LineItem> items = readItems(lines.subList(itemsStart + 1, billingStart));
        if (items.isEmpty())
            return Optional.empty();
        List<String> billingLines = lines.subList(billingStart + 1, lines.size());
        return Optional.of(new PlatformInvoice(
                findInvoiceDate(lines.subList(0, itemsStart)),
                findOrderId(lines.subList(0, itemsStart)),
                items,
                findTotal(billingLines).orElse(null),
                PaymentMethodParser.find(String.join("\n", billingLines)).orElse(null)));
    }

    private static List<LineItem> readItems(List<String> lines) {
        List<LineItem> items = new ArrayList<>();
        List<String> block = new ArrayList<>();
        for (String line : lines) {
            Optional<Money> price = readWholeLineAmount(line);
            if (price.isPresent() && !block.isEmpty()) {
                items.add(toLineItem(block, price.get()));
                block.clear();
            } else if (price.isEmpty()) {
                block.add(line);
            }
        }
        return items;
    }

    private static LineItem toLineItem(List<String> block, Money price) {
        String title = block.getFirst();
        List<String> details = block.subList(1, block.size());
        Optional<Matcher> planLine = details.stream().map(PLAN_WITH_CYCLE::matcher).filter(Matcher::matches).findFirst();
        BillingFrequency cycle = planLine.map(matcher -> toCycle(matcher.group("cycle"))).orElse(null);
        LocalDate renewsOn = details.stream().map(RENEWS::matcher).filter(Matcher::matches).findFirst()
                .flatMap(matcher -> DateFinder.findFirstDate(matcher.group("date")))
                .orElse(null);
        String plan = planLine.map(matcher -> matcher.group("plan"))
                .orElseGet(() -> details.stream().filter(line -> !RENEWS.matcher(line).matches()).findFirst().orElse(null));
        return new LineItem(title, removeServiceName(plan, title), cycle, renewsOn, price, cycle != null || renewsOn != null);
    }

    private static BillingFrequency toCycle(String text) {
        String cycle = text.toLowerCase(Locale.ROOT).trim();
        return switch (cycle) {
            case "monthly", "1 month", "month" -> BillingFrequency.MONTHLY;
            case "annual", "annually", "yearly", "1 year", "year" -> BillingFrequency.ANNUAL;
            case "weekly", "1 week", "week" -> BillingFrequency.WEEKLY;
            default -> null;
        };
    }

    private static String removeServiceName(String plan, String title) {
        if (plan == null)
            return null;
        String trimmed = plan.regionMatches(true, 0, title, 0, title.length()) ? plan.substring(title.length()) : plan;
        String cleaned = LEADING_WITH.matcher(trimmed.strip()).replaceFirst("").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static Optional<Money> findTotal(List<String> billingLines) {
        Money labelled = null;
        Money lastUnlabelled = null;
        for (String line : billingLines) {
            List<MoneyParser.MoneyMatch> amounts = MoneyParser.findAll(line);
            if (amounts.isEmpty() || ADJUSTMENT_LABEL.matcher(line).find())
                continue;
            Money amount = amounts.getLast().money();
            boolean namesPaymentMethod = PaymentMethodParser.find(line).isPresent();
            if (TOTAL_LABEL.matcher(line).find() || namesPaymentMethod)
                labelled = amount;
            lastUnlabelled = amount;
        }
        return Optional.ofNullable(labelled != null ? labelled : lastUnlabelled);
    }

    private static Optional<Money> readWholeLineAmount(String line) {
        List<MoneyParser.MoneyMatch> amounts = MoneyParser.findAll(line);
        if (amounts.size() != 1)
            return Optional.empty();
        MoneyParser.MoneyMatch match = amounts.getFirst();
        String rest = (line.substring(0, match.start()) + line.substring(match.end())).replaceAll("(?i)US|\\s", "");
        return rest.isEmpty() ? Optional.of(match.money()) : Optional.empty();
    }

    private static LocalDate findInvoiceDate(List<String> header) {
        return header.stream()
                .map(DateFinder::findFirstDate)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    private static String findOrderId(List<String> header) {
        return header.stream()
                .map(ORDER_ID::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group("id"))
                .findFirst()
                .orElse(null);
    }

    private static int indexOfFirst(List<String> lines, Pattern pattern, int from) {
        for (int index = from; index < lines.size(); index++)
            if (pattern.matcher(lines.get(index)).find())
                return index;
        return -1;
    }
}

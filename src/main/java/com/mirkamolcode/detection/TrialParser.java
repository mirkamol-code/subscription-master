package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TrialParser {
    private static final Map<BillingFrequency, Pattern> CYCLES = Map.of(
            BillingFrequency.WEEKLY, Pattern.compile("(?i)\\b(?:per week|a week|/\\s?week|weekly)\\b"),
            BillingFrequency.MONTHLY, Pattern.compile("(?i)\\b(?:per month|a month|/\\s?mo(?:nth)?|monthly)\\b"),
            BillingFrequency.ANNUAL, Pattern.compile("(?i)\\b(?:per year|a year|/\\s?y(?:ea)?r|yearly|annually)\\b"));

    private TrialParser() {
    }

    private static final Pattern STATED_LENGTH = Pattern.compile(
            "(?i)\\b(?<count>\\d{1,3}|one|two|three|four)[- ](?<unit>day|week|month)s?(?:[- ]long)?(?: free)? trial\\b");
    private static final Map<String, Integer> NUMBER_WORDS = Map.of("one", 1, "two", 2, "three", 3, "four", 4);

    static TrialDetails parse(String body, Pattern trialEnds, LocalDate receivedOn, boolean startConfirmed) {
        LocalDate statedEnd = DateFinder.findDateAfter(body, trialEnds, receivedOn);
        LocalDate estimatedEnd = statedEnd == null && startConfirmed ? estimateEnd(body, receivedOn) : null;
        return new TrialDetails(statedEnd != null ? statedEnd : estimatedEnd, estimatedEnd != null, findOnlyPositivePrice(body),
                findOnlyCycle(body), startConfirmed);
    }

    private static LocalDate estimateEnd(String body, LocalDate startedOn) {
        Matcher length = STATED_LENGTH.matcher(body);
        if (!length.find())
            return null;
        String count = length.group("count").toLowerCase(Locale.ROOT);
        int amount = NUMBER_WORDS.containsKey(count) ? NUMBER_WORDS.get(count) : Integer.parseInt(count);
        return switch (length.group("unit").toLowerCase(Locale.ROOT)) {
            case "day" -> startedOn.plusDays(amount);
            case "week" -> startedOn.plusWeeks(amount);
            default -> startedOn.plusMonths(amount);
        };
    }

    private static Money findOnlyPositivePrice(String body) {
        List<Money> prices = MoneyParser.findAll(body).stream()
                .map(MoneyParser.MoneyMatch::money)
                .filter(money -> money.amount().signum() > 0)
                .distinct()
                .toList();
        return prices.size() == 1 ? prices.getFirst() : null;
    }

    private static BillingFrequency findOnlyCycle(String body) {
        List<BillingFrequency> cycles = CYCLES.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(body).find())
                .map(Map.Entry::getKey)
                .toList();
        return cycles.size() == 1 ? cycles.getFirst() : null;
    }
}

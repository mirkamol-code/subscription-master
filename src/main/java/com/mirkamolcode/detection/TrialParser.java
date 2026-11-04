package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class TrialParser {
    private static final Map<BillingFrequency, Pattern> CYCLES = Map.of(
            BillingFrequency.WEEKLY, Pattern.compile("(?i)\\b(?:per week|a week|/\\s?week|weekly)\\b"),
            BillingFrequency.MONTHLY, Pattern.compile("(?i)\\b(?:per month|a month|/\\s?mo(?:nth)?|monthly)\\b"),
            BillingFrequency.ANNUAL, Pattern.compile("(?i)\\b(?:per year|a year|/\\s?y(?:ea)?r|yearly|annually)\\b"));

    private TrialParser() {
    }

    static TrialDetails parse(String body, Pattern trialEnds, LocalDate receivedOn) {
        return new TrialDetails(DateFinder.findDateAfter(body, trialEnds, receivedOn), findOnlyPositivePrice(body), findOnlyCycle(body));
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

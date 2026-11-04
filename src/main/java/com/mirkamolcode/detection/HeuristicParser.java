package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HeuristicParser implements EmailParser {
    private static final Pattern TOTAL_KEYWORD = Pattern.compile(
            "(?i)(?<!sub)total|amount (?:paid|charged)|charged|you paid|grand total");
    private static final Pattern ADJUSTMENT_KEYWORD = Pattern.compile(
            "(?i)\\b(?:sub\\s?total|vat|tax(?:es)?|gst|discount)\\b");

    private static final Pattern ANNUAL = Pattern.compile(
            "(?i)\\b(?:annual(?:ly)?|yearly|per year|a year|every year|12 months)\\b|/\\s?(?:yr|year)\\b");
    private static final Pattern MONTHLY = Pattern.compile(
            "(?i)\\b(?:monthly|per month|a month|every month)\\b|/\\s?(?:mo|month)\\b");
    private static final Pattern WEEKLY = Pattern.compile(
            "(?i)\\b(?:weekly|per week|a week|every week)\\b|/\\s?(?:wk|week)\\b");

    private static final Map<BillingFrequency, Pattern> CYCLES = Map.of(
            BillingFrequency.ANNUAL, ANNUAL, BillingFrequency.MONTHLY, MONTHLY, BillingFrequency.WEEKLY, WEEKLY);
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.!?](?:\\s|$)|\\n");

    private static final Pattern NEXT_BILLING_KEYWORD = Pattern.compile(
            "(?i)\\brenews?(?: on)?\\b|renewal date|next (?:billing|payment|charge)(?: date)?|will be (?:charged|billed)|next bill");

    @Override
    public boolean supports(GmailMessage message) {
        return true;
    }

    @Override
    public Optional<ParsedBilling> parse(GmailMessage message) {
        String text = message.bodyText();
        return findAmount(text).map(amount -> new ParsedBilling(
                amount.value(), amount.currency(), findCycle(text, amount), null,
                DateFinder.findDateAfter(text, NEXT_BILLING_KEYWORD, message.receivedOnUtc()), Confidence.LOW));
    }

    private static Optional<Amount> findAmount(String text) {
        List<Amount> amounts = MoneyParser.findAll(text).stream()
                .map(match -> new Amount(match.money().amount(), match.money().currency(), match.start(), match.end()))
                .toList();
        return lastAfterTotalKeyword(text, amounts).or(() -> amounts.stream().max(Comparator.comparing(Amount::value)));
    }

    private static Optional<Amount> lastAfterTotalKeyword(String text, List<Amount> amounts) {
        Amount chosen = null;
        int previousEnd = 0;
        for (Amount amount : amounts) {
            String label = text.substring(previousEnd, amount.start());
            if (TOTAL_KEYWORD.matcher(label).find() && !isAdjustment(label))
                chosen = amount;
            previousEnd = amount.end();
        }
        return Optional.ofNullable(chosen);
    }

    private static boolean isAdjustment(String label) {
        String sameLine = label.substring(label.lastIndexOf('\n') + 1);
        return ADJUSTMENT_KEYWORD.matcher(sameLine).find();
    }

    private static BillingFrequency findCycle(String text, Amount amount) {
        int sentenceStart = findSentenceStart(text, amount.start());
        int sentenceEnd = findSentenceEnd(text, amount.end());
        Optional<CycleMatch> inSentence = findCycleMatches(text.substring(sentenceStart, sentenceEnd), sentenceStart).stream()
                .min(Comparator.comparingInt(match -> match.distanceTo(amount)));
        if (inSentence.isPresent())
            return inSentence.get().cycle();
        List<BillingFrequency> anywhere = findCycleMatches(text, 0).stream().map(CycleMatch::cycle).distinct().toList();
        return anywhere.size() == 1 ? anywhere.getFirst() : null;
    }

    private static List<CycleMatch> findCycleMatches(String text, int offset) {
        List<CycleMatch> matches = new ArrayList<>();
        for (Map.Entry<BillingFrequency, Pattern> cycle : CYCLES.entrySet()) {
            Matcher matcher = cycle.getValue().matcher(text);
            while (matcher.find())
                matches.add(new CycleMatch(cycle.getKey(), offset + matcher.start(), offset + matcher.end()));
        }
        return matches;
    }

    private static int findSentenceStart(String text, int from) {
        int start = 0;
        Matcher boundary = SENTENCE_BOUNDARY.matcher(text);
        while (boundary.find() && boundary.end() <= from)
            start = boundary.end();
        return start;
    }

    private static int findSentenceEnd(String text, int from) {
        Matcher boundary = SENTENCE_BOUNDARY.matcher(text);
        return boundary.find(from) ? boundary.start() : text.length();
    }

    private record Amount(BigDecimal value, CurrencyCode currency, int start, int end) {
    }

    private record CycleMatch(BillingFrequency cycle, int start, int end) {
        int distanceTo(Amount amount) {
            return start >= amount.end() ? start - amount.end() : Math.max(0, amount.start() - end);
        }
    }
}

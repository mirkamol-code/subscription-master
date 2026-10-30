package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HeuristicParser implements EmailParser {
    private static final String NUMBER = "\\d{1,3}(?:[ ,.\\u00A0]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?";
    private static final String SYMBOLS = "[$€£₽₸₺¥]";
    private static final String CODES = "USD|EUR|GBP|RUB|KZT|TRY|JPY|CNY|UZS";
    private static final String UZS_WORDS = "so['ʻ‘’`]?m|сум|сўм";
    private static final List<AmountPattern> AMOUNT_PATTERNS = List.of(
            new AmountPattern(Pattern.compile("(?<symbol>" + SYMBOLS + ")\\s?(?<number>" + NUMBER + ")"), HeuristicParser::currencyOfSymbol),
            new AmountPattern(Pattern.compile("(?<number>" + NUMBER + ")\\s?(?<symbol>" + SYMBOLS + ")"), HeuristicParser::currencyOfSymbol),
            new AmountPattern(Pattern.compile("\\b(?<code>" + CODES + ")\\s?(?<number>" + NUMBER + ")"), HeuristicParser::currencyOfCode),
            new AmountPattern(Pattern.compile("(?<number>" + NUMBER + ")\\s?(?<code>" + CODES + ")\\b"), HeuristicParser::currencyOfCode),
            new AmountPattern(Pattern.compile("(?iu)(?<number>" + NUMBER + ")\\s?(?:" + UZS_WORDS + ")(?![\\p{L}])"), matcher -> CurrencyCode.UZS));
    private static final Pattern TOTAL_KEYWORD = Pattern.compile(
            "(?i)(?<!sub)total|amount (?:paid|charged)|charged|you paid|grand total");

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
            "(?i)renews? on|renewal date|next (?:billing|payment|charge)(?: date)?|will be (?:charged|billed)|next bill");
    private static final int DATE_SEARCH_WINDOW = 40;
    private static final String MONTH_NAME = "(?<month>jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?";
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile("(?i)" + MONTH_NAME + "\\s+(?<day>\\d{1,2}),?\\s+(?<year>\\d{4})");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("(?i)(?<day>\\d{1,2})\\s+" + MONTH_NAME + "\\s+(?<year>\\d{4})");
    private static final Pattern ISO_DATE = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(?<first>\\d{1,2})[/.](?<second>\\d{1,2})[/.](?<year>\\d{4})");

    @Override
    public boolean supports(GmailMessage message) {
        return true;
    }

    @Override
    public Optional<ParsedBilling> parse(GmailMessage message) {
        String text = message.bodyText();
        return findAmount(text).map(amount -> new ParsedBilling(
                amount.value(), amount.currency(), findCycle(text, amount), null, findNextBillingDate(text), Confidence.LOW));
    }

    private static Optional<Amount> findAmount(String text) {
        List<Amount> amounts = new ArrayList<>();
        for (AmountPattern amountPattern : AMOUNT_PATTERNS) {
            Matcher matcher = amountPattern.pattern().matcher(text);
            while (matcher.find())
                amounts.add(new Amount(toNumber(matcher.group("number")), amountPattern.currency().apply(matcher), matcher.start(), matcher.end()));
        }
        List<Amount> distinct = removeOverlaps(amounts);
        return lastAfterTotalKeyword(text, distinct).or(() -> distinct.stream().max(Comparator.comparing(Amount::value)));
    }

    private static List<Amount> removeOverlaps(List<Amount> amounts) {
        List<Amount> sorted = amounts.stream().sorted(Comparator.comparingInt(Amount::start)).toList();
        List<Amount> distinct = new ArrayList<>();
        for (Amount amount : sorted)
            if (distinct.isEmpty() || amount.start() >= distinct.getLast().end())
                distinct.add(amount);
        return distinct;
    }

    private static Optional<Amount> lastAfterTotalKeyword(String text, List<Amount> amounts) {
        Amount chosen = null;
        int previousEnd = 0;
        for (Amount amount : amounts) {
            if (TOTAL_KEYWORD.matcher(text.substring(previousEnd, amount.start())).find())
                chosen = amount;
            previousEnd = amount.end();
        }
        return Optional.ofNullable(chosen);
    }

    private static CurrencyCode currencyOfSymbol(Matcher matcher) {
        return CurrencySymbols.toCurrency(matcher.group("symbol"));
    }

    private static CurrencyCode currencyOfCode(Matcher matcher) {
        return CurrencyCode.valueOf(matcher.group("code"));
    }

    private static BigDecimal toNumber(String raw) {
        String digits = raw.replace(" ", "").replace(" ", "");
        int lastSeparator = Math.max(digits.lastIndexOf(','), digits.lastIndexOf('.'));
        if (lastSeparator < 0)
            return new BigDecimal(digits);
        boolean hasDecimals = digits.length() - lastSeparator - 1 <= 2;
        String whole = digits.substring(0, lastSeparator).replace(",", "").replace(".", "");
        String rest = digits.substring(lastSeparator + 1);
        return new BigDecimal(hasDecimals ? whole + "." + rest : whole + rest);
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

    private static LocalDate findNextBillingDate(String text) {
        Matcher keyword = NEXT_BILLING_KEYWORD.matcher(text);
        while (keyword.find()) {
            String window = text.substring(keyword.end(), Math.min(text.length(), keyword.end() + DATE_SEARCH_WINDOW));
            Optional<LocalDate> date = findFirstDate(window);
            if (date.isPresent())
                return date.get();
        }
        return null;
    }

    private static Optional<LocalDate> findFirstDate(String window) {
        List<DateMatch> matches = new ArrayList<>();
        addNamedMonthDates(MONTH_DAY_YEAR, window, matches);
        addNamedMonthDates(DAY_MONTH_YEAR, window, matches);
        Matcher iso = ISO_DATE.matcher(window);
        if (iso.find())
            matches.add(new DateMatch(iso.start(), createDate(iso.group("year"), iso.group("month"), iso.group("day"))));
        Matcher numeric = NUMERIC_DATE.matcher(window);
        if (numeric.find())
            matches.add(new DateMatch(numeric.start(), unambiguousNumericDate(numeric)));
        return matches.stream()
                .min(Comparator.comparingInt(DateMatch::position))
                .map(DateMatch::date);
    }

    private static void addNamedMonthDates(Pattern pattern, String window, List<DateMatch> matches) {
        Matcher matcher = pattern.matcher(window);
        if (matcher.find()) {
            int month = parseMonthNumber(matcher.group("month"));
            matches.add(new DateMatch(matcher.start(), createDate(matcher.group("year"), String.valueOf(month), matcher.group("day"))));
        }
    }

    private static LocalDate unambiguousNumericDate(Matcher matcher) {
        int first = Integer.parseInt(matcher.group("first"));
        int second = Integer.parseInt(matcher.group("second"));
        String year = matcher.group("year");
        if (first > 12)
            return createDate(year, String.valueOf(second), String.valueOf(first));
        if (second > 12)
            return createDate(year, String.valueOf(first), String.valueOf(second));
        return null;
    }

    private static int parseMonthNumber(String name) {
        String prefix = name.substring(0, 3).toUpperCase(Locale.ROOT);
        return Arrays.stream(Month.values())
                .filter(month -> month.name().startsWith(prefix))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    private static LocalDate createDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private record AmountPattern(Pattern pattern, Function<Matcher, CurrencyCode> currency) {
    }

    private record Amount(BigDecimal value, CurrencyCode currency, int start, int end) {
    }

    private record DateMatch(int position, LocalDate date) {
    }

    private record CycleMatch(BillingFrequency cycle, int start, int end) {
        int distanceTo(Amount amount) {
            return start >= amount.end() ? start - amount.end() : Math.max(0, amount.start() - end);
        }
    }
}

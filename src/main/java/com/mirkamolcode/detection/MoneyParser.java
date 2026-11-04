package com.mirkamolcode.detection;

import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MoneyParser {
    private static final String NUMBER = "\\d{1,3}(?:[ ,.\\u00A0]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?";
    private static final String SYMBOLS = "[$€£₽₸₺¥]";
    private static final String CODES = "USD|EUR|GBP|RUB|KZT|TRY|JPY|CNY|UZS";
    private static final String UZS_WORDS = "so['ʻ‘’`]?m|сум|сўм";
    private static final List<AmountPattern> AMOUNT_PATTERNS = List.of(
            new AmountPattern(Pattern.compile("(?<symbol>" + SYMBOLS + ")\\s?(?<number>" + NUMBER + ")"), MoneyParser::currencyOfSymbol),
            new AmountPattern(Pattern.compile("(?<number>" + NUMBER + ")\\s?(?<symbol>" + SYMBOLS + ")"), MoneyParser::currencyOfSymbol),
            new AmountPattern(Pattern.compile("\\b(?<code>" + CODES + ")\\s?(?<number>" + NUMBER + ")"), MoneyParser::currencyOfCode),
            new AmountPattern(Pattern.compile("(?<number>" + NUMBER + ")\\s?(?<code>" + CODES + ")\\b"), MoneyParser::currencyOfCode),
            new AmountPattern(Pattern.compile("(?iu)(?<number>" + NUMBER + ")\\s?(?:" + UZS_WORDS + ")(?![\\p{L}])"), matcher -> CurrencyCode.UZS));

    private MoneyParser() {
    }

    static List<MoneyMatch> findAll(String text) {
        List<MoneyMatch> matches = new ArrayList<>();
        for (AmountPattern amountPattern : AMOUNT_PATTERNS) {
            Matcher matcher = amountPattern.pattern().matcher(text);
            while (matcher.find())
                matches.add(new MoneyMatch(new Money(toNumber(matcher.group("number")), amountPattern.currency().apply(matcher)),
                        matcher.start(), matcher.end()));
        }
        return removeOverlaps(matches);
    }

    private static List<MoneyMatch> removeOverlaps(List<MoneyMatch> matches) {
        List<MoneyMatch> sorted = matches.stream().sorted(Comparator.comparingInt(MoneyMatch::start)).toList();
        List<MoneyMatch> distinct = new ArrayList<>();
        for (MoneyMatch match : sorted)
            if (distinct.isEmpty() || match.start() >= distinct.getLast().end())
                distinct.add(match);
        return distinct;
    }

    private static CurrencyCode currencyOfSymbol(Matcher matcher) {
        return CurrencySymbols.toCurrency(matcher.group("symbol"));
    }

    private static CurrencyCode currencyOfCode(Matcher matcher) {
        return CurrencyCode.valueOf(matcher.group("code"));
    }

    private static BigDecimal toNumber(String raw) {
        String digits = raw.replace(" ", "").replace(" ", "");
        int lastSeparator = Math.max(digits.lastIndexOf(','), digits.lastIndexOf('.'));
        if (lastSeparator < 0)
            return new BigDecimal(digits);
        boolean hasDecimals = digits.length() - lastSeparator - 1 <= 2;
        String whole = digits.substring(0, lastSeparator).replace(",", "").replace(".", "");
        String rest = digits.substring(lastSeparator + 1);
        return new BigDecimal(hasDecimals ? whole + "." + rest : whole + rest);
    }

    record MoneyMatch(Money money, int start, int end) {
    }

    private record AmountPattern(Pattern pattern, Function<Matcher, CurrencyCode> currency) {
    }
}

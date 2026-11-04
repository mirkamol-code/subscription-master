package com.mirkamolcode.detection;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class PriceChangeParser {
    private static final Pattern FROM_BEFORE = Pattern.compile("(?i)\\bfrom\\s*$");
    private static final Pattern TO_BETWEEN = Pattern.compile("(?i)^\\s*(?:to|->|→)\\s*$");
    private static final int FROM_LOOKBEHIND = 12;

    private PriceChangeParser() {
    }

    static Optional<PriceChangeNotice> parse(String body, Pattern effectiveKeyword, LocalDate receivedOn) {
        LocalDate effectiveOn = DateFinder.findDateAfter(body, effectiveKeyword, receivedOn);
        List<MoneyParser.MoneyMatch> amounts = MoneyParser.findAll(body);
        for (int i = 0; i + 1 < amounts.size(); i++) {
            MoneyParser.MoneyMatch from = amounts.get(i);
            MoneyParser.MoneyMatch to = amounts.get(i + 1);
            if (!isFromToPair(body, from, to))
                continue;
            if (from.money().currency() != to.money().currency())
                return Optional.empty();
            return Optional.of(new PriceChangeNotice(from.money().amount(), to.money().amount(), to.money().currency(), effectiveOn));
        }
        return Optional.of(new PriceChangeNotice(null, null, null, effectiveOn));
    }

    private static boolean isFromToPair(String body, MoneyParser.MoneyMatch from, MoneyParser.MoneyMatch to) {
        String before = body.substring(Math.max(0, from.start() - FROM_LOOKBEHIND), from.start());
        String between = body.substring(from.end(), to.start());
        return FROM_BEFORE.matcher(before).find() && TO_BETWEEN.matcher(between).find();
    }
}

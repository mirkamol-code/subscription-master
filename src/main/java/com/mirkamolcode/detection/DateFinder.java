package com.mirkamolcode.detection;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DateFinder {
    private static final int SEARCH_WINDOW = 40;
    private static final String MONTH_NAME = "(?<month>jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?";
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile("(?i)" + MONTH_NAME + "\\s+(?<day>\\d{1,2}),?\\s+(?<year>\\d{4})");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("(?i)(?<day>\\d{1,2})\\s+" + MONTH_NAME + "\\s+(?<year>\\d{4})");
    private static final Pattern MONTH_DAY = Pattern.compile("(?i)" + MONTH_NAME + "\\s+(?<day>\\d{1,2})(?!\\d)(?!,?\\s+\\d{4})");
    private static final Pattern DAY_MONTH = Pattern.compile("(?i)(?<!\\d)(?<day>\\d{1,2})\\s+" + MONTH_NAME + "(?!\\s+\\d{4})");
    private static final Pattern ISO_DATE = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(?<first>\\d{1,2})[/.](?<second>\\d{1,2})[/.](?<year>\\d{4})");

    private DateFinder() {
    }

    static LocalDate findDateAfter(String text, Pattern keyword, LocalDate referenceDate) {
        Matcher match = keyword.matcher(text);
        while (match.find()) {
            String window = text.substring(match.end(), Math.min(text.length(), match.end() + SEARCH_WINDOW));
            Optional<LocalDate> date = findFirstDate(window, referenceDate);
            if (date.isPresent())
                return date.get();
        }
        return null;
    }

    private static Optional<LocalDate> findFirstDate(String window, LocalDate referenceDate) {
        List<DateMatch> matches = new ArrayList<>();
        addNamedMonthDate(MONTH_DAY_YEAR, window, matches);
        addNamedMonthDate(DAY_MONTH_YEAR, window, matches);
        Matcher iso = ISO_DATE.matcher(window);
        if (iso.find())
            matches.add(new DateMatch(iso.start(), createDate(iso.group("year"), iso.group("month"), iso.group("day"))));
        Matcher numeric = NUMERIC_DATE.matcher(window);
        if (numeric.find())
            matches.add(new DateMatch(numeric.start(), resolveUnambiguousNumericDate(numeric)));
        if (referenceDate != null) {
            addYearlessDate(MONTH_DAY, window, referenceDate, matches);
            addYearlessDate(DAY_MONTH, window, referenceDate, matches);
        }
        return matches.stream()
                .min(Comparator.comparingInt(DateMatch::position))
                .map(DateMatch::date);
    }

    private static void addNamedMonthDate(Pattern pattern, String window, List<DateMatch> matches) {
        Matcher matcher = pattern.matcher(window);
        if (matcher.find())
            matches.add(new DateMatch(matcher.start(),
                    createDate(matcher.group("year"), String.valueOf(parseMonthNumber(matcher.group("month"))), matcher.group("day"))));
    }

    private static void addYearlessDate(Pattern pattern, String window, LocalDate referenceDate, List<DateMatch> matches) {
        Matcher matcher = pattern.matcher(window);
        if (!matcher.find())
            return;
        String month = String.valueOf(parseMonthNumber(matcher.group("month")));
        LocalDate sameYear = createDate(String.valueOf(referenceDate.getYear()), month, matcher.group("day"));
        if (sameYear == null)
            return;
        LocalDate next = sameYear.isBefore(referenceDate) ? sameYear.plusYears(1) : sameYear;
        matches.add(new DateMatch(matcher.start(), next));
    }

    private static LocalDate resolveUnambiguousNumericDate(Matcher matcher) {
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

    private record DateMatch(int position, LocalDate date) {
    }
}

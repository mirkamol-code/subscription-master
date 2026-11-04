package com.mirkamolcode.detection;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentMethodParser {
    private static final Map<String, String> BRAND_NAMES = Map.ofEntries(
            Map.entry("visa", "Visa"), Map.entry("mastercard", "Mastercard"), Map.entry("amex", "Amex"),
            Map.entry("americanexpress", "Amex"), Map.entry("maestro", "Maestro"), Map.entry("discover", "Discover"),
            Map.entry("jcb", "JCB"), Map.entry("unionpay", "UnionPay"), Map.entry("mir", "Mir"),
            Map.entry("humo", "Humo"), Map.entry("uzcard", "Uzcard"));
    private static final String UNAMBIGUOUS_BRAND = "visa|master\\s?card|amex|american express|maestro|jcb|union\\s?pay|humo|uzcard";
    private static final String ANY_BRAND = UNAMBIGUOUS_BRAND + "|discover|mir|card";
    private static final String MASK = "(?:[•·*●∙]+|[xX]{2,}|ending(?: in)?|ends in)";
    private static final String LAST_FOUR = "(?<last4>\\d{4})(?!\\d)(?![\\s-]\\d)";

    private static final Pattern BRANDED_CARD = Pattern.compile(
            "(?iu)\\b(?<brand>" + ANY_BRAND + ")\\b\\s*" + MASK + "\\s*" + LAST_FOUR);
    private static final Pattern MASKED_CARD = Pattern.compile("(?u)(?<![\\d\\p{L}])[•·*●∙]+\\s*" + LAST_FOUR);
    private static final Pattern BRAND_ONLY = Pattern.compile("(?iu)\\b(?<brand>" + UNAMBIGUOUS_BRAND + ")\\b");
    private static final Pattern CARD_WORDING = Pattern.compile("(?iu)\\b(?:credit|debit|bank) card\\b");
    private static final Pattern PAYPAL = Pattern.compile("(?iu)\\bpaypal\\b");

    private PaymentMethodParser() {
    }

    public static Optional<PaymentMethod> find(String text) {
        Matcher branded = BRANDED_CARD.matcher(text);
        if (branded.find())
            return Optional.of(PaymentMethod.card(toBrandName(branded.group("brand")), branded.group("last4")));
        Matcher masked = MASKED_CARD.matcher(text);
        if (masked.find())
            return Optional.of(PaymentMethod.card(null, masked.group("last4")));
        Matcher brandOnly = BRAND_ONLY.matcher(text);
        if (brandOnly.find())
            return Optional.of(PaymentMethod.card(toBrandName(brandOnly.group("brand")), null));
        if (CARD_WORDING.matcher(text).find())
            return Optional.of(PaymentMethod.card(null, null));
        if (PAYPAL.matcher(text).find())
            return Optional.of(PaymentMethod.paypal());
        return Optional.empty();
    }

    private static String toBrandName(String matched) {
        return BRAND_NAMES.get(matched.toLowerCase(Locale.ROOT).replaceAll("\\s", ""));
    }
}

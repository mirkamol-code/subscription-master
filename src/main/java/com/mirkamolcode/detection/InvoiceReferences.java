package com.mirkamolcode.detection;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record InvoiceReferences(String invoice, String customer) {
    private static final String REFERENCE = "(?<ref>[A-Za-z0-9][A-Za-z0-9/_-]{2,62})(?![\\w/-])";
    private static final String NUMBER_LABEL = "(?:no\\.?|number|nr\\.?|id)";
    private static final Pattern INVOICE = Pattern.compile(
            "(?i)\\binvoice(?:\\s+" + NUMBER_LABEL + ")?\\s*[:#]?\\s*#?" + REFERENCE);
    private static final Pattern CUSTOMER = Pattern.compile(
            "(?i)\\b(?:customer|client|account)\\s*" + NUMBER_LABEL + "\\s*[:#]?\\s*" + REFERENCE);
    private static final Pattern SUBJECT_CUSTOMER = Pattern.compile("\\((?<ref>[A-Z]{0,3}\\d{5,})\\)");

    public static InvoiceReferences none() {
        return new InvoiceReferences(null, null);
    }

    public static InvoiceReferences find(String subject, String body) {
        String invoice = firstReference(INVOICE, subject).or(() -> firstReference(INVOICE, body)).orElse(null);
        String customer = firstReference(SUBJECT_CUSTOMER, subject).or(() -> firstReference(CUSTOMER, body)).orElse(null);
        return new InvoiceReferences(invoice, customer);
    }

    private static Optional<String> firstReference(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String reference = matcher.group("ref");
            if (reference.chars().anyMatch(Character::isDigit))
                return Optional.of(reference);
        }
        return Optional.empty();
    }
}

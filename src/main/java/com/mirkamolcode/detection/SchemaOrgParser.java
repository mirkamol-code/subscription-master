package com.mirkamolcode.detection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class SchemaOrgParser implements EmailParser {
    private static final String JSON_LD = "application/ld+json";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public boolean supports(GmailMessage message) {
        return message.htmlBody() != null && message.htmlBody().contains(JSON_LD);
    }

    @Override
    public Optional<ParsedBilling> parse(GmailMessage message) {
        return findBillingNodes(message.htmlBody()).stream()
                .map(SchemaOrgParser::toParsedBilling)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static List<JsonNode> findBillingNodes(String html) {
        List<JsonNode> nodes = new ArrayList<>();
        for (Element script : Jsoup.parse(html).select("script[type=" + JSON_LD + "]")) {
            try {
                collectBillingNodes(JSON.readTree(script.data()), nodes);
            } catch (JsonProcessingException ignored) {
            }
        }
        return nodes;
    }

    private static void collectBillingNodes(JsonNode node, List<JsonNode> nodes) {
        if (node.isArray()) {
            node.forEach(child -> collectBillingNodes(child, nodes));
            return;
        }
        String type = node.path("@type").asText();
        if ("Invoice".equals(type) || "Order".equals(type))
            nodes.add(node);
        if (node.has("@graph"))
            collectBillingNodes(node.get("@graph"), nodes);
    }

    private static Optional<ParsedBilling> toParsedBilling(JsonNode node) {
        JsonNode price = "Invoice".equals(node.path("@type").asText()) ? node.path("totalPaymentDue") : node;
        BigDecimal amount = parseDecimal(price.path("price").asText(price.path("value").asText(null)));
        CurrencyCode currency = parseCurrency(price.path("priceCurrency").asText(price.path("currency").asText(null)));
        if (amount == null || currency == null)
            return Optional.empty();
        boolean paid = node.path("paymentStatus").asText("").endsWith("PaymentComplete");
        LocalDate dueDate = parseDate(node.path("paymentDueDate").asText(null));
        LocalDate paymentDate = paid ? dueDate : parseDate(node.path("orderDate").asText(null));
        LocalDate nextBillingDate = paid ? null : dueDate;
        return Optional.of(new ParsedBilling(amount, currency, parseCycle(node.path("billingPeriod").asText(null)),
                paymentDate, nextBillingDate, Confidence.HIGH));
    }

    private static BillingFrequency parseCycle(String isoPeriod) {
        if (isoPeriod == null)
            return null;
        try {
            Period period = Period.parse(isoPeriod);
            if (period.toTotalMonths() == 12 && period.getDays() == 0)
                return BillingFrequency.ANNUAL;
            if (period.toTotalMonths() == 1 && period.getDays() == 0)
                return BillingFrequency.MONTHLY;
            if (period.toTotalMonths() == 0 && period.getDays() == 7)
                return BillingFrequency.WEEKLY;
            return null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static CurrencyCode parseCurrency(String code) {
        return Arrays.stream(CurrencyCode.values())
                .filter(currency -> currency.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

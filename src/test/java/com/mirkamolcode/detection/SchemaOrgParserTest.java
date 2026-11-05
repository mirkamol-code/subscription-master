package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaOrgParserTest {
    private static final String INVOICE = """
            {"@type":"Invoice","totalPaymentDue":{"price":"9.99","priceCurrency":"USD"}}""";

    private final SchemaOrgParser underTest = new SchemaOrgParser();

    @Test
    void parse_shouldFindInvoiceInGraph_whenSiblingNodeHasArrayType() {
        // given
        GmailMessage message = messageWithJsonLd("""
                {"@graph":[{"@type":["Organization","Thing"],"name":"Acme"},%s]}""".formatted(INVOICE));

        // when
        Optional<ParsedBilling> billing = underTest.parse(message);

        // then
        assertThat(billing).hasValueSatisfying(parsed -> {
            assertThat(parsed.amount()).isEqualByComparingTo(new BigDecimal("9.99"));
            assertThat(parsed.currency()).isEqualTo(CurrencyCode.USD);
        });
    }

    @Test
    void parse_shouldReadInvoice_whenScriptHasTrailingContent() {
        // given
        GmailMessage message = messageWithJsonLd(INVOICE + ";");

        // when
        Optional<ParsedBilling> billing = underTest.parse(message);

        // then
        assertThat(billing).hasValueSatisfying(parsed ->
                assertThat(parsed.amount()).isEqualByComparingTo(new BigDecimal("9.99")));
    }

    private static GmailMessage messageWithJsonLd(String jsonLd) {
        String html = "<html><head><script type=\"application/ld+json\">" + jsonLd + "</script></head><body></body></html>";
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-11-01T10:00:00Z"), "billing@acme.com",
                "Your invoice", html, null);
    }
}

package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicParserTest {
    private final HeuristicParser underTest = new HeuristicParser();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Total: $12.99 per month                         | 12.99   | USD
            You paid 12,99 € for your monthly plan          | 12.99   | EUR
            USD 9.00 billed annually                        | 9.00    | USD
            £1,299.00 / year                                | 1299.00 | GBP
            Amount charged: 1.299,00 EUR                    | 1299.00 | EUR
            Charged RUB 1 234,56 this month                 | 1234.56 | RUB
            Subtotal $10.00 Tax $2.00 Total $12.00          | 12.00   | USD
            Your card was charged 49 900 UZS                | 49900   | UZS
            Total $1,299.00                                 | 1299.00 | USD
            Total 1.299,00 €                                | 1299.00 | EUR
            To'lov: UZS 120 000                             | 120000  | UZS
            Jami 120 000 so'm                               | 120000  | UZS
            Итого 120 000 сум                               | 120000  | UZS
            Oylik to'lov 89 000 so‘m                        | 89000   | UZS
            """)
    void parse_shouldFindAmountAndCurrency(String text, BigDecimal amount, CurrencyCode currency) {
        // when
        ParsedBilling parsed = underTest.parse(message(text)).orElseThrow();

        // then
        assertThat(parsed.amount()).isEqualByComparingTo(amount);
        assertThat(parsed.currency()).isEqualTo(currency);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Total $12.99 per month          | MONTHLY
            $9.99/mo                        | MONTHLY
            Billed monthly: $5.00           | MONTHLY
            $99.00 billed annually          | ANNUAL
            Yearly plan $120.00             | ANNUAL
            $119.88 for 12 months           | ANNUAL
            $2.99 per week                  | WEEKLY
            Total $9.99 per month. Switch to annual billing and save 20%.     | MONTHLY
            Billed monthly: $5.00. Annual plans are also available.            | MONTHLY
            You paid $99.00 for 12 months. Try our monthly plan too.          | ANNUAL
            """)
    void parse_shouldFindBillingCycle(String text, BillingFrequency cycle) {
        // when
        ParsedBilling parsed = underTest.parse(message(text)).orElseThrow();

        // then
        assertThat(parsed.cycle()).isEqualTo(cycle);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            $9.99. Your plan renews on Nov 30, 2026.            | 2026-11-30
            $9.99. Next billing date: 30 November 2026          | 2026-11-30
            $9.99. Your next payment is on 2026-12-01           | 2026-12-01
            $9.99. You will be charged on 11/30/2026            | 2026-11-30
            $9.99. Next charge: 30/11/2026                      | 2026-11-30
            """)
    void parse_shouldFindNextBillingDate(String text, LocalDate nextBillingDate) {
        // when
        ParsedBilling parsed = underTest.parse(message(text)).orElseThrow();

        // then
        assertThat(parsed.nextBillingDate()).isEqualTo(nextBillingDate);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            $9.99. Your plan renews on 05/06/2026
            $9.99. Next billing date: 01/02/2026
            $9.99. Next charge: 12.11.2026
            """)
    void parse_shouldNotGuessNextBillingDate_whenDayAndMonthAreAmbiguous(String text) {
        // when
        ParsedBilling parsed = underTest.parse(message(text)).orElseThrow();

        // then
        assertThat(parsed.nextBillingDate()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            $9.99. Next billing date: 13/02/2026     | 2026-02-13
            $9.99. Next billing date: 02/13/2026     | 2026-02-13
            $9.99. Next charge: 13.02.2026           | 2026-02-13
            """)
    void parse_shouldResolveNumericDate_whenOnlyOneReadingIsPossible(String text, LocalDate nextBillingDate) {
        // when
        ParsedBilling parsed = underTest.parse(message(text)).orElseThrow();

        // then
        assertThat(parsed.nextBillingDate()).isEqualTo(nextBillingDate);
    }

    @Test
    void parse_shouldIgnoreDatesThatAreNotNextBillingDates() {
        // when
        ParsedBilling parsed = underTest.parse(message("Receipt date Oct 30, 2026. Total $9.99")).orElseThrow();

        // then
        assertThat(parsed.nextBillingDate()).isNull();
    }

    @Test
    void parse_shouldAlwaysMarkResultLowConfidence() {
        // when
        ParsedBilling parsed = underTest.parse(message("Total: $12.99 per month")).orElseThrow();

        // then
        assertThat(parsed.confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void parse_shouldReadTextOutOfHtmlBody() {
        // given
        GmailMessage html = new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "billing@example.com", "Your receipt",
                "<table><tr><td>Total</td><td><b>$12.99</b></td></tr><tr><td>per month</td></tr></table>", null);

        // when
        ParsedBilling parsed = underTest.parse(html).orElseThrow();

        // then
        assertThat(parsed.amount()).isEqualByComparingTo("12.99");
        assertThat(parsed.cycle()).isEqualTo(BillingFrequency.MONTHLY);
    }

    @Test
    void parse_shouldReturnEmpty_whenTextHasNoAmount() {
        // when
        Optional<ParsedBilling> parsed = underTest.parse(message("Thanks for being a member!"));

        // then
        assertThat(parsed).isEmpty();
    }

    private static GmailMessage message(String text) {
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "billing@example.com", "Your receipt", null, text);
    }
}

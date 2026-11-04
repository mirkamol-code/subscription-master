package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.BillingPlatform;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.PaymentMethodType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AppleInvoiceParserTest {
    private final AppleInvoiceParser underTest = new AppleInvoiceParser();

    @Test
    void parse_shouldReadServicePlanCycleRenewalTotalAndCard_fromSingleItemInvoice() {
        // when
        PlatformInvoice invoice = underTest.parse(EmailFixtures.load("apple-telegram-invoice.json")).orElseThrow();

        // then
        assertThat(underTest.platform()).isEqualTo(BillingPlatform.APPLE);
        assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2025, 12, 6));
        assertThat(invoice.invoiceReference()).isEqualTo("MT0000TEST");
        assertThat(invoice.total()).isEqualTo(new Money(new BigDecimal("3.99"), CurrencyCode.USD));
        assertThat(invoice.paymentMethod()).isEqualTo(new PaymentMethod(PaymentMethodType.CARD, "Visa", "4242"));
        assertThat(invoice.items()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Telegram Messenger");
            assertThat(item.planName()).isEqualTo("Telegram Premium");
            assertThat(item.cycle()).isEqualTo(BillingFrequency.MONTHLY);
            assertThat(item.renewsOn()).isEqualTo(LocalDate.of(2026, 1, 7));
            assertThat(item.price()).isEqualTo(new Money(new BigDecimal("3.99"), CurrencyCode.USD));
            assertThat(item.recurring()).isTrue();
        });
    }

    @Test
    void parse_shouldReadEveryLineItem_andMarkOneTimePurchasesAsNotRecurring() {
        // when
        PlatformInvoice invoice = underTest.parse(EmailFixtures.load("apple-multi-item-invoice.json")).orElseThrow();

        // then
        assertThat(invoice.items())
                .extracting(LineItem::title, LineItem::planName, LineItem::cycle, LineItem::recurring)
                .containsExactly(
                        tuple("Telegram Messenger", "Telegram Premium", BillingFrequency.MONTHLY, true),
                        tuple("iCloud+", "200 GB", BillingFrequency.ANNUAL, true),
                        tuple("Puzzle Quest", "100 Coins", null, false));
        assertThat(invoice.items()).extracting(item -> item.price().amount())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.99"), new BigDecimal("29.99"), new BigDecimal("0.99"));
        assertThat(invoice.total().amount()).isEqualByComparingTo("34.97");
    }

    @Test
    void parse_shouldReturnOnlyNonRecurringItems_forOneTimePurchase() {
        // when
        PlatformInvoice invoice = underTest.parse(EmailFixtures.load("apple-one-time-purchase.json")).orElseThrow();

        // then
        assertThat(invoice.items()).extracting(LineItem::recurring).containsExactly(false);
    }

    @Test
    void supports_shouldAcceptAppleInvoicesOnly() {
        // when / then
        assertThat(underTest.supports(EmailFixtures.load("apple-telegram-invoice.json"))).isTrue();
        assertThat(underTest.supports(EmailFixtures.load("hetzner-invoice.json"))).isFalse();
    }
}

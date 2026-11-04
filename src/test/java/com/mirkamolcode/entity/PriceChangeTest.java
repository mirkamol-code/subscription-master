package com.mirkamolcode.entity;

import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.PriceChangeSource;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceChangeTest {
    private static final LocalDate NEXT_INVOICE = LocalDate.of(2026, 11, 30);

    private Subscription subscription;

    @BeforeEach
    void setUp() {
        subscription = new Subscription(new User("alex.doe@example.com", Set.of(Role.USER)), "Netflix", new BigDecimal("19.99"),
                CurrencyCode.USD, BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT,
                LocalDate.of(2026, 9, 30));
        subscription.recordPayment(LocalDate.of(2026, 10, 30), new BigDecimal("19.99"), CurrencyCode.USD, BillingFrequency.MONTHLY,
                null, Confidence.HIGH);
    }

    @Test
    void fromInvoice_shouldRecordOldAndNewPrice_whenConfidentSameCycleInvoiceChargesNewAmount() {
        // when
        Optional<PriceChange> change = invoice("22.99", CurrencyCode.USD, BillingFrequency.MONTHLY, Confidence.HIGH, false);

        // then
        assertThat(change).hasValueSatisfying(recorded -> {
            assertThat(recorded.getOldAmount()).isEqualByComparingTo("19.99");
            assertThat(recorded.getNewAmount()).isEqualByComparingTo("22.99");
            assertThat(recorded.getCurrency()).isEqualTo(CurrencyCode.USD);
            assertThat(recorded.getEffectiveOn()).isEqualTo(NEXT_INVOICE);
            assertThat(recorded.getSource()).isEqualTo(PriceChangeSource.INVOICE);
        });
    }

    @Test
    void fromInvoice_shouldIgnoreAmountChange_forUsageBasedMerchant() {
        // then
        assertThat(invoice("24.37", CurrencyCode.USD, BillingFrequency.MONTHLY, Confidence.HIGH, true)).isEmpty();
    }

    @Test
    void fromInvoice_shouldIgnoreCurrencySwitch() {
        // then
        assertThat(invoice("22.99", CurrencyCode.EUR, BillingFrequency.MONTHLY, Confidence.HIGH, false)).isEmpty();
    }

    @Test
    void fromInvoice_shouldIgnoreCycleSwitch() {
        // then
        assertThat(invoice("199.99", CurrencyCode.USD, BillingFrequency.ANNUAL, Confidence.HIGH, false)).isEmpty();
    }

    @Test
    void fromInvoice_shouldIgnoreGuessedAmount() {
        // then
        assertThat(invoice("22.99", CurrencyCode.USD, BillingFrequency.MONTHLY, Confidence.LOW, false)).isEmpty();
    }

    @Test
    void fromInvoice_shouldIgnoreSameAmount() {
        // then
        assertThat(invoice("19.99", CurrencyCode.USD, BillingFrequency.MONTHLY, Confidence.HIGH, false)).isEmpty();
    }

    @Test
    void fromInvoice_shouldIgnoreOlderInvoice() {
        // when
        Optional<PriceChange> change = PriceChange.fromInvoice(subscription, null, LocalDate.of(2026, 8, 30), new BigDecimal("17.99"),
                CurrencyCode.USD, BillingFrequency.MONTHLY, Confidence.HIGH, false);

        // then
        assertThat(change).isEmpty();
    }

    private Optional<PriceChange> invoice(String amount, CurrencyCode currency, BillingFrequency cycle, Confidence confidence,
                                          boolean usageBased) {
        return PriceChange.fromInvoice(subscription, null, NEXT_INVOICE, new BigDecimal(amount), currency, cycle, confidence, usageBased);
    }
}

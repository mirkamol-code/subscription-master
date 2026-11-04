package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantResolverTest {
    private final MerchantResolver underTest = new MerchantResolver(EmailFixtures.detectionProperties());

    @Test
    void resolve_shouldKeyUnknownSenderByItsDomain_whenItsNameIsTheLegacyAppleKey() {
        // when
        Merchant merchant = underTest.resolve(message("Apple <billing@apple-billing.com>", "Your monthly invoice")).orElseThrow();

        // then
        assertThat(merchant.key()).isEqualTo("apple-billing-com");
        assertThat(merchant.displayName()).isEqualTo("Apple");
        assertThat(merchant.known()).isFalse();
    }

    @Test
    void resolve_shouldKeyUnknownSenderByItsDomain_whenItsNameIsAKnownMerchantKey() {
        // when
        Merchant merchant = underTest.resolve(message("Netflix <billing@netflix-mail.example>", "Your monthly invoice")).orElseThrow();

        // then
        assertThat(merchant.key()).isEqualTo("netflix-mail-example");
        assertThat(merchant.known()).isFalse();
    }

    @Test
    void resolve_shouldKeyUnknownSenderByItsDomain_whenItsNameLooksLikeAnUnreadPlatformKey() {
        // when
        Merchant merchant = underTest.resolve(message("Apple Unparsed <billing@shop.example>", "Your monthly invoice")).orElseThrow();

        // then
        assertThat(merchant.key()).isEqualTo("shop-example");
    }

    @Test
    void resolve_shouldKeepNameSlug_whenUnknownSenderNameIsNotReserved() {
        // when
        Merchant merchant = underTest.resolve(message("Acme Hosting <billing@acmehost.example>", "Your monthly invoice")).orElseThrow();

        // then
        assertThat(merchant.key()).isEqualTo("acme-hosting");
    }

    @Test
    void resolve_shouldNotUseLegacyAppleKey_forProcessorReceiptFromApple() {
        // when
        Merchant merchant = underTest.resolve(message("Stripe <receipts@stripe.com>", "Your receipt from Apple #1234")).orElseThrow();

        // then
        assertThat(merchant.key()).isNotEqualTo(MerchantResolver.LEGACY_APPLE_KEY).isEqualTo("apple-stripe-com");
    }

    @Test
    void resolveService_shouldNotUseLegacyAppleKey_forUnknownLineItemNamedApple() {
        // when
        Merchant merchant = underTest.resolveService("Apple");

        // then
        assertThat(merchant.key()).isEqualTo("apple-item");
    }

    private static GmailMessage message(String from, String subject) {
        return new GmailMessage("id-1", "thread-1", Instant.parse("2026-11-05T10:00:00Z"), from, subject, null,
                "Thanks for your payment. Amount charged: $9.99 per month.");
    }
}

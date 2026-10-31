package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.MessageHeaders;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SenderVerifierTest {
    private final SenderVerifier underTest = new SenderVerifier(EmailFixtures.detectionProperties());

    @Test
    void isAuthenticated_shouldAccept_whenDkimPassesForSenderOrganization() {
        // given
        GmailMessage message = messageFrom("Netflix <info@account.netflix.com>",
                "mx.google.com; dkim=pass header.i=@netflix.com header.s=s1; spf=softfail smtp.mailfrom=bounce@other.example");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isTrue();
    }

    @Test
    void isAuthenticated_shouldAccept_whenSpfPassesForSenderOrganization() {
        // given
        GmailMessage message = messageFrom("Stripe <receipts@stripe.com>",
                "mx.google.com; dkim=fail header.i=@stripe.com; spf=pass (google.com: domain of bounces+x@stripe.com) smtp.mailfrom=bounces+x@stripe.com");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isTrue();
    }

    @Test
    void isAuthenticated_shouldAccept_whenDmarcPassesForSenderDomain() {
        // given
        GmailMessage message = messageFrom("Anthropic <invoice+statements@mail.anthropic.com>",
                "mx.google.com; dmarc=pass (p=REJECT) header.from=mail.anthropic.com");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isTrue();
    }

    @Test
    void isAuthenticated_shouldReject_whenPassingDomainBelongsToAnotherOrganization() {
        // given
        GmailMessage message = messageFrom("Netflix <billing@netflix.com>",
                "mx.google.com; dkim=pass header.i=@sendgrid.net; spf=pass smtp.mailfrom=bounce@sendgrid.net; dmarc=fail header.from=netflix.com");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isFalse();
    }

    @Test
    void isAuthenticated_shouldReject_whenChecksFail() {
        // given
        GmailMessage message = messageFrom("Netflix <billing@netflix.com>",
                "mx.google.com; dkim=fail header.i=@netflix.com; spf=fail smtp.mailfrom=billing@netflix.com; dmarc=fail header.from=netflix.com");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isFalse();
    }

    @Test
    void isAuthenticated_shouldReject_whenHeaderIsMissing() {
        // given
        GmailMessage message = messageFrom("Netflix <billing@netflix.com>", null);

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isFalse();
    }

    @Test
    void isAuthenticated_shouldReject_whenCountryCodeDomainsDifferInOrganizationLabel() {
        // given
        GmailMessage message = messageFrom("Shop <billing@shop.co.uk>",
                "mx.google.com; dkim=pass header.i=@other.co.uk");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isFalse();
    }

    @Test
    void isAuthenticated_shouldReject_whenAustralianDomainsDifferInOrganizationLabel() {
        // given
        GmailMessage message = messageFrom("Shop <billing@shop.com.au>",
                "mx.google.com; dkim=pass header.i=@other.com.au");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isFalse();
    }

    @Test
    void isAuthenticated_shouldReject_whenResultsWereNotWrittenByGmail() {
        // given
        GmailMessage message = messageFrom("Netflix <billing@netflix.com>",
                "spoofer.example; dkim=pass header.i=@netflix.com; spf=pass smtp.mailfrom=billing@netflix.com");

        // when
        boolean authenticated = underTest.isAuthenticated(message);

        // then
        assertThat(authenticated).isFalse();
    }

    @Test
    void isTrusted_shouldReject_authenticatedPersonalMailbox() {
        // given
        GmailMessage message = messageFrom("Netflix Billing <netflix.billing.team@gmail.com>",
                "mx.google.com; dkim=pass header.i=@gmail.com; dmarc=pass header.from=gmail.com");

        // when
        boolean trusted = underTest.isTrusted(message);

        // then
        assertThat(trusted).isFalse();
    }

    @Test
    void isTrusted_shouldAccept_authenticatedCompanySender() {
        // given
        GmailMessage message = messageFrom("Netflix <info@account.netflix.com>",
                "mx.google.com; dkim=pass header.i=@netflix.com");

        // when
        boolean trusted = underTest.isTrusted(message);

        // then
        assertThat(trusted).isTrue();
    }

    @Test
    void isPersonalMailbox_shouldFlagFreeMailDomains() {
        // given
        GmailMessage message = messageFrom("Netflix Billing <netflix.billing.team@gmail.com>",
                "mx.google.com; dkim=pass header.i=@gmail.com; dmarc=pass header.from=gmail.com");

        // when
        boolean personal = underTest.isPersonalMailbox(message);

        // then
        assertThat(personal).isTrue();
    }

    @Test
    void isPersonalMailbox_shouldNotFlagCompanyDomains() {
        // given
        GmailMessage message = messageFrom("Netflix <info@account.netflix.com>", null);

        // when
        boolean personal = underTest.isPersonalMailbox(message);

        // then
        assertThat(personal).isFalse();
    }

    private static GmailMessage messageFrom(String from, String authenticationResults) {
        return new GmailMessage("id-1", "id-1", Instant.parse("2026-10-20T12:00:00Z"), from, "Your receipt", null, "Total: $5.00",
                List.of(), new MessageHeaders(null, null, authenticationResults));
    }
}

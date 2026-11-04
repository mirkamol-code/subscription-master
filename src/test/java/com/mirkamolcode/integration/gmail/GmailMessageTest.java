package com.mirkamolcode.integration.gmail;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailMessageTest {

    @Test
    void bodyText_shouldDecodeHtmlEntityInPlainTextBody() {
        // given
        GmailMessage message = withText("The open invoice amount of &dollar; 10.09 will soon be debited.");

        // when
        String text = message.bodyText();

        // then
        assertThat(text).isEqualTo("The open invoice amount of $ 10.09 will soon be debited.");
    }

    @Test
    void bodyText_shouldDecodeDoubleEncodedEntityInHtmlBody() {
        // given
        GmailMessage message = withHtml("<p>The open invoice amount of &amp;dollar; 10.09 will soon be debited.</p>");

        // when
        String text = message.bodyText();

        // then
        assertThat(text).isEqualTo("The open invoice amount of $ 10.09 will soon be debited.");
    }

    @Test
    void bodyText_shouldKeepHtmlBlocksAndTableRowsOnSeparateLines() {
        // given
        GmailMessage message = withHtml("""
                <div>6 December 2025</div>
                <table>
                  <tr><td><div>Telegram Messenger</div><div>Telegram Premium (Monthly)</div><div>Renews 7 January 2026</div></td>
                      <td>US$3,99</td></tr>
                  <tr><td>Subtotal</td><td>US$3,56</td></tr>
                  <tr><td>VAT charged at 12%</td><td>US$0,43</td></tr>
                  <tr><td><b>Visa &bull;&bull;&bull;&bull; 4242</b></td><td>US$3,99</td></tr>
                </table>
                <p>Line one<br>Line&nbsp;two</p>
                """);

        // when
        String text = message.bodyText();

        // then
        assertThat(text.lines()).containsExactly(
                "6 December 2025",
                "Telegram Messenger",
                "Telegram Premium (Monthly)",
                "Renews 7 January 2026",
                "US$3,99",
                "Subtotal US$3,56",
                "VAT charged at 12% US$0,43",
                "Visa •••• 4242 US$3,99",
                "Line one",
                "Line two");
    }

    private static GmailMessage withHtml(String html) {
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-10-12T05:08:00Z"), "Billing <billing@example.com>",
                "Invoice", html, null);
    }

    private static GmailMessage withText(String text) {
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-10-12T05:08:00Z"), "Billing <billing@example.com>",
                "Invoice", null, text);
    }
}

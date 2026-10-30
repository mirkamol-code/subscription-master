package com.mirkamolcode.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlSanitizerTest {
    private final EmailHtmlSanitizer underTest = new EmailHtmlSanitizer();

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<iframe src=\"https://evil.example\"></iframe>",
            "<object data=\"x.swf\"></object><embed src=\"x.swf\">",
            "<form action=\"https://evil.example\"><input name=\"card\"></form>",
            "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.example\">",
            "<base href=\"https://evil.example/\">",
            "<link rel=\"stylesheet\" href=\"https://evil.example/x.css\">",
            "<style>body{background:url(https://tracker.example/p.gif)}</style>"
    })
    void sanitize_shouldRemoveActiveAndExternalContent(String dangerous) {
        // when
        String html = underTest.sanitize("<p>Receipt</p>" + dangerous, false).html();

        // then
        assertThat(html).contains("Receipt")
                .doesNotContain("<script").doesNotContain("<iframe").doesNotContain("<object").doesNotContain("<embed")
                .doesNotContain("<form").doesNotContain("<input").doesNotContain("<meta").doesNotContain("<base")
                .doesNotContain("<link").doesNotContain("<style").doesNotContain("evil.example").doesNotContain("tracker.example");
    }

    @Test
    void sanitize_shouldRemoveEventHandlersAndScriptUrls() {
        // when
        String html = underTest.sanitize("""
                <a href="javascript:alert(1)" onclick="steal()">pay</a>
                <a href="data:text/html;base64,PHNjcmlwdD4=">open</a>
                <div onmouseover="steal()">Total $9.99</div>
                """, false).html();

        // then
        assertThat(html).contains("Total $9.99").contains("pay")
                .doesNotContain("javascript:").doesNotContain("onclick").doesNotContain("onmouseover")
                .doesNotContain("data:text/html");
    }

    @Test
    void sanitize_shouldKeepReceiptLayout_andOpenLinksSafely() {
        // when
        String html = underTest.sanitize("""
                <table style="width:100%;border-collapse:collapse"><tr>
                <td style="color:#333;font-weight:bold;padding:4px">Total</td><td>$22.99</td></tr></table>
                <a href="https://www.netflix.com/account">Account</a>
                """, false).html();

        // then
        assertThat(html).contains("<table").contains("<td").contains("font-weight:bold").contains("$22.99")
                .contains("href=\"https://www.netflix.com/account\"")
                .contains("rel=\"noopener noreferrer\"")
                .contains("target=\"_blank\"");
    }

    @Test
    void sanitize_shouldStripUrlsAndFixedPositioningFromInlineStyles() {
        // when
        String html = underTest.sanitize("""
                <div style="background-image:url('https://tracker.example/p.gif');color:red">a</div>
                <div style="position: fixed; top:0; left:0; width:100%">overlay</div>
                <div style="width:expression(alert(1));-moz-binding:url(x.xml#xss);behavior:url(x.htc)">b</div>
                """, false).html();

        // then
        assertThat(html).contains("color:red").contains("overlay")
                .doesNotContain("url(").doesNotContain("tracker.example").doesNotContain("fixed")
                .doesNotContain("expression").doesNotContain("binding").doesNotContain("behavior");
    }

    @Test
    void sanitize_shouldBlockRemoteImagesByDefault_andCountThem() {
        // when
        SanitizedEmail sanitized = underTest.sanitize("""
                <img src="https://tracker.example/open.gif?u=alex" width="1" height="1">
                <img src="http://cdn.example/logo.png">
                <img src="cid:logo@netflix">
                <img src="data:image/png;base64,iVBORw0KGgo=" alt="inline">
                """, false);

        // then
        assertThat(sanitized.html()).doesNotContain("tracker.example").doesNotContain("cdn.example").doesNotContain("cid:")
                .contains("data:image/png;base64,iVBORw0KGgo=");
        assertThat(sanitized.blockedRemoteImages()).isEqualTo(2);
    }

    @Test
    void sanitize_shouldKeepHttpsImagesOnly_whenUserOptsIn() {
        // when
        SanitizedEmail sanitized = underTest.sanitize("""
                <img src="https://cdn.example/logo.png">
                <img src="http://insecure.example/logo.png">
                """, true);

        // then
        assertThat(sanitized.html()).contains("https://cdn.example/logo.png").doesNotContain("insecure.example");
        assertThat(sanitized.blockedRemoteImages()).isEqualTo(1);
    }

    @Test
    void sanitize_shouldNotAllowNonImageDataUrlsInImages() {
        // when
        String html = underTest.sanitize("<img src=\"data:text/html;base64,PHNjcmlwdD4=\">", true).html();

        // then
        assertThat(html).doesNotContain("data:text/html");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "background:\\75 rl(https://tracker.example/p.gif)",
            "background:u\\rl(https://tracker.example/p.gif)",
            "background-image:image-set(\"https://tracker.example/p.gif\" 1x)",
            "background-image:-webkit-image-set(\"https://tracker.example/p.gif\" 1x)",
            "position:fix\\65 d",
            "p\\6fsition:fixed",
            "position:absolute;top:0",
            "content:attr(data-x)"
    })
    void sanitize_shouldDropStyleDeclarations_thatBrowsersDecodeIntoUrlsOrOverlays(String declaration) {
        // when
        String html = underTest.sanitize("<div style=\"color:red;" + declaration + "\">text</div>", false).html();

        // then
        assertThat(html).contains("color:red").contains("text")
                .doesNotContain("tracker.example").doesNotContain("\\").doesNotContain("image-set")
                .doesNotContain("position").doesNotContain("fixed").doesNotContain("attr(");
    }

    @Test
    void sanitize_shouldKeepHarmlessColorFunctions() {
        // when
        String html = underTest.sanitize("<table><tr><td style=\"color:rgb(51,51,51);background-color:rgba(0,0,0,0.1);width:calc(100% - 8px)\">x</td></tr></table>", false).html();

        // then
        assertThat(html).contains("rgb(51,51,51)").contains("rgba(0,0,0,0.1)").contains("calc(100% - 8px)");
    }

    @Test
    void sanitize_shouldCountProtocolRelativeRemoteImagesAsBlocked() {
        // when
        SanitizedEmail sanitized = underTest.sanitize("<img src=\"//tracker.example/open.gif\"><img src=\"https://cdn.example/a.png\">", false);

        // then
        assertThat(sanitized.blockedRemoteImages()).isEqualTo(2);
        assertThat(sanitized.html()).doesNotContain("tracker.example");
    }
}

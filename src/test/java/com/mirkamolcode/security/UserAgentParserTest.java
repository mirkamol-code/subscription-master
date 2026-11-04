package com.mirkamolcode.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void parse_shouldReturnUnknown_whenUserAgentIsBlank(String userAgent) {
        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(userAgent);

        // then
        assertThat(client.device()).isEqualTo("Unknown");
        assertThat(client.browser()).isEqualTo("Unknown");
        assertThat(client.os()).isEqualTo("Unknown");
    }

    @Test
    void parse_shouldIdentifyMacChrome() {
        // given
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Desktop");
        assertThat(client.browser()).isEqualTo("Chrome");
        assertThat(client.os()).isEqualTo("macOS");
    }

    @Test
    void parse_shouldIdentifyMacSafari() {
        // given
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Desktop");
        assertThat(client.browser()).isEqualTo("Safari");
        assertThat(client.os()).isEqualTo("macOS");
    }

    @Test
    void parse_shouldIdentifyWindowsEdge() {
        // given
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Desktop");
        assertThat(client.browser()).isEqualTo("Edge");
        assertThat(client.os()).isEqualTo("Windows");
    }

    @Test
    void parse_shouldIdentifyWindowsFirefox() {
        // given
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Desktop");
        assertThat(client.browser()).isEqualTo("Firefox");
        assertThat(client.os()).isEqualTo("Windows");
    }

    @Test
    void parse_shouldIdentifyLinuxChrome() {
        // given
        String ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Desktop");
        assertThat(client.browser()).isEqualTo("Chrome");
        assertThat(client.os()).isEqualTo("Linux");
    }

    @Test
    void parse_shouldIdentifyChromeOS() {
        // given
        String ua = "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Desktop");
        assertThat(client.browser()).isEqualTo("Chrome");
        assertThat(client.os()).isEqualTo("ChromeOS");
    }

    @Test
    void parse_shouldIdentifyIphoneSafari() {
        // given
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Mobile");
        assertThat(client.browser()).isEqualTo("Safari");
        assertThat(client.os()).isEqualTo("iOS");
    }

    @Test
    void parse_shouldIdentifyIphoneChrome() {
        // given
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/128.0.6613.98 Mobile/15E148 Safari/604.1";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Mobile");
        assertThat(client.browser()).isEqualTo("Chrome");
        assertThat(client.os()).isEqualTo("iOS");
    }

    @Test
    void parse_shouldIdentifyIpadSafari() {
        // given
        String ua = "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Tablet");
        assertThat(client.browser()).isEqualTo("Safari");
        assertThat(client.os()).isEqualTo("iOS");
    }

    @Test
    void parse_shouldIdentifyAndroidPhoneChrome() {
        // given
        String ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.88 Mobile Safari/537.36";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Mobile");
        assertThat(client.browser()).isEqualTo("Chrome");
        assertThat(client.os()).isEqualTo("Android");
    }

    @Test
    void parse_shouldIdentifyAndroidTabletChrome() {
        // given
        String ua = "Mozilla/5.0 (Linux; Android 14; SM-X910) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.88 Safari/537.36";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Tablet");
        assertThat(client.browser()).isEqualTo("Chrome");
        assertThat(client.os()).isEqualTo("Android");
    }

    @Test
    void parse_shouldIdentifyOpera() {
        // given
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 OPR/113.0.0.0";

        // when
        UserAgentParser.ClientInfo client = UserAgentParser.parse(ua);

        // then
        assertThat(client.device()).isEqualTo("Desktop");
        assertThat(client.browser()).isEqualTo("Opera");
        assertThat(client.os()).isEqualTo("Windows");
    }
}

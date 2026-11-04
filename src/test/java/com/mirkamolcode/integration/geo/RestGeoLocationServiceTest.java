package com.mirkamolcode.integration.geo;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class RestGeoLocationServiceTest {

    private WireMockServer wireMockServer;
    private RestGeoLocationService underTest;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        underTest = new RestGeoLocationService(
                RestClient.builder(),
                wireMockServer.baseUrl(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Unknown", "unknown"})
    void locate_shouldReturnUnknown_whenIpIsBlankOrUnknown(String ip) {
        // when
        GeoLocation location = underTest.locate(ip);

        // then
        assertThat(location).isEqualTo(GeoLocation.UNKNOWN);
        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "localhost", "Localhost"})
    void locate_shouldReturnLocalhost_whenIpIsLoopback(String ip) {
        // when
        GeoLocation location = underTest.locate(ip);

        // then
        assertThat(location).isEqualTo(GeoLocation.LOCALHOST);
        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.1", "10.0.0.1", "172.16.0.5", "172.31.255.255", "169.254.1.1"})
    void locate_shouldReturnLocalNetwork_whenIpIsPrivate(String ip) {
        // when
        GeoLocation location = underTest.locate(ip);

        // then
        assertThat(location).isEqualTo(GeoLocation.LOCAL_NETWORK);
        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    @Test
    void locate_shouldReturnCityAndCountry_whenPublicIpSucceeds() {
        // given
        wireMockServer.stubFor(get(urlEqualTo("/213.230.100.45?fields=status,country,city"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": "success",
                                  "country": "Uzbekistan",
                                  "city": "Tashkent"
                                }
                                """)));

        // when
        GeoLocation location = underTest.locate("213.230.100.45");

        // then
        assertThat(location.city()).isEqualTo("Tashkent");
        assertThat(location.country()).isEqualTo("Uzbekistan");
    }

    @Test
    void locate_shouldReturnUnknown_whenRemoteFails() {
        // given
        wireMockServer.stubFor(get(urlEqualTo("/8.8.8.8?fields=status,country,city"))
                .willReturn(aResponse().withStatus(500)));

        // when
        GeoLocation location = underTest.locate("8.8.8.8");

        // then
        assertThat(location).isEqualTo(GeoLocation.UNKNOWN);
    }
}

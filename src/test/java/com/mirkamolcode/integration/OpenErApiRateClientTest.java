package com.mirkamolcode.integration;

import com.mirkamolcode.dto.SourceRate;
import com.mirkamolcode.model.CurrencyCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenErApiRateClientTest {
    private static final String LATEST_USD = """
            {"result":"success","time_last_update_unix":1793491351,"base_code":"USD",
             "rates":{"USD":1,"UZS":12048.192771,"EUR":0.903614,"RUB":80.807,"AED":3.6725}}
            """;

    private volatile String body;
    private HttpServer fakeApi;
    private OpenErApiRateClient underTest;

    @BeforeEach
    void setUp() throws IOException {
        fakeApi = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeApi.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeApi.start();
        underTest = new OpenErApiRateClient(RestClient.builder(), "http://localhost:" + fakeApi.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() {
        fakeApi.stop(0);
    }

    @Test
    void fetchAll_shouldConvertUsdBasedRatesToUzsPerUnit_forSupportedCurrencies() {
        // given
        body = LATEST_USD;

        // when
        List<SourceRate> rates = underTest.fetchAll();

        // then
        LocalDate updatedOn = LocalDate.of(2026, 11, 1);
        assertThat(rates).containsExactlyInAnyOrder(
                new SourceRate(CurrencyCode.USD, new BigDecimal("12048.192771"), updatedOn),
                new SourceRate(CurrencyCode.EUR, new BigDecimal("13333.340089"), updatedOn),
                new SourceRate(CurrencyCode.RUB, new BigDecimal("149.098380"), updatedOn));
    }

    @Test
    void fetchAll_shouldFail_whenApiReportsAnError() {
        // given
        body = """
                {"result":"error","error-type":"unsupported-code"}
                """;

        // when / then
        assertThatThrownBy(underTest::fetchAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported-code");
    }

    @Test
    void fetchAll_shouldFail_whenResponseHasNoUzsRate() {
        // given
        body = """
                {"result":"success","time_last_update_unix":1793491351,"rates":{"USD":1,"EUR":0.903614}}
                """;

        // when / then
        assertThatThrownBy(underTest::fetchAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("open.er-api.com has no UZS rate");
    }
}

package com.mirkamolcode.integration;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.model.CurrencyCode;
import com.sun.net.httpserver.HttpServer;
import com.mirkamolcode.dto.SourceRate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbuRateClientIT extends AbstractTestConfig {
    private static final AtomicInteger cbuRequests = new AtomicInteger();
    private static volatile String cbuBody;
    private static HttpServer fakeCbu;

    @Autowired
    private CbuRateClient underTest;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetCbu() {
        cbuRequests.set(0);
        cbuBody = null;
        circuitBreakers.circuitBreaker("cbuRates").reset();
    }

    @DynamicPropertySource
    static void cbuBaseUrl(DynamicPropertyRegistry registry) throws IOException {
        fakeCbu = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeCbu.createContext("/", exchange -> {
            cbuRequests.incrementAndGet();
            String body = cbuBody;
            if (body == null) {
                exchange.sendResponseHeaders(500, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        fakeCbu.start();
        registry.add("app.cbu.base-url", () -> "http://localhost:" + fakeCbu.getAddress().getPort());
    }

    @AfterAll
    static void stopCbu() {
        fakeCbu.stop(0);
    }

    @Test
    void fetchAll_shouldStopCallingCbu_afterRepeatedFailures() {
        // given CBU has failed five times in a row
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(underTest::fetchAll).isInstanceOf(RuntimeException.class);
        assertThat(cbuRequests.get()).isEqualTo(5);

        // when
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(underTest::fetchAll).isInstanceOf(CallNotPermittedException.class);

        // then
        assertThat(cbuRequests.get()).isEqualTo(5);
    }

    @Test
    void fetchAll_shouldReturnSupportedCurrenciesOnly_withCbuEffectiveDate() {
        // given
        cbuBody = """
                [{"Ccy":"USD","Rate":"12023.46","Date":"31.10.2026"},
                 {"Ccy":"AED","Rate":"3273.87","Date":"31.10.2026"}]
                """;

        // when
        List<SourceRate> rates = underTest.fetchAll();

        // then
        assertThat(rates).containsExactly(
                new SourceRate(CurrencyCode.USD, new BigDecimal("12023.46"), LocalDate.of(2026, 10, 31)));
    }
}

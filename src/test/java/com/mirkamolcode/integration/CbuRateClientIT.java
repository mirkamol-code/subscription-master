package com.mirkamolcode.integration;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.model.CurrencyCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbuRateClientIT extends AbstractTestConfig {
    private static final AtomicInteger cbuRequests = new AtomicInteger();
    private static HttpServer failingCbu;

    @Autowired
    private CbuRateClient underTest;

    @DynamicPropertySource
    static void cbuBaseUrl(DynamicPropertyRegistry registry) throws IOException {
        failingCbu = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        failingCbu.createContext("/", exchange -> {
            cbuRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        failingCbu.start();
        registry.add("app.cbu.base-url", () -> "http://localhost:" + failingCbu.getAddress().getPort());
    }

    @AfterAll
    static void stopCbu() {
        failingCbu.stop(0);
    }

    @Test
    void find_shouldStopCallingCbu_afterRepeatedFailures() {
        // given CBU has failed five times in a row
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> underTest.find(CurrencyCode.USD))
                    .isInstanceOf(CbuRateClient.ExchangeRateUnavailableException.class);
        }
        assertThat(cbuRequests.get()).isEqualTo(5);

        // when
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> underTest.find(CurrencyCode.USD))
                    .isInstanceOf(CbuRateClient.ExchangeRateUnavailableException.class);
        }

        // then
        assertThat(cbuRequests.get()).isEqualTo(5);
    }
}

package com.mirkamolcode.integration;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CbuRateClientTest {
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer slowCbu;
    private CbuRateClient underTest;

    @BeforeEach
    void setUp() throws IOException {
        slowCbu = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slowCbu.createContext("/", exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        slowCbu.start();
        underTest = new CbuRateClient(RestClient.builder(), "http://localhost:" + slowCbu.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        slowCbu.stop(0);
    }

    @Test
    void fetchRates_shouldGiveUpQuickly_whenCbuDoesNotRespond() {
        // given a CBU server that takes 10 seconds to answer

        // when / then
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(underTest::fetchRates).isInstanceOf(RuntimeException.class));
    }
}

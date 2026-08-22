package com.mirkamolcode.integration;

import com.mirkamolcode.dto.CbuRate;
import com.mirkamolcode.model.CurrencyCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CbuRateClient {
    private final RestClient client;

    public CbuRateClient(RestClient.Builder builder,
                         @Value("${app.cbu.base-url}") String url
    ) {
        this.client = builder.baseUrl(url).build();
    }

    @CircuitBreaker(name = "cbuRates", fallbackMethod = "unavailable")
    public CbuRate[] fetchRates() {
        CbuRate[] rates = client.get().retrieve().body(CbuRate[].class);
        if (rates == null) throw new IllegalStateException("CBU returned no rates");
        return rates;
    }

    private CbuRate[] unavailable(Throwable failure) {
        throw new ExchangeRateUnavailableException(failure);
    }

    public CbuRate find(CurrencyCode currency) {
        return Arrays.stream(fetchRates())
                .filter(r -> currency.name().equals(r.code()))
                .findFirst()
                .orElseThrow(()
                        -> new IllegalArgumentException("CBU rate unavailable for " + currency));
    }


    public static class ExchangeRateUnavailableException extends RuntimeException {
        public ExchangeRateUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}

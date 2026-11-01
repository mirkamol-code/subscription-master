package com.mirkamolcode.integration;

import com.mirkamolcode.dto.CbuRate;
import com.mirkamolcode.dto.SourceRate;
import com.mirkamolcode.model.CurrencyCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(1)
public class CbuRateClient implements ExchangeRateSource {
    private static final Set<String> SUPPORTED = Arrays.stream(CurrencyCode.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    private final RestClient client;

    public CbuRateClient(RestClient.Builder builder,
                         @Value("${app.cbu.base-url}") String url,
                         @Value("${app.cbu.connect-timeout}") Duration connectTimeout,
                         @Value("${app.cbu.read-timeout}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(url).requestFactory(requestFactory).build();
    }

    @Override
    public String name() {
        return "CBU";
    }

    @Override
    public boolean isOfficial() {
        return true;
    }

    @Override
    @CircuitBreaker(name = "cbuRates")
    public List<SourceRate> fetchAll() {
        CbuRate[] rates = client.get().retrieve().body(CbuRate[].class);
        if (rates == null) throw new IllegalStateException("CBU returned no rates");
        return Arrays.stream(rates)
                .filter(rate -> SUPPORTED.contains(rate.code()))
                .map(rate -> new SourceRate(CurrencyCode.valueOf(rate.code()), rate.rate(), rate.effectiveDate()))
                .toList();
    }
}

package com.mirkamolcode.integration;

import com.mirkamolcode.dto.OpenErApiRates;
import com.mirkamolcode.dto.SourceRate;
import com.mirkamolcode.model.CurrencyCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(2)
public class OpenErApiRateClient implements ExchangeRateSource {
    private static final int RATE_SCALE = 6;

    private final RestClient client;

    public OpenErApiRateClient(RestClient.Builder builder,
                               @Value("${app.open-er-api.base-url}") String url,
                               @Value("${app.open-er-api.connect-timeout}") Duration connectTimeout,
                               @Value("${app.open-er-api.read-timeout}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(url).requestFactory(requestFactory).build();
    }

    @Override
    public String name() {
        return "open.er-api.com";
    }

    @Override
    public boolean isOfficial() {
        return false;
    }

    @Override
    @CircuitBreaker(name = "openErApiRates")
    public List<SourceRate> fetchAll() {
        OpenErApiRates response = client.get().retrieve().body(OpenErApiRates.class);
        if (response == null || !response.isSuccess())
            throw new IllegalStateException("open.er-api.com returned no rates: "
                    + (response == null ? "empty body" : response.errorType()));
        BigDecimal uzsPerUnitOfBase = response.rates().get("UZS");
        if (uzsPerUnitOfBase == null) throw new IllegalStateException("open.er-api.com has no UZS rate");
        return Arrays.stream(CurrencyCode.values())
                .filter(currency -> currency != CurrencyCode.UZS && response.rates().containsKey(currency.name()))
                .map(currency -> toSourceRate(currency, uzsPerUnitOfBase, response.rates(), response.updatedOn()))
                .toList();
    }

    private static SourceRate toSourceRate(CurrencyCode currency, BigDecimal uzsPerUnitOfBase,
                                           Map<String, BigDecimal> rates, LocalDate updatedOn) {
        BigDecimal rateToUzs = uzsPerUnitOfBase.divide(rates.get(currency.name()), RATE_SCALE, RoundingMode.HALF_UP);
        return new SourceRate(currency, rateToUzs, updatedOn);
    }
}

package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.ExchangeRatesResponse;
import com.mirkamolcode.service.ExchangeRateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/currencies")
public class CurrencyController {
    private final ExchangeRateService exchangeRateService;

    public CurrencyController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/rates")
    public ExchangeRatesResponse getRates() {
        return exchangeRateService.getCurrentRates();
    }
}

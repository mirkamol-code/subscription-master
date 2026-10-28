package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.ExchangeRatesResponse;
import com.mirkamolcode.service.CurrencyRatesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/currencies")
public class CurrencyController {
    private final CurrencyRatesService currencyRatesService;

    public CurrencyController(CurrencyRatesService currencyRatesService) {
        this.currencyRatesService = currencyRatesService;
    }

    @GetMapping("/rates")
    public ExchangeRatesResponse getRates() {
        return currencyRatesService.getCurrentRates();
    }
}

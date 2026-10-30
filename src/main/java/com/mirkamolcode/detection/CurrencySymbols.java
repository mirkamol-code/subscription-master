package com.mirkamolcode.detection;

import com.mirkamolcode.model.CurrencyCode;

import java.util.Map;

final class CurrencySymbols {
    private static final Map<String, CurrencyCode> SYMBOLS = Map.of(
            "$", CurrencyCode.USD, "€", CurrencyCode.EUR, "£", CurrencyCode.GBP, "₽", CurrencyCode.RUB,
            "₸", CurrencyCode.KZT, "₺", CurrencyCode.TRY, "¥", CurrencyCode.JPY);

    private CurrencySymbols() {
    }

    static CurrencyCode toCurrency(String symbol) {
        return SYMBOLS.get(symbol);
    }
}

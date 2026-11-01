package com.mirkamolcode.integration;

import com.mirkamolcode.dto.SourceRate;

import java.util.List;

public interface ExchangeRateSource {
    String name();

    boolean isOfficial();

    List<SourceRate> fetchAll();
}

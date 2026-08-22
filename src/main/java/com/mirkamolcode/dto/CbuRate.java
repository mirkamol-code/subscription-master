package com.mirkamolcode.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record CbuRate(
        @JsonProperty("Ccy") String code,
        @JsonProperty("Rate") BigDecimal rate,
        @JsonProperty("Date") String date) {

    public LocalDate effectiveDate() {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }
}


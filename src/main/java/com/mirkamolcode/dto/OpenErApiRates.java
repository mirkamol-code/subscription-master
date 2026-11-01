package com.mirkamolcode.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

public record OpenErApiRates(
        @JsonProperty("result") String result,
        @JsonProperty("error-type") String errorType,
        @JsonProperty("time_last_update_unix") long updatedAtEpochSecond,
        @JsonProperty("rates") Map<String, BigDecimal> rates) {

    public boolean isSuccess() {
        return "success".equals(result);
    }

    public LocalDate updatedOn() {
        return LocalDate.ofInstant(Instant.ofEpochSecond(updatedAtEpochSecond), ZoneOffset.UTC);
    }
}

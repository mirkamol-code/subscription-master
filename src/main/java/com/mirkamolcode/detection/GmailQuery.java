package com.mirkamolcode.detection;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class GmailQuery {
    private final String candidates;
    private final String lookback;

    public GmailQuery(DetectionProperties properties) {
        String senders = properties.merchants().stream()
                .flatMap(merchant -> merchant.senders().stream())
                .collect(Collectors.joining(" OR "));
        this.candidates = "{" + properties.query() + " from:(" + senders + ")}";
        this.lookback = properties.lookback();
    }

    public String buildLookbackQuery() {
        return candidates + " newer_than:" + lookback;
    }

    public String buildReceivedAfterQuery(Instant instant) {
        return candidates + " after:" + instant.getEpochSecond();
    }
}

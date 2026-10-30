package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;

import java.util.Optional;

public interface EmailParser {
    boolean supports(GmailMessage message);

    Optional<ParsedBilling> parse(GmailMessage message);
}

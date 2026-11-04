package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingPlatform;

import java.util.Optional;

public interface PlatformInvoiceParser {
    BillingPlatform platform();

    boolean supports(GmailMessage message);

    Optional<PlatformInvoice> parse(GmailMessage message);
}

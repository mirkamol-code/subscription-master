package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.SubscriptionCategory;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.gmail.detection")
public record DetectionProperties(
        String query,
        String lookback,
        Keywords keywords,
        List<MerchantConfig> merchants,
        List<String> personalMailboxDomains
) {
    public DetectionProperties {
        personalMailboxDomains = personalMailboxDomains == null ? List.of() : List.copyOf(personalMailboxDomains);
    }

    public record Keywords(
            List<String> receiptSubject,
            List<String> subscriptionSubject,
            List<String> paymentDue,
            List<String> freePlan,
            List<String> paymentConfirmation,
            List<String> cancellation,
            List<String> paymentFailed,
            List<String> promotion,
            List<String> accessEnds
    ) {
    }

    public record MerchantConfig(
            String key,
            String displayName,
            List<String> aliases,
            List<String> senders,
            SubscriptionCategory category,
            BillingFrequency defaultCycle
    ) {
        public MerchantConfig {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        public String logoDomain() {
            String sender = senders.getFirst();
            return sender.contains("@") ? sender.substring(sender.indexOf('@') + 1) : sender;
        }
    }
}

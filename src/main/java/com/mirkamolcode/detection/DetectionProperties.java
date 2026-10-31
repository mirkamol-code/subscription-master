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
        List<String> processors,
        List<String> personalMailboxDomains
) {
    public DetectionProperties {
        processors = processors == null ? List.of() : List.copyOf(processors);
        personalMailboxDomains = personalMailboxDomains == null ? List.of() : List.copyOf(personalMailboxDomains);
    }

    public record Keywords(
            List<String> receiptSubject,
            List<String> subscriptionSubject,
            List<String> paymentDue,
            List<String> freePlan,
            List<String> recurring,
            List<String> paymentConfirmation,
            List<String> cancellation,
            List<String> paymentFailed,
            List<String> promotion,
            List<String> accessEnds,
            List<String> accessEndsNow
    ) {
    }

    public record MerchantConfig(
            String key,
            String displayName,
            List<String> aliases,
            List<String> senders,
            String logoDomain,
            SubscriptionCategory category,
            BillingFrequency defaultCycle
    ) {
        public MerchantConfig {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            senders = senders == null ? List.of() : List.copyOf(senders);
            if (logoDomain == null && !senders.isEmpty())
                logoDomain = toDomain(senders.getFirst());
        }

        public List<String> namesForMatching() {
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(displayName), aliases.stream()).toList();
        }

        private static String toDomain(String sender) {
            return sender.contains("@") ? sender.substring(sender.indexOf('@') + 1) : sender;
        }
    }
}

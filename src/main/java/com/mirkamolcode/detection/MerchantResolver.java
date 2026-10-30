package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.SubscriptionCategory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class MerchantResolver {
    private final List<DetectionProperties.MerchantConfig> merchants;

    public MerchantResolver(DetectionProperties properties) {
        this.merchants = List.copyOf(properties.merchants());
    }

    public Merchant resolve(GmailMessage message) {
        return findKnown(message).orElseGet(() -> unknownMerchant(message, message.senderDomain()));
    }

    public boolean isKnownSender(GmailMessage message) {
        return findKnown(message).isPresent();
    }

    private Optional<Merchant> findKnown(GmailMessage message) {
        return merchants.stream()
                .filter(merchant -> merchant.senders().stream().anyMatch(sender -> isFrom(message, sender)))
                .findFirst()
                .map(merchant -> new Merchant(merchant.key(), merchant.displayName(), merchant.aliases(), merchant.logoDomain(),
                        merchant.category(), merchant.defaultCycle(), true));
    }

    private static boolean isFrom(GmailMessage message, String sender) {
        if (sender.contains("@"))
            return message.senderAddress().equalsIgnoreCase(sender);
        return message.isFromDomain(sender);
    }

    private static Merchant unknownMerchant(GmailMessage message, String domain) {
        String displayName = message.senderName().isBlank() ? extractOrganizationLabel(domain) : message.senderName();
        return new Merchant(toSlug(displayName), displayName, List.of(), domain, SubscriptionCategory.OTHER, null, false);
    }

    private static String extractOrganizationLabel(String domain) {
        String[] labels = domain.split("\\.");
        return labels.length >= 2 ? labels[labels.length - 2] : domain;
    }

    private static String toSlug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
    }
}

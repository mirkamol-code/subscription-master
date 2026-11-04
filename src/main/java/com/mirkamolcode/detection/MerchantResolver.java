package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingPlatform;
import com.mirkamolcode.model.SubscriptionCategory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

@Component
public class MerchantResolver {
    private static final Pattern RECEIPT_FROM = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:receipt|invoice) from ([\\p{L}\\p{N}][\\p{L}\\p{N}&' -]{0,60}?)\\s*(?:[,#(\\n]|\\.(?:\\s|$)|\\s-\\s|$)");

    public static final String LEGACY_APPLE_KEY = "apple";
    private static final String UNREAD_PLATFORM_KEY_SUFFIX = "-unparsed";
    private static final String LINE_ITEM_KEY_SUFFIX = "-item";

    private final List<DetectionProperties.MerchantConfig> merchants;
    private final List<String> processors;
    private final Map<DetectionProperties.MerchantConfig, Pattern> merchantNames = new LinkedHashMap<>();
    private final Set<String> reservedKeys;

    public MerchantResolver(DetectionProperties properties) {
        this.merchants = List.copyOf(properties.merchants());
        this.processors = List.copyOf(properties.processors());
        merchants.forEach(merchant -> merchantNames.put(merchant, anyName(merchant.namesForMatching())));
        this.reservedKeys = Stream.concat(merchants.stream().map(DetectionProperties.MerchantConfig::key), Stream.of(LEGACY_APPLE_KEY))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<Merchant> resolve(GmailMessage message) {
        if (isFromProcessor(message))
            return resolveFromContent(message);
        Optional<DetectionProperties.MerchantConfig> bySender = findBySender(message);
        if (bySender.isPresent())
            return bySender.map(MerchantResolver::toMerchant);
        if (findNamedIn(message.subject(), merchants).isPresent())
            return Optional.empty();
        return Optional.of(unknownMerchant(senderLabel(message), message.senderDomain(), toSlug(message.senderDomain())));
    }

    public Merchant resolveService(String title) {
        return findNamedIn(title, merchants)
                .map(MerchantResolver::toMerchant)
                .orElseGet(() -> unknownMerchant(title, null, toSlug(title) + LINE_ITEM_KEY_SUFFIX));
    }

    public Merchant unreadPlatformInvoice(BillingPlatform platform) {
        String key = platform.name().toLowerCase(Locale.ROOT).replace('_', '-') + UNREAD_PLATFORM_KEY_SUFFIX;
        return new Merchant(key, platform.displayName() + " purchase", List.of(), platform.logoDomain(), SubscriptionCategory.OTHER,
                null, true);
    }

    public boolean isUsageBased(String merchantKey) {
        return merchants.stream().anyMatch(merchant -> merchant.key().equals(merchantKey) && merchant.usageBased());
    }

    public boolean isKnownSender(GmailMessage message) {
        return isFromProcessor(message) || findBySender(message).isPresent();
    }

    private Optional<Merchant> resolveFromContent(GmailMessage message) {
        List<DetectionProperties.MerchantConfig> sellers = merchants.stream()
                .filter(merchant -> !sendsAs(merchant, message))
                .toList();
        Optional<DetectionProperties.MerchantConfig> named = findNamedIn(message.subject(), sellers)
                .or(() -> findNamedIn(message.bodyText(), sellers));
        if (named.isPresent())
            return named.map(MerchantResolver::toMerchant);
        Optional<DetectionProperties.MerchantConfig> processorItself = findBySender(message);
        if (processorItself.isPresent())
            return processorItself.map(MerchantResolver::toMerchant);
        return extractReceiptName(message.subject())
                .or(() -> extractReceiptName(message.bodyText()))
                .map(name -> unknownMerchant(name, null, toSlug(name) + "-" + toSlug(message.senderDomain())));
    }

    private Optional<DetectionProperties.MerchantConfig> findNamedIn(String text, List<DetectionProperties.MerchantConfig> candidates) {
        return candidates.stream()
                .flatMap(merchant -> findFirstMention(merchant, text).stream())
                .min(Comparator.comparingInt(Mention::position))
                .map(Mention::merchant);
    }

    private Optional<Mention> findFirstMention(DetectionProperties.MerchantConfig merchant, String text) {
        Matcher matcher = merchantNames.get(merchant).matcher(text);
        return matcher.find() ? Optional.of(new Mention(merchant, matcher.start())) : Optional.empty();
    }

    private record Mention(DetectionProperties.MerchantConfig merchant, int position) {
    }

    private Optional<DetectionProperties.MerchantConfig> findBySender(GmailMessage message) {
        return merchants.stream()
                .filter(merchant -> sendsAs(merchant, message))
                .findFirst();
    }

    private boolean isFromProcessor(GmailMessage message) {
        return processors.stream().anyMatch(processor -> isFrom(message, processor));
    }

    private static boolean sendsAs(DetectionProperties.MerchantConfig merchant, GmailMessage message) {
        return merchant.senders().stream().anyMatch(sender -> isFrom(message, sender));
    }

    private static boolean isFrom(GmailMessage message, String sender) {
        if (sender.contains("@"))
            return message.senderAddress().equalsIgnoreCase(sender);
        return message.isFromDomain(sender);
    }

    private static Optional<String> extractReceiptName(String text) {
        Matcher matcher = RECEIPT_FROM.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1).trim()).filter(name -> !name.isEmpty()) : Optional.empty();
    }

    private static Merchant toMerchant(DetectionProperties.MerchantConfig merchant) {
        return new Merchant(merchant.key(), merchant.displayName(), merchant.aliases(), merchant.logoDomain(),
                merchant.category(), merchant.defaultCycle(), true);
    }

    private static String senderLabel(GmailMessage message) {
        return message.senderName().isBlank() ? extractOrganizationLabel(message.senderDomain()) : message.senderName();
    }

    private Merchant unknownMerchant(String displayName, String logoDomain, String keyIfNameIsReserved) {
        String nameKey = toSlug(displayName);
        String key = isReservedKey(nameKey) ? keyIfNameIsReserved : nameKey;
        return new Merchant(key, displayName, List.of(), logoDomain, SubscriptionCategory.OTHER, null, false);
    }

    private boolean isReservedKey(String key) {
        return reservedKeys.contains(key) || key.endsWith(UNREAD_PLATFORM_KEY_SUFFIX);
    }

    private static String extractOrganizationLabel(String domain) {
        String[] labels = domain.split("\\.");
        return labels.length >= 2 ? labels[labels.length - 2] : domain;
    }

    private static String toSlug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
    }

    private static Pattern anyName(List<String> names) {
        String alternatives = String.join("|", names.stream().map(Pattern::quote).toList());
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])");
    }
}

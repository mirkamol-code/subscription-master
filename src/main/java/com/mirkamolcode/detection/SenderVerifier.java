package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class SenderVerifier {
    private static final String GMAIL_AUTHENTICATION_SERVER = "mx.google.com";
    private static final Pattern CHECK_RESULT = Pattern.compile("^\\s*(dkim|spf|dmarc)\\s*=\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Pattern> CHECKED_DOMAIN = Map.of(
            "dkim", Pattern.compile("header\\.[id]\\s*=\\s*([^\\s;]+)", Pattern.CASE_INSENSITIVE),
            "spf", Pattern.compile("smtp\\.mailfrom\\s*=\\s*([^\\s;]+)", Pattern.CASE_INSENSITIVE),
            "dmarc", Pattern.compile("header\\.from\\s*=\\s*([^\\s;]+)", Pattern.CASE_INSENSITIVE));
    private static final Set<String> SECOND_LEVEL_LABELS = Set.of("co", "com", "org", "net", "ac", "gov", "edu", "or", "ne", "go");

    private final Set<String> personalMailboxDomains;

    public SenderVerifier(DetectionProperties properties) {
        this.personalMailboxDomains = properties.personalMailboxDomains().stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isTrusted(GmailMessage message) {
        return isAuthenticated(message) && !isPersonalMailbox(message);
    }

    public boolean isAuthenticated(GmailMessage message) {
        String authenticationResults = message.headers().authenticationResults();
        String senderDomain = message.senderDomain();
        if (authenticationResults == null || senderDomain.isEmpty() || !isWrittenByGmail(authenticationResults))
            return false;
        String senderOrganization = toOrganizationDomain(senderDomain);
        return parsePassingDomains(authenticationResults).stream()
                .map(SenderVerifier::toOrganizationDomain)
                .anyMatch(senderOrganization::equals);
    }

    public boolean isPersonalMailbox(GmailMessage message) {
        return personalMailboxDomains.contains(message.senderDomain());
    }

    private static boolean isWrittenByGmail(String authenticationResults) {
        String authenticationServer = authenticationResults.split(";", 2)[0].trim();
        return GMAIL_AUTHENTICATION_SERVER.equalsIgnoreCase(authenticationServer);
    }

    private static List<String> parsePassingDomains(String authenticationResults) {
        return Arrays.stream(authenticationResults.split(";"))
                .map(SenderVerifier::findPassingDomain)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<String> findPassingDomain(String clause) {
        Matcher result = CHECK_RESULT.matcher(clause);
        if (!result.find() || !"pass".equalsIgnoreCase(result.group(2)))
            return Optional.empty();
        Matcher domain = CHECKED_DOMAIN.get(result.group(1).toLowerCase(Locale.ROOT)).matcher(clause);
        return domain.find() ? Optional.of(extractDomain(domain.group(1))) : Optional.empty();
    }

    private static String extractDomain(String addressOrDomain) {
        int at = addressOrDomain.lastIndexOf('@');
        return addressOrDomain.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    private static String toOrganizationDomain(String domain) {
        String[] labels = domain.split("\\.");
        int count = labels.length;
        if (count < 2)
            return domain;
        boolean hasSecondLevelSuffix = count >= 3 && labels[count - 1].length() == 2 && SECOND_LEVEL_LABELS.contains(labels[count - 2]);
        int kept = hasSecondLevelSuffix ? 3 : 2;
        return String.join(".", Arrays.copyOfRange(labels, count - kept, count));
    }
}

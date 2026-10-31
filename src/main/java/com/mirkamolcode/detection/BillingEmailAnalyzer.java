package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

@Service
public class BillingEmailAnalyzer {
    private final EmailClassifier classifier;
    private final MerchantResolver merchants;
    private final SenderVerifier senders;
    private final List<EmailParser> parsers;

    public BillingEmailAnalyzer(EmailClassifier classifier, MerchantResolver merchants, SenderVerifier senders, List<EmailParser> parsers) {
        this.classifier = classifier;
        this.merchants = merchants;
        this.senders = senders;
        List<EmailParser> ordered = new ArrayList<>(parsers);
        AnnotationAwareOrderComparator.sort(ordered);
        this.parsers = List.copyOf(ordered);
    }

    public BillingEmailAnalysis analyze(GmailMessage message) {
        EmailKind kind = classifier.classify(message);
        if (kind == EmailKind.IRRELEVANT || !senders.isTrusted(message))
            return BillingEmailAnalysis.irrelevant();
        Optional<Merchant> resolved = merchants.resolve(message);
        if (resolved.isEmpty())
            return BillingEmailAnalysis.irrelevant();
        Merchant merchant = resolved.get();
        if (kind == EmailKind.CANCELLATION)
            return new BillingEmailAnalysis(kind, merchant, null, classifier.findAccessEndDate(message));
        if (kind != EmailKind.RECEIPT)
            return new BillingEmailAnalysis(kind, merchant, null, null);

        Optional<ParsedBilling> parsed = parseWithFirstMatchingParser(message).map(BillingEmailAnalyzer::dropNonPositiveAmount);
        if (classifier.mentionsFreePlan(message) && !hasAmount(parsed))
            return BillingEmailAnalysis.irrelevant();
        if (!merchant.known() && !hasRecurringBilling(parsed, message))
            return BillingEmailAnalysis.irrelevant();
        return new BillingEmailAnalysis(kind, merchant, complete(parsed, merchant, message), null);
    }

    public boolean isCandidate(GmailMessage headers) {
        if (classifier.isConversation(headers) || !senders.isTrusted(headers))
            return false;
        return merchants.isKnownSender(headers) || classifier.hasBillingSubject(headers);
    }

    private static ParsedBilling dropNonPositiveAmount(ParsedBilling billing) {
        if (billing.amount() == null || billing.amount().signum() > 0)
            return billing;
        return new ParsedBilling(null, null, billing.cycle(), billing.paymentDate(), billing.nextBillingDate(), billing.confidence());
    }

    private static boolean hasAmount(Optional<ParsedBilling> parsed) {
        return parsed.map(billing -> billing.amount() != null).orElse(false);
    }

    private boolean hasRecurringBilling(Optional<ParsedBilling> parsed, GmailMessage message) {
        boolean hasCycle = parsed.map(billing -> billing.cycle() != null).orElse(false);
        return hasAmount(parsed) && (hasCycle || classifier.mentionsRecurringBilling(message));
    }

    private Optional<ParsedBilling> parseWithFirstMatchingParser(GmailMessage message) {
        for (EmailParser parser : parsers) {
            if (!parser.supports(message))
                continue;
            Optional<ParsedBilling> parsed = parser.parse(message);
            if (parsed.isPresent())
                return parsed;
        }
        return Optional.empty();
    }

    private static ParsedBilling complete(Optional<ParsedBilling> parsed, Merchant merchant, GmailMessage message) {
        LocalDate receivedOn = LocalDate.ofInstant(message.receivedAt(), ZoneOffset.UTC);
        ParsedBilling billing = parsed.orElse(new ParsedBilling(null, null, null, null, null, Confidence.LOW));
        BillingFrequency knownCycle = billing.cycle() != null ? billing.cycle() : merchant.defaultCycle();
        BillingFrequency cycle = knownCycle != null ? knownCycle : BillingFrequency.MONTHLY;
        LocalDate paymentDate = billing.paymentDate() != null ? billing.paymentDate() : receivedOn;
        LocalDate nextBillingDate = billing.nextBillingDate() != null ? billing.nextBillingDate() : cycle.addTo(paymentDate);
        Confidence confidence = billing.amount() == null || billing.currency() == null || knownCycle == null
                ? Confidence.LOW
                : billing.confidence();
        return new ParsedBilling(billing.amount(), billing.currency(), cycle, paymentDate, nextBillingDate, confidence);
    }
}

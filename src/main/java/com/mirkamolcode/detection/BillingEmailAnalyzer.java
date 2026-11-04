package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.BillingPlatform;

import java.math.BigDecimal;
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
    private final List<PlatformInvoiceParser> platformParsers;

    public BillingEmailAnalyzer(EmailClassifier classifier, MerchantResolver merchants, SenderVerifier senders, List<EmailParser> parsers,
                                List<PlatformInvoiceParser> platformParsers) {
        this.classifier = classifier;
        this.merchants = merchants;
        this.senders = senders;
        List<EmailParser> ordered = new ArrayList<>(parsers);
        AnnotationAwareOrderComparator.sort(ordered);
        this.parsers = List.copyOf(ordered);
        this.platformParsers = List.copyOf(platformParsers);
    }

    public BillingEmailAnalysis analyze(GmailMessage message) {
        EmailKind kind = classifier.classify(message);
        if (kind == EmailKind.IRRELEVANT || !senders.isTrusted(message))
            return BillingEmailAnalysis.irrelevant();
        Optional<PlatformInvoiceParser> platformParser = kind == EmailKind.RECEIPT ? findPlatformParser(message) : Optional.empty();
        if (platformParser.isPresent()) {
            Optional<PlatformInvoice> invoice = platformParser.get().parse(message);
            if (invoice.isPresent())
                return toAnalysis(platformParser.get(), invoice.get(), message);
        }
        Optional<Merchant> resolved = merchants.resolve(message);
        if (resolved.isEmpty())
            return BillingEmailAnalysis.irrelevant();
        BillingPlatform unreadPlatform = platformParser.map(PlatformInvoiceParser::platform).orElse(null);
        Merchant merchant = unreadPlatform != null && !resolved.get().known()
                ? merchants.unreadPlatformInvoice(unreadPlatform)
                : resolved.get();
        if (kind == EmailKind.CANCELLATION)
            return BillingEmailAnalysis.of(kind, merchant, null, classifier.findAccessEndDate(message));
        if (kind == EmailKind.TRIAL_STARTED)
            return merchant.known() || classifier.confirmsTrialStart(message)
                    ? BillingEmailAnalysis.trialStarted(merchant, classifier.findTrialDetails(message))
                    : BillingEmailAnalysis.irrelevant();
        if (kind == EmailKind.PRICE_CHANGE)
            return merchant.known()
                    ? BillingEmailAnalysis.priceChange(merchant, classifier.findPriceChange(message).orElse(null))
                    : BillingEmailAnalysis.irrelevant();
        if (kind != EmailKind.RECEIPT)
            return BillingEmailAnalysis.of(kind, merchant, null, null);

        Optional<ParsedBilling> parsed = parseWithFirstMatchingParser(message).map(BillingEmailAnalyzer::dropNonPositiveAmount);
        if (classifier.mentionsFreePlan(message) && !hasAmount(parsed))
            return BillingEmailAnalysis.irrelevant();
        boolean awaitingRepeat = !merchant.known() && !hasRecurringBilling(parsed, message);
        if (awaitingRepeat && !(hasAmount(parsed) && classifier.isRecurringInvoiceCandidate(message)))
            return BillingEmailAnalysis.irrelevant();
        ParsedBilling billing = unreadPlatform == null ? complete(parsed, merchant, message) : complete(parsed, merchant, message).withLowConfidence();
        PaymentMethod paymentMethod = PaymentMethodParser.find(message.bodyText()).orElse(null);
        return new BillingEmailAnalysis(kind, List.of(new DetectedCharge(merchant, null, unreadPlatform, paymentMethod,
                InvoiceReferences.find(message.subject(), message.bodyText()), billing)), null, awaitingRepeat);
    }

    private Optional<PlatformInvoiceParser> findPlatformParser(GmailMessage message) {
        return platformParsers.stream().filter(platformParser -> platformParser.supports(message)).findFirst();
    }

    private BillingEmailAnalysis toAnalysis(PlatformInvoiceParser platformParser, PlatformInvoice invoice, GmailMessage message) {
        List<LineItem> recurring = invoice.items().stream()
                .filter(LineItem::recurring)
                .filter(item -> item.price() != null && item.price().amount().signum() > 0)
                .toList();
        if (recurring.isEmpty())
            return BillingEmailAnalysis.irrelevant();
        boolean pricesAddUp = invoice.items().size() == 1 || addsUpTo(invoice.items(), invoice.total());
        List<DetectedCharge> charges = recurring.stream()
                .map(item -> toCharge(platformParser, invoice, item, pricesAddUp, message))
                .toList();
        return new BillingEmailAnalysis(EmailKind.RECEIPT, charges, null, false);
    }

    private DetectedCharge toCharge(PlatformInvoiceParser platformParser, PlatformInvoice invoice, LineItem item, boolean pricesAddUp,
                                    GmailMessage message) {
        Merchant merchant = merchants.resolveService(item.title());
        Money price = invoice.items().size() == 1 && invoice.total() != null ? invoice.total() : item.price();
        LocalDate paidOn = invoice.invoiceDate() != null ? invoice.invoiceDate() : message.receivedOnUtc();
        BillingFrequency knownCycle = item.cycle() != null ? item.cycle() : merchant.defaultCycle();
        BillingFrequency cycle = knownCycle != null ? knownCycle : BillingFrequency.MONTHLY;
        LocalDate nextBillingDate = item.renewsOn() != null ? item.renewsOn() : cycle.addTo(paidOn);
        Confidence confidence = knownCycle != null && pricesAddUp ? Confidence.HIGH : Confidence.LOW;
        ParsedBilling billing = new ParsedBilling(price.amount(), price.currency(), cycle, paidOn, nextBillingDate, confidence);
        return new DetectedCharge(merchant, item.planName(), platformParser.platform(), invoice.paymentMethod(),
                new InvoiceReferences(invoice.invoiceReference(), null), billing);
    }

    private static boolean addsUpTo(List<LineItem> items, Money total) {
        if (total == null || items.stream().anyMatch(item -> item.price() == null || item.price().currency() != total.currency()))
            return false;
        BigDecimal sum = items.stream().map(item -> item.price().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.compareTo(total.amount()) == 0;
    }

    public boolean isCandidate(GmailMessage headers) {
        if (classifier.isConversation(headers) || !senders.isTrusted(headers))
            return false;
        return merchants.isKnownSender(headers) || classifier.hasBillingSubject(headers);
    }

    private static ParsedBilling dropNonPositiveAmount(ParsedBilling billing) {
        return billing.hasAmount() && billing.amount().signum() <= 0 ? billing.withoutAmount() : billing;
    }

    private static boolean hasAmount(Optional<ParsedBilling> parsed) {
        return parsed.map(ParsedBilling::hasAmount).orElse(false);
    }

    private boolean hasRecurringBilling(Optional<ParsedBilling> parsed, GmailMessage message) {
        boolean hasCycle = parsed.map(ParsedBilling::hasCycle).orElse(false);
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

package com.mirkamolcode.service;

import com.mirkamolcode.detection.BillingEmailAnalysis;
import com.mirkamolcode.detection.BillingEmailAnalyzer;
import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.DetectedCharge;
import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.detection.Merchant;
import com.mirkamolcode.detection.ParsedBilling;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.MerchantTombstone;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.event.SubscriptionRetrackedEvent;
import com.mirkamolcode.integration.gmail.GmailAttachment;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.repository.MerchantTombstoneRepository;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class BillingEmailRecorder {
    private static final String PDF = "application/pdf";
    private static final List<EmailKind> REPLAYED_KINDS = List.of(EmailKind.RECEIPT, EmailKind.CANCELLATION, EmailKind.PAYMENT_FAILED);

    private final BillingEmailAnalyzer analyzer;
    private final UserRepository users;
    private final BillingEmailRepository billingEmails;
    private final SubscriptionRepository subscriptions;
    private final PaymentHistoryRepository payments;
    private final MerchantTombstoneRepository tombstones;
    private final ExchangeRateService exchangeRates;
    private final ApplicationEventPublisher events;
    private final SubscriptionStatusService statusService;
    private final GmailConnectionRepository connections;
    private final RecurringInvoiceMatcher recurringInvoices;

    public BillingEmailRecorder(BillingEmailAnalyzer analyzer,
                                UserRepository users,
                                BillingEmailRepository billingEmails,
                                SubscriptionRepository subscriptions,
                                PaymentHistoryRepository payments,
                                MerchantTombstoneRepository tombstones,
                                ExchangeRateService exchangeRates,
                                ApplicationEventPublisher events,
                                SubscriptionStatusService statusService,
                                GmailConnectionRepository connections,
                                RecurringInvoiceMatcher recurringInvoices) {
        this.analyzer = analyzer;
        this.users = users;
        this.billingEmails = billingEmails;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.tombstones = tombstones;
        this.exchangeRates = exchangeRates;
        this.events = events;
        this.statusService = statusService;
        this.connections = connections;
        this.recurringInvoices = recurringInvoices;
    }

    public void record(Long userId, GmailMessage message) {
        if (connections.findLockedByUserId(userId).filter(GmailConnection::isConnected).isEmpty())
            return;
        User user = users.getReferenceById(userId);
        BillingEmailAnalysis analysis = analyzer.analyze(message);
        if (analysis.kind() == EmailKind.IRRELEVANT) {
            billingEmails.save(BillingEmail.irrelevant(user, message.id(), message.receivedAt()));
            return;
        }

        ParsedBilling billing = analysis.billing();
        BillingEmail email = BillingEmail.relevant(user, message.id(), message.threadId(), analysis.kind(),
                message.from(), message.subject(), message.receivedAt(), billing == null ? null : billing.confidence(),
                analysis.merchant().key(), analysis.accessEndsOn());
        message.attachments().stream()
                .filter(attachment -> PDF.equalsIgnoreCase(attachment.mimeType()))
                .forEach(attachment -> addAttachment(email, attachment));
        if (analysis.awaitingRepeat()) {
            recordRecurrenceCandidate(user, email, message, analysis.charges().getFirst());
            return;
        }
        billingEmails.save(email);
        analysis.charges().forEach(charge -> recordCharge(user, email, charge));
    }

    private void recordRecurrenceCandidate(User user, BillingEmail email, GmailMessage message, DetectedCharge charge) {
        ParsedBilling billing = charge.billing();
        email.holdForRecurrence(message.senderDomain(), charge.references(), billing.amount(), billing.currency(), billing.paymentDate());
        billingEmails.save(email);
        Optional<Subscription> existing = subscriptions.findByUserIdAndMerchantKeyAndIsDeletedFalse(user.getId(), charge.merchant().key());
        if (existing.isPresent()) {
            recordCharge(user, email, charge);
            existing.get().raiseRecurrenceConfidence((int) payments.countBySubscriptionId(existing.get().getId()));
            return;
        }
        List<BillingEmail> candidates = billingEmails.findByUserIdAndSenderDomainAndRecurrenceCandidateTrue(user.getId(), message.senderDomain());
        recurringInvoices.findSeries(email, candidates).ifPresent(series -> trackSeries(user, charge, series));
    }

    private void trackSeries(User user, DetectedCharge latestCharge, RecurringInvoiceMatcher.RecurringSeries series) {
        Merchant merchant = latestCharge.merchant();
        Optional<MerchantTombstone> tombstone = tombstones.findByUserIdAndMerchantKey(user.getId(), merchant.key());
        if (tombstone.map(deleted -> deleted.covers(series.latest().getReceivedAt())).orElse(false))
            return;
        BillingEmail latest = series.latest();
        Subscription subscription = new Subscription(user, merchant.displayName(), latest.getParsedAmount(), latest.getParsedCurrency(),
                series.cycle(), SubscriptionStatus.ACTIVE, merchant.category(), series.first().getBilledOn(),
                series.cycle().addTo(latest.getBilledOn()));
        subscription.linkMerchant(merchant.key(), merchant.logoDomain());
        subscription.describeBilling(null, null, latestCharge.paymentMethod());
        subscription.flagUnconfirmedRecurring(series.invoices().size());
        subscriptions.save(subscription);
        if (tombstone.isPresent())
            events.publishEvent(new SubscriptionRetrackedEvent(user.getId(), subscription.getId(), merchant.displayName(),
                    latest.getBilledOn()));
        series.invoices().forEach(invoice -> backfillPayment(subscription, invoice, series.cycle()));
    }

    private void backfillPayment(Subscription subscription, BillingEmail invoice, BillingFrequency cycle) {
        if (invoice.getSubscription() == null)
            invoice.attachTo(subscription);
        subscription.recordPayment(invoice.getBilledOn(), invoice.getParsedAmount(), invoice.getParsedCurrency(), cycle, null, Confidence.LOW);
        PaymentHistory payment = new PaymentHistory(subscription, invoice, invoice.getBilledOn(), invoice.getParsedAmount(),
                invoice.getParsedCurrency(), exchangeRates.rateToUzs(invoice.getParsedCurrency()));
        payment.referenceInvoice(invoice.getInvoiceReference());
        payments.save(payment);
        applyEvidence(subscription, invoice);
    }

    private void recordCharge(User user, BillingEmail email, DetectedCharge charge) {
        Optional<MerchantTombstone> tombstone = tombstones.findByUserIdAndMerchantKey(user.getId(), charge.merchant().key());
        if (tombstone.map(deleted -> deleted.covers(email.getReceivedAt())).orElse(false))
            return;
        Optional<TrackedSubscription> tracked = findOrCreateSubscription(user, charge, tombstone.isPresent());
        if (tracked.isEmpty())
            return;
        Subscription subscription = tracked.get().subscription();
        if (email.getSubscription() == null)
            email.attachTo(subscription);
        boolean alreadyPaid = isInvoiceAlreadyRecorded(subscription, charge);
        if (charge.isPayment() && !alreadyPaid)
            recordPayment(subscription, email, charge);
        if (alreadyPaid)
            statusService.apply(subscription, toStatusEvidence(email), email);
        else
            applyEvidence(subscription, email);
        if (tracked.get().newlyLinked())
            replayUnattachedStatusEmails(user.getId(), subscription, tombstone);
    }

    private boolean isInvoiceAlreadyRecorded(Subscription subscription, DetectedCharge charge) {
        return charge.invoiceReference() != null
                && payments.existsBySubscriptionIdAndInvoiceReference(subscription.getId(), charge.invoiceReference());
    }

    private void replayUnattachedStatusEmails(Long userId, Subscription subscription, Optional<MerchantTombstone> tombstone) {
        billingEmails.findByUserIdAndMerchantKeyAndSubscriptionIsNullAndKindInOrderByReceivedAtAsc(
                        userId, subscription.getMerchantKey(), REPLAYED_KINDS)
                .stream()
                .filter(email -> tombstone.map(deleted -> !deleted.covers(email.getReceivedAt())).orElse(true))
                .forEach(email -> {
                    email.attachTo(subscription);
                    applyEvidence(subscription, email);
                });
    }

    private void applyEvidence(Subscription subscription, BillingEmail email) {
        boolean isReceiptWithoutPayment = email.getKind() == EmailKind.RECEIPT
                && !payments.existsByBillingEmailIdAndSubscriptionId(email.getId(), subscription.getId());
        if (isReceiptWithoutPayment)
            statusService.reviewReceiptWithoutAmount(subscription, email.getReceivedAt());
        else
            statusService.apply(subscription, toStatusEvidence(email), email);
    }

    private static StatusEvidence toStatusEvidence(BillingEmail email) {
        return switch (email.getKind()) {
            case CANCELLATION -> StatusEvidence.cancellation(email.getReceivedAt(), email.getAccessEndsOn());
            case PAYMENT_FAILED -> StatusEvidence.paymentFailed(email.getReceivedAt());
            case RECEIPT, IRRELEVANT -> StatusEvidence.receipt(email.getReceivedAt());
        };
    }

    private Optional<TrackedSubscription> findOrCreateSubscription(User user, DetectedCharge charge, boolean wasDeleted) {
        Merchant merchant = charge.merchant();
        Optional<Subscription> existing = subscriptions.findByUserIdAndMerchantKeyAndIsDeletedFalse(user.getId(), merchant.key());
        if (existing.isPresent())
            return Optional.of(new TrackedSubscription(existing.get(), false));
        if (!charge.isPayment())
            return Optional.empty();
        Subscription created = subscriptions.save(createSubscription(user, charge));
        if (wasDeleted)
            events.publishEvent(new SubscriptionRetrackedEvent(user.getId(), created.getId(), merchant.displayName(),
                    charge.billing().paymentDate()));
        return Optional.of(new TrackedSubscription(created, true));
    }

    private record TrackedSubscription(Subscription subscription, boolean newlyLinked) {
    }

    private static Subscription createSubscription(User user, DetectedCharge charge) {
        Merchant merchant = charge.merchant();
        ParsedBilling billing = charge.billing();
        Subscription subscription = new Subscription(user, merchant.displayName(), billing.amount(), billing.currency(),
                billing.cycle(), SubscriptionStatus.ACTIVE, merchant.category(), billing.paymentDate(), billing.nextBillingDate());
        subscription.linkMerchant(merchant.key(), merchant.logoDomain());
        subscription.describeBilling(charge.planName(), charge.platform(), charge.paymentMethod());
        if (billing.confidence() == Confidence.LOW)
            subscription.flagForReview();
        return subscription;
    }

    private void recordPayment(Subscription subscription, BillingEmail email, DetectedCharge charge) {
        ParsedBilling billing = charge.billing();
        subscription.recordPayment(billing.paymentDate(), billing.amount(), billing.currency(), billing.cycle(),
                billing.nextBillingDate(), billing.confidence());
        if (billing.paymentDate().equals(subscription.getLastPaymentDate()))
            subscription.describeBilling(charge.planName(), charge.platform(), charge.paymentMethod());
        PaymentHistory payment = new PaymentHistory(subscription, email, billing.paymentDate(), billing.amount(), billing.currency(),
                exchangeRates.rateToUzs(billing.currency()));
        payment.referenceInvoice(charge.invoiceReference());
        payments.save(payment);
    }

    private static void addAttachment(BillingEmail email, GmailAttachment attachment) {
        email.addAttachment(attachment.attachmentId(), attachment.filename(), attachment.mimeType(), attachment.sizeBytes());
    }
}

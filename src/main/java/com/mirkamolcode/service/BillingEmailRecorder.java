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
        AccountMatch account = matchAccount(user.getId(), charge.merchant().key(), charge.references().customer());
        if (account.ambiguous()) {
            holdForAccount(email, charge);
            return;
        }
        if (account.subscription().isPresent()) {
            Subscription existing = account.subscription().get();
            recordCharge(user, email, charge);
            existing.raiseRecurrenceConfidence((int) billingEmails.countBySubscriptionIdAndRecurrenceCandidateTrue(existing.getId()));
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
        subscription.assignCustomer(latest.getCustomerReference());
        subscription.describeBilling(null, null, latestCharge.paymentMethod());
        subscription.flagUnconfirmedRecurring(series.invoices().size());
        subscriptions.save(subscription);
        returnGuessedInvoicesForReview(user.getId(), subscription);
        if (tombstone.isPresent())
            events.publishEvent(new SubscriptionRetrackedEvent(user.getId(), subscription.getId(), merchant.displayName(),
                    latest.getBilledOn()));
        series.invoices().forEach(invoice -> backfillPayment(subscription, invoice, series.cycle()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void assignAccount(BillingEmail email, Subscription subscription) {
        email.assignAccount(subscription);
        if (email.getKind() == EmailKind.RECEIPT && email.getParsedAmount() != null && !isInvoiceAlreadyRecorded(subscription, email))
            backfillPayment(subscription, email, subscription.getFrequency());
        else
            applyEvidence(subscription, email);
    }

    private void returnGuessedInvoicesForReview(Long userId, Subscription newAccount) {
        if (newAccount.getCustomerReference() == null)
            return;
        subscriptions.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(userId, newAccount.getMerchantKey()).stream()
                .filter(account -> !account.getId().equals(newAccount.getId()))
                .forEach(this::returnGuessedInvoicesForReview);
    }

    private void returnGuessedInvoicesForReview(Subscription account) {
        List<BillingEmail> guessed = billingEmails.findBySubscriptionIdAndAccountGuessedTrue(account.getId());
        if (guessed.isEmpty())
            return;
        guessed.forEach(email -> {
            payments.deleteByBillingEmailIdAndSubscriptionId(email.getId(), account.getId());
            email.returnForAccountReview();
        });
        payments.findBySubscriptionIdOrderByPaymentDateDesc(account.getId()).stream().findFirst()
                .ifPresent(latest -> account.restoreLatestPayment(latest.getPaymentDate(), latest.getOriginalAmount(),
                        latest.getOriginalCurrency()));
    }

    private void holdForAccount(BillingEmail email, DetectedCharge charge) {
        ParsedBilling billing = charge.billing();
        email.holdForAccount(charge.references(), billing == null ? null : billing.amount(), billing == null ? null : billing.currency(),
                billing == null ? null : billing.paymentDate());
    }

    private boolean isInvoiceAlreadyRecorded(Subscription subscription, BillingEmail invoice) {
        return invoice.getInvoiceReference() != null
                && payments.findBySubscriptionIdAndInvoiceReference(subscription.getId(), invoice.getInvoiceReference()).stream()
                .anyMatch(payment -> DuplicateInvoices.areSameInvoice(payment.getInvoiceReference(), payment.getPaymentDate(),
                        invoice.getInvoiceReference(), invoice.getBilledOn()));
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
        AccountMatch account = matchAccount(user.getId(), charge.merchant().key(), charge.references().customer());
        if (account.ambiguous()) {
            holdForAccount(email, charge);
            return;
        }
        Optional<TrackedSubscription> tracked = findOrCreateSubscription(user, charge, account, tombstone.isPresent());
        if (tracked.isEmpty())
            return;
        Subscription subscription = tracked.get().subscription();
        if (email.getSubscription() == null)
            email.attachTo(subscription);
        if (charge.platform() == null && charge.references().customer() == null && charge.billing() != null)
            email.markAccountGuessed(charge.references(), charge.billing().amount(), charge.billing().currency(),
                    charge.billing().paymentDate());
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
        if (charge.invoiceReference() == null || charge.billing() == null)
            return false;
        return payments.findBySubscriptionIdAndInvoiceReference(subscription.getId(), charge.invoiceReference()).stream()
                .anyMatch(payment -> DuplicateInvoices.areSameInvoice(payment.getInvoiceReference(), payment.getPaymentDate(),
                        charge.invoiceReference(), charge.billing().paymentDate()));
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

    private Optional<TrackedSubscription> findOrCreateSubscription(User user, DetectedCharge charge, AccountMatch account, boolean wasDeleted) {
        Merchant merchant = charge.merchant();
        if (account.subscription().isPresent())
            return Optional.of(new TrackedSubscription(account.subscription().get(), false));
        if (!charge.isPayment())
            return Optional.empty();
        Subscription created = subscriptions.save(createSubscription(user, charge));
        returnGuessedInvoicesForReview(user.getId(), created);
        if (wasDeleted)
            events.publishEvent(new SubscriptionRetrackedEvent(user.getId(), created.getId(), merchant.displayName(),
                    charge.billing().paymentDate()));
        return Optional.of(new TrackedSubscription(created, true));
    }

    private record TrackedSubscription(Subscription subscription, boolean newlyLinked) {
    }

    private AccountMatch matchAccount(Long userId, String merchantKey, String customerReference) {
        List<Subscription> accounts = subscriptions.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(userId, merchantKey);
        if (customerReference == null)
            return switch (accounts.size()) {
                case 0 -> AccountMatch.none();
                case 1 -> AccountMatch.of(accounts.getFirst());
                default -> AccountMatch.unclear();
            };
        Optional<Subscription> sameCustomer = accounts.stream()
                .filter(account -> customerReference.equals(account.getCustomerReference()))
                .findFirst();
        if (sameCustomer.isPresent())
            return AccountMatch.of(sameCustomer.get());
        if (accounts.size() == 1 && accounts.getFirst().getCustomerReference() == null) {
            accounts.getFirst().assignCustomer(customerReference);
            return AccountMatch.of(accounts.getFirst());
        }
        return AccountMatch.none();
    }

    private record AccountMatch(Optional<Subscription> subscription, boolean ambiguous) {
        static AccountMatch of(Subscription subscription) {
            return new AccountMatch(Optional.of(subscription), false);
        }

        static AccountMatch none() {
            return new AccountMatch(Optional.empty(), false);
        }

        static AccountMatch unclear() {
            return new AccountMatch(Optional.empty(), true);
        }
    }

    private static Subscription createSubscription(User user, DetectedCharge charge) {
        Merchant merchant = charge.merchant();
        ParsedBilling billing = charge.billing();
        Subscription subscription = new Subscription(user, merchant.displayName(), billing.amount(), billing.currency(),
                billing.cycle(), SubscriptionStatus.ACTIVE, merchant.category(), billing.paymentDate(), billing.nextBillingDate());
        subscription.linkMerchant(merchant.key(), merchant.logoDomain());
        subscription.assignCustomer(charge.references().customer());
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
        else
            subscription.fillMissingBilling(charge.planName(), charge.paymentMethod());
        PaymentHistory payment = new PaymentHistory(subscription, email, billing.paymentDate(), billing.amount(), billing.currency(),
                exchangeRates.rateToUzs(billing.currency()));
        payment.referenceInvoice(charge.invoiceReference());
        payments.save(payment);
    }

    private static void addAttachment(BillingEmail email, GmailAttachment attachment) {
        email.addAttachment(attachment.attachmentId(), attachment.filename(), attachment.mimeType(), attachment.sizeBytes());
    }
}

package com.mirkamolcode.service;

import com.mirkamolcode.detection.BillingEmailAnalysis;
import com.mirkamolcode.detection.BillingEmailAnalyzer;
import com.mirkamolcode.detection.DetectedCharge;
import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.exception.GmailDisconnectedException;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailNotFoundException;
import com.mirkamolcode.integration.gmail.GmailScopeMissingException;
import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.StatusChangeRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.service.AppleRedetectionReport.Decision;
import com.mirkamolcode.service.AppleRedetectionReport.Outcome;
import com.mirkamolcode.service.AppleRedetectionReport.PredictedService;
import com.mirkamolcode.service.AppleRedetectionReport.UserPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppleRedetectionDryRun {
    static final String LEGACY_APPLE_KEY = "apple";
    private static final String UNREAD_PLATFORM_SUFFIX = "-unparsed";

    private final SubscriptionRepository subscriptions;
    private final BillingEmailRepository billingEmails;
    private final PaymentHistoryRepository payments;
    private final StatusChangeRepository statusChanges;
    private final GmailConnectionRepository connections;
    private final GmailAccessor gmailAccessor;
    private final GmailClient gmail;
    private final BillingEmailAnalyzer analyzer;

    public AppleRedetectionDryRun(SubscriptionRepository subscriptions,
                                  BillingEmailRepository billingEmails,
                                  PaymentHistoryRepository payments,
                                  StatusChangeRepository statusChanges,
                                  GmailConnectionRepository connections,
                                  GmailAccessor gmailAccessor,
                                  GmailClient gmail,
                                  BillingEmailAnalyzer analyzer) {
        this.subscriptions = subscriptions;
        this.billingEmails = billingEmails;
        this.payments = payments;
        this.statusChanges = statusChanges;
        this.connections = connections;
        this.gmailAccessor = gmailAccessor;
        this.gmail = gmail;
        this.analyzer = analyzer;
    }

    @Transactional(readOnly = true)
    public AppleRedetectionReport run() {
        Map<Long, List<Subscription>> legacyByUser = subscriptions.findAllByMerchantKeyAndIsDeletedFalse(LEGACY_APPLE_KEY).stream()
                .collect(Collectors.groupingBy(subscription -> subscription.getUser().getId(), TreeMap::new, Collectors.toList()));
        return new AppleRedetectionReport(legacyByUser.entrySet().stream()
                .map(user -> planUser(user.getKey(), user.getValue()))
                .toList());
    }

    private UserPlan planUser(Long userId, List<Subscription> legacy) {
        List<BillingEmail> emails = billingEmails.findByUserIdAndMerchantKey(userId, LEGACY_APPLE_KEY);
        List<PaymentHistory> legacyPayments = payments.findBySubscriptionIdIn(legacy.stream().map(Subscription::getId).toList());
        LegacyRows rows = new LegacyRows(legacy, emails, legacyPayments);
        if (!isGmailConnected(userId))
            return waitingForGmail(userId, rows);
        Map<String, BillingEmailAnalysis> readable = new LinkedHashMap<>();
        int missing = 0;
        try {
            for (BillingEmail email : emails) {
                try {
                    GmailMessage message = gmailAccessor.callWithFreshToken(userId, token -> gmail.getMessage(token, email.getGmailMessageId()));
                    readable.put(email.getGmailMessageId(), analyzer.analyze(message));
                } catch (GmailNotFoundException e) {
                    missing++;
                }
            }
        } catch (GmailDisconnectedException | GmailScopeMissingException e) {
            return waitingForGmail(userId, rows);
        }
        return predictPlan(userId, rows, readable, missing);
    }

    private UserPlan predictPlan(Long userId, LegacyRows rows, Map<String, BillingEmailAnalysis> readable, int missing) {
        List<DetectedCharge> charges = readable.values().stream()
                .filter(analysis -> analysis.kind() == EmailKind.RECEIPT)
                .flatMap(analysis -> analysis.charges().stream())
                .filter(DetectedCharge::isPayment)
                .toList();
        List<PredictedService> services = predictServices(charges);
        int keptInHistoryRow = 0;
        int confirmedCarried = 0;
        int ambiguousPayments = 0;
        for (PaymentHistory payment : rows.payments()) {
            BillingEmailAnalysis analysis = payment.getBillingEmail() == null ? null : readable.get(payment.getBillingEmail().getGmailMessageId());
            if (analysis == null)
                keptInHistoryRow++;
            else if (analysis.charges().size() == 1 && !analysis.charges().getFirst().isPayment())
                confirmedCarried++;
            else if (analysis.charges().size() > 1 && !analysis.charges().stream().allMatch(DetectedCharge::isPayment))
                ambiguousPayments++;
        }
        return new UserPlan(userId, missing > 0 ? Outcome.MISSING_EMAILS : Outcome.READY,
                rows.subscriptions().size(), rows.payments().size(), rows.emails().size(), missing, services, charges.size(),
                keptInHistoryRow,
                (int) readable.values().stream().filter(analysis -> analysis.kind() == EmailKind.IRRELEVANT).count(),
                (int) charges.stream().filter(charge -> charge.merchant().key().endsWith(UNREAD_PLATFORM_SUFFIX)).count(),
                confirmedCarried, ambiguousPayments,
                decideCarryOver(rows.subscriptions().stream().anyMatch(Subscription::isHidden), services),
                decideCarryOver(hasUserEdits(rows.subscriptions()), services));
    }

    private static List<PredictedService> predictServices(List<DetectedCharge> charges) {
        Map<String, List<DetectedCharge>> byService = charges.stream()
                .collect(Collectors.groupingBy(charge -> charge.merchant().key(), TreeMap::new, Collectors.toList()));
        return byService.values().stream()
                .map(serviceCharges -> {
                    DetectedCharge latest = serviceCharges.getLast();
                    return new PredictedService(latest.merchant().key(), latest.merchant().displayName(), latest.planName(),
                            latest.billing().cycle(), serviceCharges.size());
                })
                .toList();
    }

    private static Decision decideCarryOver(boolean needed, List<PredictedService> services) {
        if (!needed)
            return Decision.NOT_NEEDED;
        return services.size() == 1 ? Decision.CARRY : Decision.AMBIGUOUS;
    }

    private boolean hasUserEdits(List<Subscription> legacy) {
        return legacy.stream().anyMatch(subscription -> subscription.getCategory() != SubscriptionCategory.OTHER
                || statusChanges.existsBySubscriptionIdAndReason(subscription.getId(), StatusChangeReason.USER));
    }

    private boolean isGmailConnected(Long userId) {
        return connections.findByUserId(userId).map(GmailConnection::isConnected).orElse(false);
    }

    private static UserPlan waitingForGmail(Long userId, LegacyRows rows) {
        return new UserPlan(userId, Outcome.WAITING_FOR_GMAIL, rows.subscriptions().size(), rows.payments().size(), rows.emails().size(),
                0, List.of(), 0, 0, 0, 0, 0, 0, Decision.NOT_NEEDED, Decision.NOT_NEEDED);
    }

    private record LegacyRows(List<Subscription> subscriptions, List<BillingEmail> emails, List<PaymentHistory> payments) {
    }
}

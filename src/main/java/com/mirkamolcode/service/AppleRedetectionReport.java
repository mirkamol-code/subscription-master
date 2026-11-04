package com.mirkamolcode.service;

import com.mirkamolcode.model.BillingFrequency;

import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record AppleRedetectionReport(List<UserPlan> users) {
    public AppleRedetectionReport {
        users = List.copyOf(users);
    }

    public UserPlan planFor(Long userId) {
        return users.stream().filter(plan -> plan.userId().equals(userId)).findFirst().orElseThrow();
    }

    public long countUsers(Outcome outcome) {
        return users.stream().filter(plan -> plan.outcome() == outcome).count();
    }

    public long countDecisions(Decision decision) {
        return users.stream().filter(plan -> plan.hidden() == decision).count()
                + users.stream().filter(plan -> plan.userEdits() == decision).count();
    }

    public int sum(ToIntFunction<UserPlan> count) {
        return users.stream().mapToInt(count).sum();
    }

    public List<String> summaryLines() {
        String totals = "users=%d ready=%d waitingForGmail=%d missingEmails=%d legacySubscriptions=%d legacyPayments=%d emails=%d "
                .formatted(users.size(), countUsers(Outcome.READY), countUsers(Outcome.WAITING_FOR_GMAIL), countUsers(Outcome.MISSING_EMAILS),
                        sum(UserPlan::legacySubscriptions), sum(UserPlan::legacyPayments), sum(UserPlan::emails))
                + "emailsMissing=%d predictedServices=%d paymentsRebuilt=%d paymentsKeptInHistoryRow=%d oneTimeEmails=%d "
                .formatted(sum(UserPlan::emailsMissing), sum(plan -> plan.services().size()), sum(UserPlan::paymentsRebuilt),
                        sum(UserPlan::paymentsKeptInHistoryRow), sum(UserPlan::oneTimeEmails))
                + "unreadAppleEmails=%d confirmedAmountsCarried=%d ambiguousPayments=%d carryDecisions=%d ambiguousDecisions=%d"
                .formatted(sum(UserPlan::unreadAppleEmails), sum(UserPlan::confirmedAmountsCarried), sum(UserPlan::ambiguousPayments),
                        countDecisions(Decision.CARRY), countDecisions(Decision.AMBIGUOUS));
        return Stream.concat(Stream.of(totals), users.stream().map(AppleRedetectionReport::describe))
                .toList();
    }

    private static String describe(UserPlan plan) {
        String services = plan.services().stream()
                .map(service -> service.name() + (service.planName() == null ? "" : " / " + service.planName())
                        + " (" + service.cycle() + ", " + service.payments() + " payments)")
                .collect(Collectors.joining("; "));
        return "user=%d outcome=%s legacyPayments=%d emailsMissing=%d rebuilt=%d keptInHistoryRow=%d hidden=%s userEdits=%s services=[%s]"
                .formatted(plan.userId(), plan.outcome(), plan.legacyPayments(), plan.emailsMissing(), plan.paymentsRebuilt(),
                        plan.paymentsKeptInHistoryRow(), plan.hidden(), plan.userEdits(), services);
    }

    public enum Outcome {
        READY,
        WAITING_FOR_GMAIL,
        MISSING_EMAILS
    }

    public enum Decision {
        NOT_NEEDED,
        CARRY,
        AMBIGUOUS
    }

    public record PredictedService(String merchantKey, String name, String planName, BillingFrequency cycle, int payments) {
    }

    public record UserPlan(
            Long userId,
            Outcome outcome,
            int legacySubscriptions,
            int legacyPayments,
            int emails,
            int emailsMissing,
            List<PredictedService> services,
            int paymentsRebuilt,
            int paymentsKeptInHistoryRow,
            int oneTimeEmails,
            int unreadAppleEmails,
            int confirmedAmountsCarried,
            int ambiguousPayments,
            Decision hidden,
            Decision userEdits
    ) {
        public UserPlan {
            services = List.copyOf(services);
        }
    }
}

package com.mirkamolcode.service;

import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.SubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.mirkamolcode.model.SubscriptionStatus.ACTIVE;
import static com.mirkamolcode.model.SubscriptionStatus.BILLING_REQUIRED;
import static com.mirkamolcode.model.SubscriptionStatus.CANCELLED;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionStatusPolicyTest {
    private static final Instant EARLIER = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-02T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final SubscriptionStatusPolicy underTest = new SubscriptionStatusPolicy();

    @Test
    void evaluate_shouldCancel_whenCancellationEmailHasNoAccessEndDate() {
        // when
        StatusDecision decision = decide(active(), StatusEvidence.cancellation(LATER, null)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(CANCELLED);
        assertThat(decision.endsOn()).isNull();
        assertThat(decision.reason()).isEqualTo(StatusChangeReason.EMAIL_CANCELLATION);
        assertThat(decision.isStatusChange()).isTrue();
    }

    @Test
    void evaluate_shouldStayActiveUntilPaidPeriodEnds_whenCancellationEmailHasNoAccessEndDate() {
        // when
        StatusDecision decision = decide(activeDueOn(LocalDate.of(2026, 11, 30)), StatusEvidence.cancellation(LATER, null)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(ACTIVE);
        assertThat(decision.endsOn()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(decision.reason()).isEqualTo(StatusChangeReason.CANCELLATION_SCHEDULED);
    }

    @Test
    void evaluate_shouldCancel_whenPaidPeriodAlreadyEndedBeforeCancellation() {
        // when
        StatusDecision decision = decide(activeDueOn(LocalDate.of(2026, 11, 5)), StatusEvidence.cancellation(LATER, null)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(CANCELLED);
        assertThat(decision.endsOn()).isNull();
    }

    @Test
    void evaluate_shouldPreferStatedAccessEnd_overPaidPeriod() {
        // when
        StatusDecision decision = decide(activeDueOn(LocalDate.of(2026, 11, 30)),
                StatusEvidence.cancellation(LATER, LocalDate.of(2026, 11, 20))).orElseThrow();

        // then
        assertThat(decision.endsOn()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void evaluate_shouldCancelAtScheduledEnd_afterPaidPeriodRunsOut() {
        // given
        StatusSnapshot scheduled = new StatusSnapshot(ACTIVE, LATER, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));

        // when
        StatusDecision decision = decide(scheduled, StatusEvidence.scheduledEnd(Instant.parse("2026-11-10T00:00:00Z"))).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(CANCELLED);
    }

    @Test
    void evaluate_shouldRequireBilling_whenDueDatePassesWithoutCancellation() {
        // given
        StatusSnapshot overdue = new StatusSnapshot(ACTIVE, EARLIER, null, LocalDate.of(2026, 11, 1));

        // when
        StatusDecision decision = decide(overdue, StatusEvidence.overdue(Instant.parse("2026-11-10T00:00:00Z"))).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(BILLING_REQUIRED);
    }

    @Test
    void evaluate_shouldStayActiveUntilAccessEnds_whenCancellationEmailHasFutureEndDate() {
        // when
        StatusDecision decision = decide(active(), StatusEvidence.cancellation(LATER, LocalDate.of(2026, 11, 20))).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(ACTIVE);
        assertThat(decision.endsOn()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(decision.reason()).isEqualTo(StatusChangeReason.CANCELLATION_SCHEDULED);
        assertThat(decision.isStatusChange()).isTrue();
    }

    @Test
    void evaluate_shouldCancelRightAway_whenAccessEndDateHasAlreadyArrived() {
        // when
        StatusDecision decision = decide(active(), StatusEvidence.cancellation(LATER, TODAY)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(CANCELLED);
        assertThat(decision.endsOn()).isNull();
    }

    @Test
    void evaluate_shouldRequireBilling_whenPaymentFailedEmailIsNewer() {
        // when
        StatusDecision decision = decide(active(), StatusEvidence.paymentFailed(LATER)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(BILLING_REQUIRED);
        assertThat(decision.reason()).isEqualTo(StatusChangeReason.EMAIL_PAYMENT_FAILED);
    }

    @Test
    void evaluate_shouldReactivate_whenNewerReceiptFollowsPaymentFailure() {
        // when
        StatusDecision decision = decide(snapshot(BILLING_REQUIRED, EARLIER, null), StatusEvidence.receipt(LATER)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(ACTIVE);
        assertThat(decision.reason()).isEqualTo(StatusChangeReason.EMAIL_RECEIPT);
        assertThat(decision.isStatusChange()).isTrue();
    }

    @Test
    void evaluate_shouldReactivate_whenNewerReceiptFollowsCancellation() {
        // when
        StatusDecision decision = decide(snapshot(CANCELLED, EARLIER, null), StatusEvidence.receipt(LATER)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(ACTIVE);
    }

    @Test
    void evaluate_shouldClearScheduledEnd_whenNewerReceiptShowsUserResubscribed() {
        // when
        StatusDecision decision = decide(snapshot(ACTIVE, EARLIER, LocalDate.of(2026, 11, 20)), StatusEvidence.receipt(LATER)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(ACTIVE);
        assertThat(decision.endsOn()).isNull();
        assertThat(decision.isStatusChange()).isTrue();
    }

    @Test
    void evaluate_shouldOnlyMoveEvidenceForward_whenReceiptConfirmsActiveSubscription() {
        // when
        StatusDecision decision = decide(snapshot(ACTIVE, EARLIER, null), StatusEvidence.receipt(LATER)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(ACTIVE);
        assertThat(decision.evidenceAt()).isEqualTo(LATER);
        assertThat(decision.isStatusChange()).isFalse();
    }

    @Test
    void evaluate_shouldIgnoreEvidence_whenItIsOlderThanLatestEvidence() {
        // given
        StatusSnapshot activeSinceLater = snapshot(ACTIVE, LATER, null);

        // when / then
        assertThat(decide(activeSinceLater, StatusEvidence.paymentFailed(EARLIER))).isEmpty();
        assertThat(decide(activeSinceLater, StatusEvidence.cancellation(EARLIER, null))).isEmpty();
        assertThat(decide(snapshot(CANCELLED, LATER, null), StatusEvidence.receipt(EARLIER))).isEmpty();
    }

    @Test
    void evaluate_shouldApplyManualMark_andLetOnlyNewerEmailsOverrideIt() {
        // given
        Instant clickedAt = Instant.parse("2026-10-15T12:00:00Z");
        StatusDecision marked = decide(active(), StatusEvidence.userMark(clickedAt, CANCELLED)).orElseThrow();
        StatusSnapshot afterMark = snapshot(marked.status(), marked.evidenceAt(), marked.endsOn());

        // when
        Optional<StatusDecision> olderReceipt = decide(afterMark, StatusEvidence.receipt(EARLIER));
        Optional<StatusDecision> newerReceipt = decide(afterMark, StatusEvidence.receipt(LATER));

        // then
        assertThat(marked.status()).isEqualTo(CANCELLED);
        assertThat(marked.reason()).isEqualTo(StatusChangeReason.USER);
        assertThat(olderReceipt).isEmpty();
        assertThat(newerReceipt).map(StatusDecision::status).contains(ACTIVE);
    }

    @Test
    void evaluate_shouldIgnoreManualMark_whenStatusIsAlreadyTheRequestedOne() {
        // when / then
        assertThat(decide(active(), StatusEvidence.userMark(LATER, ACTIVE))).isEmpty();
    }

    @Test
    void evaluate_shouldRequireBilling_whenOverdueActiveSubscriptionHasNoScheduledEnd() {
        // when
        StatusDecision decision = decide(active(), StatusEvidence.overdue(LATER)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(BILLING_REQUIRED);
        assertThat(decision.reason()).isEqualTo(StatusChangeReason.OVERDUE);
    }

    @Test
    void evaluate_shouldNeverMarkOverdue_whenSubscriptionIsCancelledOrEndingAnyway() {
        // when / then
        assertThat(decide(snapshot(ACTIVE, EARLIER, LocalDate.of(2026, 11, 20)), StatusEvidence.overdue(LATER))).isEmpty();
        assertThat(decide(snapshot(CANCELLED, EARLIER, null), StatusEvidence.overdue(LATER))).isEmpty();
        assertThat(decide(snapshot(BILLING_REQUIRED, EARLIER, null), StatusEvidence.overdue(LATER))).isEmpty();
    }

    @Test
    void evaluate_shouldCancel_whenScheduledEndArrives() {
        // when
        StatusDecision decision = decide(snapshot(ACTIVE, EARLIER, LocalDate.of(2026, 11, 10)), StatusEvidence.scheduledEnd(LATER)).orElseThrow();

        // then
        assertThat(decision.status()).isEqualTo(CANCELLED);
        assertThat(decision.endsOn()).isNull();
        assertThat(decision.reason()).isEqualTo(StatusChangeReason.SCHEDULED_END);
    }

    @Test
    void evaluate_shouldIgnoreScheduledEnd_whenNothingIsScheduledOrDateHasNotArrived() {
        // when / then
        assertThat(decide(active(), StatusEvidence.scheduledEnd(LATER))).isEmpty();
        assertThat(decide(snapshot(ACTIVE, EARLIER, LocalDate.of(2026, 11, 20)), StatusEvidence.scheduledEnd(LATER))).isEmpty();
    }

    private Optional<StatusDecision> decide(StatusSnapshot current, StatusEvidence evidence) {
        return underTest.evaluate(current, evidence, TODAY);
    }

    private static StatusSnapshot active() {
        return snapshot(ACTIVE, null, null);
    }

    private static StatusSnapshot snapshot(SubscriptionStatus status, Instant evidenceAt, LocalDate endsOn) {
        return new StatusSnapshot(status, evidenceAt, endsOn, null);
    }

    private static StatusSnapshot activeDueOn(LocalDate nextPaymentDate) {
        return new StatusSnapshot(ACTIVE, EARLIER, null, nextPaymentDate);
    }
}

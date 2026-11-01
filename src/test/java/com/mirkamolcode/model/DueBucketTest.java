package com.mirkamolcode.model;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DueBucketTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @ParameterizedTest(name = "payment due {0} days from today while {1} is {2}")
    @CsvSource({
            "-1, ACTIVE, PASSED",
            "0, ACTIVE, TODAY",
            "1, ACTIVE, NEXT_3_DAYS",
            "3, ACTIVE, NEXT_3_DAYS",
            "4, ACTIVE, NEXT_7_DAYS",
            "6, ACTIVE, NEXT_7_DAYS",
            "7, ACTIVE, LATER",
            "5, BILLING_REQUIRED, OVERDUE",
            "-3, BILLING_REQUIRED, OVERDUE"
    })
    void classifyPayment_shouldBucketByDaysUntilDue(int daysUntilDue, SubscriptionStatus status, DueBucket expected) {
        // when
        DueBucket bucket = DueBucket.classifyPayment(TODAY.plusDays(daysUntilDue), status, TODAY);

        // then
        assertThat(bucket).isEqualTo(expected);
    }

    @Test
    void classifyAccessEnd_shouldNeverBeOverdue() {
        // then
        assertThat(DueBucket.classifyAccessEnd(TODAY.minusDays(2), TODAY)).isEqualTo(DueBucket.PASSED);
        assertThat(DueBucket.classifyAccessEnd(TODAY.plusDays(2), TODAY)).isEqualTo(DueBucket.NEXT_3_DAYS);
    }
}

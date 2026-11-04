package com.mirkamolcode.entity;

import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTrialTest {

    @Test
    void isInRenewalWindow_shouldBeFalse_forTrialWithUnknownEnd() {
        // given
        Subscription trial = Subscription.startTrial(new User("alex.doe@example.com", Set.of(Role.USER)), "Acme Notes",
                SubscriptionCategory.OTHER, LocalDate.of(2026, 11, 4), null, null, null);

        // when
        boolean inWindow = trial.isInRenewalWindow(LocalDate.of(2026, 11, 5), 3, 5);

        // then
        assertThat(inWindow).isFalse();
    }
}

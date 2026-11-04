package com.mirkamolcode.detection;

import com.mirkamolcode.model.CategoryConfidence;
import com.mirkamolcode.model.SubscriptionCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryClassifierTest {
    private final DetectionProperties properties = EmailFixtures.detectionProperties();
    private final MerchantResolver merchants = new MerchantResolver(properties);
    private final CategoryClassifier underTest = new CategoryClassifier(properties);

    @Test
    void classify_shouldUseKnownMerchantCategory_withHighConfidence() {
        // when
        CategoryGuess guess = underTest.classify(merchants.resolveService("Netflix"), List.of("Your receipt"));

        // then
        assertThat(guess).isEqualTo(new CategoryGuess(SubscriptionCategory.ENTERTAINMENT, CategoryConfidence.HIGH));
    }

    @Test
    void classify_shouldUseReliableSignals_forUnknownMerchant_withMediumConfidence() {
        // when
        CategoryGuess guess = underTest.classify(unknown("Acme Host"), List.of("Your VPS hosting invoice", "acmehost.example"));

        // then
        assertThat(guess).isEqualTo(new CategoryGuess(SubscriptionCategory.INFRASTRUCTURE, CategoryConfidence.MEDIUM));
    }

    @Test
    void classify_shouldFallBackToOther_whenOnlyOneSignalMatches() {
        // when
        CategoryGuess guess = underTest.classify(unknown("Acme"), List.of("Your hosting invoice"));

        // then
        assertThat(guess).isEqualTo(new CategoryGuess(SubscriptionCategory.OTHER, CategoryConfidence.LOW));
    }

    @Test
    void classify_shouldFallBackToOther_whenSignalsPointToDifferentCategories() {
        // when
        CategoryGuess guess = underTest.classify(unknown("Acme"), List.of("VPS hosting plus online course lessons"));

        // then
        assertThat(guess).isEqualTo(new CategoryGuess(SubscriptionCategory.OTHER, CategoryConfidence.LOW));
    }

    @Test
    void classify_shouldNotGuessBrandCategory_fromSimilarName() {
        // when
        CategoryGuess guess = underTest.classify(unknown("Netflixx"), List.of("Your Netflixx receipt"));

        // then
        assertThat(guess).isEqualTo(new CategoryGuess(SubscriptionCategory.OTHER, CategoryConfidence.LOW));
    }

    private static Merchant unknown(String name) {
        return new Merchant(name.toLowerCase(), name, List.of(), null, SubscriptionCategory.OTHER, null, false);
    }
}

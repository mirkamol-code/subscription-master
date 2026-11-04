package com.mirkamolcode.detection;

import com.mirkamolcode.model.CategoryConfidence;
import com.mirkamolcode.model.SubscriptionCategory;

public record CategoryGuess(SubscriptionCategory category, CategoryConfidence confidence) {
    static final CategoryGuess UNSURE = new CategoryGuess(SubscriptionCategory.OTHER, CategoryConfidence.LOW);
}

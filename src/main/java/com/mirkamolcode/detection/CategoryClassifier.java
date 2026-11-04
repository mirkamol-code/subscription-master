package com.mirkamolcode.detection;

import com.mirkamolcode.model.CategoryConfidence;
import com.mirkamolcode.model.SubscriptionCategory;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class CategoryClassifier {
    private static final int SIGNALS_NEEDED = 2;

    private final Map<SubscriptionCategory, List<Pattern>> signals = new EnumMap<>(SubscriptionCategory.class);

    public CategoryClassifier(DetectionProperties properties) {
        properties.categorySignals().forEach((category, phrases) -> signals.put(category, phrases.stream()
                .map(phrase -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])"))
                .toList()));
    }

    public CategoryGuess classify(Merchant merchant, List<String> texts) {
        if (merchant.known())
            return new CategoryGuess(merchant.category(), CategoryConfidence.HIGH);
        String text = String.join(" ", texts.stream().filter(Objects::nonNull).toList()).toLowerCase(Locale.ROOT);
        Map<SubscriptionCategory, Long> hits = new EnumMap<>(SubscriptionCategory.class);
        signals.forEach((category, patterns) -> {
            long matched = patterns.stream().filter(pattern -> pattern.matcher(text).find()).count();
            if (matched > 0)
                hits.put(category, matched);
        });
        if (hits.size() != 1)
            return CategoryGuess.UNSURE;
        Map.Entry<SubscriptionCategory, Long> only = hits.entrySet().iterator().next();
        return only.getValue() >= SIGNALS_NEEDED ? new CategoryGuess(only.getKey(), CategoryConfidence.MEDIUM) : CategoryGuess.UNSURE;
    }
}

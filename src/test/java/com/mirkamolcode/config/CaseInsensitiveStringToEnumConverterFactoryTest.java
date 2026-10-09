package com.mirkamolcode.config;

import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseInsensitiveStringToEnumConverterFactoryTest {

    private final CaseInsensitiveStringToEnumConverterFactory factory = new CaseInsensitiveStringToEnumConverterFactory();

    @Test
    void shouldConvertStatusCaseInsensitively() {
        Converter<String, SubscriptionStatus> converter = factory.getConverter(SubscriptionStatus.class);

        assertThat(converter.convert("active")).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(converter.convert("ACTIVE")).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(converter.convert("Active")).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(converter.convert("paused")).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(converter.convert("cancelled")).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void shouldConvertCurrencyCaseInsensitively() {
        Converter<String, CurrencyCode> converter = factory.getConverter(CurrencyCode.class);

        assertThat(converter.convert("usd")).isEqualTo(CurrencyCode.USD);
        assertThat(converter.convert("USD")).isEqualTo(CurrencyCode.USD);
        assertThat(converter.convert("Eur")).isEqualTo(CurrencyCode.EUR);
        assertThat(converter.convert("uzs")).isEqualTo(CurrencyCode.UZS);
    }

    @Test
    void shouldConvertCategoryWithHyphensAndUnderscores() {
        Converter<String, SubscriptionCategory> converter = factory.getConverter(SubscriptionCategory.class);

        assertThat(converter.convert("ai_tools")).isEqualTo(SubscriptionCategory.AI_TOOLS);
        assertThat(converter.convert("ai-tools")).isEqualTo(SubscriptionCategory.AI_TOOLS);
        assertThat(converter.convert("AITOOLS")).isEqualTo(SubscriptionCategory.AI_TOOLS);
        assertThat(converter.convert("entertainment")).isEqualTo(SubscriptionCategory.ENTERTAINMENT);
    }

    @Test
    void shouldReturnNullForBlank() {
        Converter<String, SubscriptionStatus> converter = factory.getConverter(SubscriptionStatus.class);
        assertThat(converter.convert("  ")).isNull();
        assertThat(converter.convert(null)).isNull();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForUnknownValue() {
        Converter<String, SubscriptionStatus> converter = factory.getConverter(SubscriptionStatus.class);
        assertThatThrownBy(() -> converter.convert("unknown_status"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown enum constant unknown_status");
    }
}

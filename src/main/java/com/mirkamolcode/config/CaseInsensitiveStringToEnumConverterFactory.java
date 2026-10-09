package com.mirkamolcode.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

public class CaseInsensitiveStringToEnumConverterFactory implements ConverterFactory<String, Enum<?>> {

    @Override
    public <T extends Enum<?>> Converter<String, T> getConverter(Class<T> targetType) {
        return new StringToEnumConverter<>(targetType);
    }

    private static class StringToEnumConverter<T extends Enum<?>> implements Converter<String, T> {
        private final Class<T> enumType;

        StringToEnumConverter(Class<T> enumType) {
            this.enumType = enumType;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            String trimmed = source.trim();
            for (T constant : enumType.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(trimmed)
                        || constant.name().replace("_", "").equalsIgnoreCase(trimmed.replace("-", "").replace("_", ""))) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("Unknown enum constant " + trimmed + " for type " + enumType.getSimpleName());
        }
    }
}

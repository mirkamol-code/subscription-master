package com.mirkamolcode.config;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.*;

public class CustomSortHandlerMethodArgumentResolver extends SortHandlerMethodArgumentResolver {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();

    static {
        registerProperty("id");
        registerProperty("name");
        registerProperty("price");
        registerProperty("currency");
        registerProperty("frequency");
        registerProperty("status");
        registerProperty("category");
        registerProperty("startDate", "startdate", "start_date", "start-date");
        registerProperty("nextPaymentDate", "nextpaymentdate", "next_payment_date", "next-payment-date");
        registerProperty("isDeleted", "isdeleted", "is_deleted", "is-deleted");
        registerProperty("createdAt", "createdat", "created_at", "created-at");
        registerProperty("updatedAt", "updatedat", "updated_at", "updated-at");
        registerProperty("version");
    }

    private static void registerProperty(String canonicalName, String... aliases) {
        PROPERTY_MAP.put(canonicalName.toLowerCase(Locale.ROOT), canonicalName);
        PROPERTY_MAP.put(canonicalName.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT), canonicalName);
        for (String alias : aliases) {
            PROPERTY_MAP.put(alias.toLowerCase(Locale.ROOT), canonicalName);
        }
    }

    @Override
    public Sort resolveArgument(MethodParameter parameter,
                                ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) {
        String[] sortParams = webRequest.getParameterValues(getSortParameter(parameter));
        if (sortParams == null || sortParams.length == 0) {
            return super.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        }

        return parseSort(sortParams);
    }

    public static Sort parseSort(String[] sortParams) {
        if (sortParams == null || sortParams.length == 0) {
            return Sort.unsorted();
        }

        List<Sort.Order> orders = new ArrayList<>();

        // Check if serialized as array of length 2 like ["nextPaymentDate", "ASC"]
        if (sortParams.length == 2 && isDirection(sortParams[1]) && !sortParams[0].contains(",")) {
            String prop = mapProperty(sortParams[0]);
            Sort.Direction dir = parseDirection(sortParams[1]);
            orders.add(new Sort.Order(dir, prop));
            return Sort.by(orders);
        }

        for (String param : sortParams) {
            if (param == null || param.isBlank()) continue;
            String[] parts = param.split(",");
            if (parts.length == 0) continue;

            String rawProp = parts[0].trim();
            if (rawProp.isEmpty()) continue;
            String prop = mapProperty(rawProp);

            Sort.Direction dir = Sort.Direction.ASC;
            if (parts.length > 1 && parts[1] != null && !parts[1].isBlank()) {
                dir = parseDirection(parts[1].trim());
            }

            orders.add(new Sort.Order(dir, prop));
        }

        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }

    private static boolean isDirection(String val) {
        if (val == null) return false;
        String v = val.trim().toLowerCase(Locale.ROOT);
        return "asc".equals(v) || "desc".equals(v);
    }

    private static Sort.Direction parseDirection(String val) {
        if (val == null || val.isBlank()) return Sort.Direction.ASC;
        try {
            return Sort.Direction.fromString(val.trim());
        } catch (IllegalArgumentException ex) {
            return Sort.Direction.ASC;
        }
    }

    private static String mapProperty(String rawProperty) {
        if (rawProperty == null || rawProperty.isBlank()) return rawProperty;
        String normalized = rawProperty.trim().toLowerCase(Locale.ROOT);
        return PROPERTY_MAP.getOrDefault(normalized, rawProperty.trim());
    }
}

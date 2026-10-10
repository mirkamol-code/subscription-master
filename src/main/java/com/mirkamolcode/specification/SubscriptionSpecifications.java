package com.mirkamolcode.specification;

import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.entity.Subscription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

public final class SubscriptionSpecifications {
    private SubscriptionSpecifications() {
    }

    public static Specification<Subscription> ownedBy(Long userId) {
        return (root, q, b) -> b.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Subscription> visible() {
        return (root, q, b) -> b.isFalse(root.get("isDeleted"));
    }

    public static Specification<Subscription> hasStatus(SubscriptionStatus status) {
        return status == null ? Specification.unrestricted() : (root, q, b) -> b.equal(root.get("status"), status);
    }

    public static Specification<Subscription> hasCurrency(CurrencyCode currency) {
        return currency == null ? Specification.unrestricted() : (root, q, b) -> b.equal(root.get("currency"), currency);
    }

    /** TASK-02: case-insensitive category filter */
    public static Specification<Subscription> hasCategory(SubscriptionCategory category) {
        return category == null ? Specification.unrestricted() : (root, q, b) -> b.equal(root.get("category"), category);
    }

    /** TASK-01: case-insensitive LIKE search on name */
    public static Specification<Subscription> nameContains(String name) {
        if (name == null || name.isBlank()) return Specification.unrestricted();
        String pattern = "%" + name.trim().toLowerCase() + "%";
        return (root, q, b) -> b.like(b.lower(root.get("name")), pattern);
    }

    /** TASK-05: nextPaymentDate >= from */
    public static Specification<Subscription> nextPaymentDateFrom(LocalDate from) {
        return from == null ? Specification.unrestricted() : (root, q, b) -> b.greaterThanOrEqualTo(root.get("nextPaymentDate"), from);
    }

    /** TASK-05: nextPaymentDate <= to */
    public static Specification<Subscription> nextPaymentDateTo(LocalDate to) {
        return to == null ? Specification.unrestricted() : (root, q, b) -> b.lessThanOrEqualTo(root.get("nextPaymentDate"), to);
    }

    public static Specification<Subscription> priceAtLeast(BigDecimal value) {
        return value == null ? Specification.unrestricted() : (root, q, b) -> b.greaterThanOrEqualTo(root.get("price"), value);
    }

    public static Specification<Subscription> priceAtMost(BigDecimal value) {
        return value == null ? Specification.unrestricted() : (root, q, b) -> b.lessThanOrEqualTo(root.get("price"), value);
    }

    public static Specification<Subscription> priceInUzsAtLeast(BigDecimal value, Map<CurrencyCode, BigDecimal> rates) {
        if (value == null) return Specification.unrestricted();
        return (root, q, b) -> b.greaterThanOrEqualTo(buildNormalizedPriceExpression(root, b, rates), value);
    }

    public static Specification<Subscription> priceInUzsAtMost(BigDecimal value, Map<CurrencyCode, BigDecimal> rates) {
        if (value == null) return Specification.unrestricted();
        return (root, q, b) -> b.lessThanOrEqualTo(buildNormalizedPriceExpression(root, b, rates), value);
    }

    private static Expression<BigDecimal> buildNormalizedPriceExpression(Root<Subscription> root, CriteriaBuilder b, Map<CurrencyCode, BigDecimal> rates) {
        CriteriaBuilder.Case<BigDecimal> selectCase = b.<BigDecimal>selectCase();
        if (rates != null) {
            for (Map.Entry<CurrencyCode, BigDecimal> entry : rates.entrySet()) {
                if (entry.getKey() != CurrencyCode.UZS && entry.getValue() != null) {
                    selectCase = selectCase.when(
                            b.equal(root.get("currency"), entry.getKey()),
                            b.prod(root.get("price"), entry.getValue())
                    );
                }
            }
        }
        return selectCase.otherwise(root.get("price"));
    }
}

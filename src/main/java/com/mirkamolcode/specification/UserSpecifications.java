package com.mirkamolcode.specification;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.SubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.SortDirection;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {
    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    public static Specification<User> matchesSearch(String search) {
        if (search == null || search.isBlank())
            return null;
        String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, q, b) -> b.or(
                b.like(b.lower(root.get("email")), pattern, LIKE_ESCAPE),
                b.like(b.lower(root.get("displayName")), pattern, LIKE_ESCAPE));
    }

    public static Specification<User> joinedSince(Instant since) {
        return since == null ? null : (root, q, b) -> b.greaterThanOrEqualTo(root.get("createdAt"), since);
    }

    public static Specification<User> loggedInSince(Instant since) {
        return since == null ? null : (root, q, b) -> b.greaterThanOrEqualTo(root.get("lastLoginAt"), since);
    }

    public static Specification<User> hasSubscriptionWithStatus(SubscriptionStatus status) {
        return status == null ? null : (root, q, b) ->
                b.exists(visibleSubscriptionsOf(root, q, b, s -> b.equal(s.get("status"), status)));
    }

    public static Specification<User> hasSubscriptionWithMerchant(String merchantSlug) {
        if (merchantSlug == null || merchantSlug.isBlank())
            return null;
        String slug = merchantSlug.trim().toLowerCase(Locale.ROOT);
        return (root, q, b) ->
                b.exists(visibleSubscriptionsOf(root, q, b, s -> b.equal(s.get("merchantSlug"), slug)));
    }

    public static Specification<User> orderedNullsLast(Sort sort) {
        return (root, q, b) -> {
            if (!isCountQuery(q)) {
                HibernateCriteriaBuilder hb = (HibernateCriteriaBuilder) b;
                List<Order> orders = sort.stream()
                        .map(order -> (Order) hb.sort((JpaExpression<?>) root.get(order.getProperty()),
                                order.isAscending() ? SortDirection.ASCENDING : SortDirection.DESCENDING,
                                NullPrecedence.LAST))
                        .toList();
                q.orderBy(orders);
            }
            return null;
        };
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
    }

    private static Subquery<Long> visibleSubscriptionsOf(Root<User> user, CriteriaQuery<?> query, CriteriaBuilder b,
                                                         Function<Root<Subscription>, Predicate> condition) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Subscription> subscription = subquery.from(Subscription.class);
        return subquery.select(subscription.get("id")).where(
                b.equal(subscription.get("user"), user),
                b.isFalse(subscription.get("isDeleted")),
                b.isFalse(subscription.get("isHidden")),
                condition.apply(subscription));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

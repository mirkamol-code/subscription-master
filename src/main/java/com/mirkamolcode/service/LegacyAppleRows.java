package com.mirkamolcode.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LegacyAppleRows {
    public static final String LEGACY_APPLE_KEY = "apple";
    public static final String APPLE_SENDER_DOMAIN = "apple.com";

    private final NamedParameterJdbcTemplate jdbc;

    public LegacyAppleRows(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LegacySubscription> findSubscriptions() {
        return jdbc.query("""
                        select id, user_id, is_hidden, category from subscriptions
                         where merchant_key = :key and is_deleted = false order by user_id, id""",
                Map.of("key", LEGACY_APPLE_KEY),
                (row, index) -> new LegacySubscription(row.getLong("id"), row.getLong("user_id"), row.getBoolean("is_hidden"),
                        row.getString("category")));
    }

    public List<String> findEmailMessageIds(long userId) {
        return jdbc.queryForList("""
                        select gmail_message_id from billing_emails
                         where user_id = :userId and merchant_key = :key order by received_at""",
                Map.of("userId", userId, "key", LEGACY_APPLE_KEY), String.class);
    }

    public List<LegacyPayment> findPayments(List<Long> subscriptionIds) {
        return jdbc.query("""
                        select e.gmail_message_id from payment_history p
                          left join billing_emails e on e.id = p.billing_email_id
                         where p.subscription_id in (:ids)""",
                Map.of("ids", subscriptionIds),
                (row, index) -> new LegacyPayment(row.getString("gmail_message_id")));
    }

    public boolean hasUserStatusChange(long subscriptionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists (select 1 from status_changes where subscription_id = :id and reason = 'USER')""",
                Map.of("id", subscriptionId), Boolean.class));
    }

    public record LegacySubscription(long id, long userId, boolean hidden, String category) {
    }

    public record LegacyPayment(String gmailMessageId) {
    }
}

package com.mirkamolcode.service;

import com.mirkamolcode.detection.MerchantResolver;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.exception.GmailDisconnectedException;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailNotFoundException;
import com.mirkamolcode.integration.gmail.GmailScopeMissingException;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.service.AppleRedetectionReport.Decision;
import com.mirkamolcode.service.AppleRedetectionReport.Outcome;
import com.mirkamolcode.service.AppleRedetectionReport.UserPlan;
import com.mirkamolcode.service.LegacyAppleRows.LegacySubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AppleCleanUp {
    public static final String EARLIER_PURCHASES_KEY = MerchantResolver.APPLE_EARLIER_PURCHASES_KEY;
    static final int MISSING_EMAIL_ATTEMPTS_BEFORE_REBUILD = 5;

    private static final Logger log = LoggerFactory.getLogger(AppleCleanUp.class);
    private static final String REPLAY_REASON = "Apple clean-up replay";
    private static final List<String> BACKUP_QUERIES = List.of(
            "select 'subscriptions', s.id, to_jsonb(s) from subscriptions s where s.id in (:ids)",
            "select 'payment_history', p.id, to_jsonb(p) from payment_history p where p.subscription_id in (:ids)",
            "select 'billing_emails', e.id, to_jsonb(e) from billing_emails e"
                    + " where e.user_id = :userId and (e.subscription_id in (:ids) or e.merchant_key = :legacyKey)",
            "select 'email_attachments', a.id, to_jsonb(a) from email_attachments a join billing_emails e on e.id = a.billing_email_id"
                    + " where e.user_id = :userId and (e.subscription_id in (:ids) or e.merchant_key = :legacyKey)",
            "select 'status_changes', c.id, to_jsonb(c) from status_changes c where c.subscription_id in (:ids)",
            "select 'notifications', n.id, to_jsonb(n) from notifications n where n.subscription_id in (:ids)",
            "select 'price_changes', c.id, to_jsonb(c) from price_changes c where c.subscription_id in (:ids)");

    private final AppleRedetectionDryRun planner;
    private final LegacyAppleRows legacyRows;
    private final NamedParameterJdbcTemplate jdbc;
    private final SubscriptionRepository subscriptions;
    private final BillingEmailRecorder recorder;
    private final FailedGmailMessageTracker failedMessages;
    private final GmailAccessor gmailAccessor;
    private final GmailClient gmail;
    private final TransactionTemplate transactions;

    public AppleCleanUp(AppleRedetectionDryRun planner,
                        LegacyAppleRows legacyRows,
                        NamedParameterJdbcTemplate jdbc,
                        SubscriptionRepository subscriptions,
                        BillingEmailRecorder recorder,
                        FailedGmailMessageTracker failedMessages,
                        GmailAccessor gmailAccessor,
                        GmailClient gmail,
                        TransactionTemplate transactions) {
        this.planner = planner;
        this.legacyRows = legacyRows;
        this.jdbc = jdbc;
        this.subscriptions = subscriptions;
        this.recorder = recorder;
        this.failedMessages = failedMessages;
        this.gmailAccessor = gmailAccessor;
        this.gmail = gmail;
        this.transactions = transactions;
    }

    public Map<Long, UserOutcome> apply() {
        Map<Long, UserOutcome> outcomes = new TreeMap<>();
        legacyRows.findSubscriptions().stream()
                .map(LegacySubscription::userId)
                .distinct()
                .forEach(userId -> outcomes.put(userId, applyUserSafely(userId)));
        return outcomes;
    }

    private UserOutcome applyUserSafely(Long userId) {
        try {
            return applyUser(userId);
        } catch (RuntimeException e) {
            log.warn("Apple clean-up stopped for user {}; backups and queued replays are kept: {}", userId, e.getMessage());
            return recordAttempt(userId, UserOutcome.FAILED, 0);
        }
    }

    private UserOutcome applyUser(Long userId) {
        UserPlan plan = planner.planUser(userId);
        if (plan.outcome() == Outcome.WAITING_FOR_GMAIL)
            return recordAttempt(userId, UserOutcome.WAITING_FOR_GMAIL, 0);
        if (plan.outcome() == Outcome.MISSING_EMAILS
                && recordMissingEmailAttempt(userId) < MISSING_EMAIL_ATTEMPTS_BEFORE_REBUILD)
            return UserOutcome.WAITING_FOR_MISSING_EMAILS;
        List<String> replayable;
        try {
            replayable = transactions.execute(status -> detachLegacyRows(userId));
        } catch (GmailDisconnectedException | GmailScopeMissingException e) {
            return recordAttempt(userId, UserOutcome.WAITING_FOR_GMAIL, 0);
        }
        replayable.forEach(messageId -> replay(userId, messageId));
        transactions.executeWithoutResult(status -> carryUserChoices(userId, plan));
        return recordAttempt(userId, UserOutcome.REBUILT, 0);
    }

    private List<String> detachLegacyRows(Long userId) {
        List<Subscription> legacy = subscriptions.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(userId, LegacyAppleRows.LEGACY_APPLE_KEY);
        List<Long> legacyIds = legacy.stream().map(Subscription::getId).toList();
        backUp(userId, legacyIds);
        List<String> messageIds = legacyRows.findEmailMessageIds(userId);
        Set<String> readable = messageIds.stream().filter(messageId -> isReadable(userId, messageId)).collect(Collectors.toSet());
        moveUnreadablePaymentsToHistoryRow(userId, legacy.getFirst(), legacyIds, readable);
        jdbc.update("delete from payment_history where subscription_id in (:ids)", new MapSqlParameterSource("ids", legacyIds));
        if (!readable.isEmpty())
            jdbc.update("delete from billing_emails where user_id = :userId and gmail_message_id in (:readable)",
                    new MapSqlParameterSource("userId", userId).addValue("readable", readable));
        legacy.forEach(Subscription::markDeleted);
        List<String> replayable = messageIds.stream().filter(readable::contains).toList();
        replayable.forEach(messageId -> queueForReplay(userId, messageId));
        return replayable;
    }

    private void backUp(Long userId, List<Long> legacyIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("run", UUID.randomUUID())
                .addValue("userId", userId)
                .addValue("ids", legacyIds)
                .addValue("legacyKey", LegacyAppleRows.LEGACY_APPLE_KEY);
        BACKUP_QUERIES.forEach(query -> jdbc.update(
                "insert into apple_clean_up_backups (run_id, user_id, table_name, row_id, row_data) select :run, :userId, backup.* from ("
                        + query + ") backup", params));
    }

    private boolean isReadable(Long userId, String messageId) {
        try {
            gmailAccessor.callWithFreshToken(userId, token -> gmail.getMessage(token, messageId));
            return true;
        } catch (GmailNotFoundException e) {
            return false;
        }
    }

    private void moveUnreadablePaymentsToHistoryRow(Long userId, Subscription legacy, List<Long> legacyIds, Set<String> readable) {
        List<Long> unreadablePayments = new ArrayList<>(jdbc.queryForList("""
                        select p.id from payment_history p left join billing_emails e on e.id = p.billing_email_id
                         where p.subscription_id in (:ids) and (e.gmail_message_id is null or e.gmail_message_id not in (:readable))""",
                new MapSqlParameterSource("ids", legacyIds).addValue("readable", readable.isEmpty() ? Set.of("") : readable), Long.class));
        if (unreadablePayments.isEmpty())
            return;
        Subscription history = subscriptions.save(Subscription.earlierApplePurchases(legacy, EARLIER_PURCHASES_KEY));
        MapSqlParameterSource params = new MapSqlParameterSource("history", history.getId()).addValue("payments", unreadablePayments)
                .addValue("userId", userId).addValue("ids", legacyIds).addValue("legacyKey", LegacyAppleRows.LEGACY_APPLE_KEY);
        jdbc.update("update payment_history set subscription_id = :history where id in (:payments)", params);
        jdbc.update("""
                update billing_emails set subscription_id = :history
                 where user_id = :userId and (subscription_id in (:ids) or merchant_key = :legacyKey)
                   and id in (select billing_email_id from payment_history where id in (:payments))""", params);
    }

    private void queueForReplay(Long userId, String messageId) {
        jdbc.update("""
                insert into failed_gmail_messages (user_id, gmail_message_id, attempts, last_error) values (:userId, :messageId, 1, :reason)
                on conflict (user_id, gmail_message_id) do update set attempts = 1, last_error = :reason""",
                new MapSqlParameterSource("userId", userId).addValue("messageId", messageId).addValue("reason", REPLAY_REASON));
    }

    private void replay(Long userId, String messageId) {
        try {
            GmailMessage message = gmailAccessor.callWithFreshToken(userId, token -> gmail.getMessage(token, messageId));
            recorder.record(userId, message);
            failedMessages.clearFailure(userId, messageId);
        } catch (RuntimeException e) {
            log.warn("Apple clean-up could not replay message {} for user {}; the next sync retries it: {}", messageId, userId,
                    e.getMessage());
        }
    }

    private void carryUserChoices(Long userId, UserPlan plan) {
        Subscription legacy = subscriptions.findAllByUserIdAndMerchantKey(userId, LegacyAppleRows.LEGACY_APPLE_KEY).getFirst();
        List<Subscription> rebuilt = plan.services().stream()
                .flatMap(service -> subscriptions.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(userId, service.merchantKey()).stream())
                .toList();
        if (plan.hidden() == Decision.AMBIGUOUS || plan.userEdits() == Decision.AMBIGUOUS)
            rebuilt.forEach(Subscription::flagForReview);
        if (rebuilt.size() == 1)
            rebuilt.getFirst().carryUserChoicesFrom(legacy, plan.hidden() == Decision.CARRY, plan.userEdits() == Decision.CARRY);
    }

    private int recordMissingEmailAttempt(Long userId) {
        return upsertAttempt(userId, UserOutcome.WAITING_FOR_MISSING_EMAILS, 1);
    }

    private UserOutcome recordAttempt(Long userId, UserOutcome outcome, int missingEmailAttempts) {
        upsertAttempt(userId, outcome, missingEmailAttempts);
        return outcome;
    }

    private int upsertAttempt(Long userId, UserOutcome outcome, int missingEmailAttempts) {
        return jdbc.queryForObject("""
                        insert into apple_clean_up_attempts (user_id, missing_email_attempts, last_outcome, last_attempt_at)
                        values (:userId, :increment, :outcome, now())
                        on conflict (user_id) do update
                           set missing_email_attempts = apple_clean_up_attempts.missing_email_attempts + :increment,
                               last_outcome = :outcome, last_attempt_at = now()
                        returning missing_email_attempts""",
                new MapSqlParameterSource("userId", userId).addValue("increment", missingEmailAttempts)
                        .addValue("outcome", outcome.name()), Integer.class);
    }

    public enum UserOutcome {
        REBUILT,
        WAITING_FOR_GMAIL,
        WAITING_FOR_MISSING_EMAILS,
        FAILED
    }
}

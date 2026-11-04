-- Apple re-detection, Option A: read-only counts on production.
-- Every statement returns counts only. No emails, subjects, senders, card details, customer IDs,
-- Gmail message IDs or user IDs are selected.
-- Written for schema V10 (production today). Run the whole file in one session:
--   psql "$PROD_READONLY_URL" -v ON_ERROR_STOP=1 -f docs/runbooks/apple-redetection-counts.sql

BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '2s';

-- Q1 Schema version. Expect 10; if it is not, stop: the queries below assume V10 columns.
SELECT max(version::int) AS schema_version
  FROM flyway_schema_history WHERE success;

-- Q2 Who is in scope: users and live legacy "apple" subscriptions, and how many carry user-set state.
SELECT count(DISTINCT user_id)                                   AS users_in_scope,
       count(*)                                                  AS legacy_subscriptions,
       count(*) FILTER (WHERE is_hidden)                         AS hidden,
       count(*) FILTER (WHERE needs_review)                      AS needs_review,
       count(*) FILTER (WHERE review_reason = 'AMOUNT_UNREADABLE') AS waiting_for_amount,
       count(*) FILTER (WHERE category <> 'OTHER')               AS category_changed,
       count(*) FILTER (WHERE status = 'ACTIVE')                 AS active,
       count(*) FILTER (WHERE status = 'CANCELLED')              AS cancelled,
       count(*) FILTER (WHERE status = 'BILLING_REQUIRED')       AS billing_required
  FROM subscriptions
 WHERE merchant_key = 'apple' AND is_deleted = FALSE;

-- Q3 Payments on those subscriptions: how many can be rebuilt from an email, and how many have no email at all
--    (those can only ever go to the "Apple (earlier purchases)" row).
SELECT count(*)                                            AS legacy_payments,
       count(*) FILTER (WHERE p.billing_email_id IS NOT NULL) AS with_billing_email,
       count(*) FILTER (WHERE p.billing_email_id IS NULL)     AS without_billing_email,
       count(DISTINCT p.billing_email_id)                  AS distinct_emails_with_payment,
       min(p.payment_date)                                 AS oldest_payment,
       max(p.payment_date)                                 AS newest_payment
  FROM payment_history p
  JOIN subscriptions s ON s.id = p.subscription_id
 WHERE s.merchant_key = 'apple' AND s.is_deleted = FALSE;

-- Q4 Apple billing emails for in-scope users: what the job would re-read from Gmail, by kind and link state.
SELECT e.kind,
       count(*)                                      AS emails,
       count(*) FILTER (WHERE e.subscription_id IS NOT NULL) AS linked_to_subscription,
       count(*) FILTER (WHERE e.subscription_id IS NULL)     AS unlinked,
       count(*) FILTER (WHERE e.received_at < now() - interval '13 months') AS older_than_13_months
  FROM billing_emails e
 WHERE e.merchant_key = 'apple'
   AND e.user_id IN (SELECT user_id FROM subscriptions WHERE merchant_key = 'apple' AND is_deleted = FALSE)
 GROUP BY e.kind
 ORDER BY e.kind;

-- Q5 How the work is spread: number of in-scope users per bucket of Apple emails each.
SELECT CASE WHEN n = 0 THEN '0' WHEN n = 1 THEN '1' WHEN n <= 5 THEN '2-5' WHEN n <= 20 THEN '6-20' ELSE '21+' END AS emails_per_user,
       count(*) AS users
  FROM (SELECT s.user_id, count(e.id) AS n
          FROM (SELECT DISTINCT user_id FROM subscriptions WHERE merchant_key = 'apple' AND is_deleted = FALSE) s
          LEFT JOIN billing_emails e ON e.user_id = s.user_id AND e.merchant_key = 'apple'
         GROUP BY s.user_id) per_user
 GROUP BY 1
 ORDER BY min(n);

-- Q6 Gmail connection state of in-scope users: CONNECTED users can be dry-run; the rest would wait for reconnect.
SELECT coalesce(c.status, 'NO_CONNECTION') AS gmail_status,
       count(*)                            AS users
  FROM (SELECT DISTINCT user_id FROM subscriptions WHERE merchant_key = 'apple' AND is_deleted = FALSE) s
  LEFT JOIN gmail_connections c ON c.user_id = s.user_id
 GROUP BY 1
 ORDER BY 1;

-- Q7 User actions that the clean-up must carry over or flag as ambiguous.
SELECT count(*)                         AS user_status_changes,
       count(DISTINCT c.subscription_id) AS subscriptions_with_user_status_change
  FROM status_changes c
  JOIN subscriptions s ON s.id = c.subscription_id
 WHERE s.merchant_key = 'apple' AND s.is_deleted = FALSE AND c.reason = 'USER';

-- Q8 Rows outside the job's scope, reported so nothing is a surprise:
--    users who deleted their Apple row (tombstone; not replayed), and live notifications pointing at legacy rows.
SELECT (SELECT count(*) FROM merchant_tombstones WHERE merchant_key = 'apple') AS apple_tombstones,
       (SELECT count(*) FROM notifications n JOIN subscriptions s ON s.id = n.subscription_id
         WHERE s.merchant_key = 'apple' AND s.is_deleted = FALSE)             AS notifications_on_legacy_rows,
       (SELECT count(*) FROM subscriptions WHERE merchant_key = 'apple' AND is_deleted = TRUE) AS deleted_legacy_rows;

-- Q9 Apple-sent emails already filed under another merchant (e.g. a known app resolved from the receipt text).
--    They are not re-read by the job and will not get "billed through Apple" or card details.
SELECT count(*) AS apple_sent_emails_under_other_merchants,
       count(DISTINCT merchant_key) AS distinct_merchants
  FROM billing_emails
 WHERE kind <> 'IRRELEVANT' AND merchant_key <> 'apple'
   AND split_part(lower(sender), '@', 2) LIKE '%apple.com%';

ROLLBACK;

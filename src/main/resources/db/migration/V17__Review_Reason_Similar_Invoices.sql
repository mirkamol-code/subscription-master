ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_review_reason_check;
UPDATE subscriptions SET review_reason = 'SIMILAR_INVOICES' WHERE review_reason = 'UNCONFIRMED_RECURRING';
UPDATE subscriptions SET review_reason = 'SIMILAR_INVOICES_REPEATED' WHERE review_reason = 'UNCONFIRMED_RECURRING_REPEATED';
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_review_reason_check
    CHECK (review_reason IN ('AMOUNT_UNREADABLE','SIMILAR_INVOICES','SIMILAR_INVOICES_REPEATED'));

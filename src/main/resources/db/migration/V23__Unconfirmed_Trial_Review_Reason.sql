ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_review_reason_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_review_reason_check
    CHECK (review_reason IN ('AMOUNT_UNREADABLE','SIMILAR_INVOICES','SIMILAR_INVOICES_REPEATED','UNCONFIRMED_TRIAL'));

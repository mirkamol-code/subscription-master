ALTER TABLE subscriptions
    ADD COLUMN review_reason VARCHAR(32) CHECK (review_reason IN ('AMOUNT_UNREADABLE'));

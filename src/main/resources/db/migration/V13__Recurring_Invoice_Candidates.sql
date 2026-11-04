ALTER TABLE billing_emails ADD COLUMN recurrence_candidate BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE billing_emails ADD COLUMN sender_domain VARCHAR(253);
ALTER TABLE billing_emails ADD COLUMN customer_reference VARCHAR(64);
ALTER TABLE billing_emails ADD COLUMN invoice_reference VARCHAR(64);
ALTER TABLE billing_emails ADD COLUMN parsed_amount NUMERIC(19,4);
ALTER TABLE billing_emails ADD COLUMN parsed_currency VARCHAR(3)
    CHECK (parsed_currency IN ('UZS','USD','EUR','GBP','RUB','KZT','TRY','JPY','CNY'));
ALTER TABLE billing_emails ADD COLUMN billed_on DATE;
CREATE INDEX idx_billing_emails_recurrence
    ON billing_emails(user_id, sender_domain) WHERE recurrence_candidate;

ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_review_reason_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_review_reason_check
    CHECK (review_reason IN ('AMOUNT_UNREADABLE','UNCONFIRMED_RECURRING','UNCONFIRMED_RECURRING_REPEATED'));

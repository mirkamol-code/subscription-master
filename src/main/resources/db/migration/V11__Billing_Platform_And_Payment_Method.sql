ALTER TABLE subscriptions ADD COLUMN plan_name VARCHAR(120);
ALTER TABLE subscriptions ADD COLUMN billing_platform VARCHAR(16)
    CHECK (billing_platform IN ('APPLE','GOOGLE_PLAY','PAYPAL'));
ALTER TABLE subscriptions ADD COLUMN payment_method_type VARCHAR(16)
    CHECK (payment_method_type IN ('CARD','PAYPAL'));
ALTER TABLE subscriptions ADD COLUMN card_brand VARCHAR(20);
ALTER TABLE subscriptions ADD COLUMN card_last4 VARCHAR(4)
    CHECK (card_last4 ~ '^[0-9]{4}$');

ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_category_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_category_check
    CHECK (category IN ('ENTERTAINMENT','PRODUCTIVITY','AI_TOOLS','EDUCATION','HEALTH','FINANCE','INFRASTRUCTURE','OTHER'));

DROP INDEX uk_payment_history_billing_email;
CREATE UNIQUE INDEX uk_payment_history_email_subscription
    ON payment_history(billing_email_id, subscription_id) WHERE billing_email_id IS NOT NULL;

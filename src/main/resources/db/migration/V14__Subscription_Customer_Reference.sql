ALTER TABLE subscriptions ADD COLUMN customer_reference VARCHAR(64);

DROP INDEX uk_subscriptions_user_merchant;
CREATE UNIQUE INDEX uk_subscriptions_user_merchant_customer
    ON subscriptions(user_id, merchant_key, COALESCE(customer_reference, ''))
    WHERE merchant_key IS NOT NULL AND is_deleted = FALSE;

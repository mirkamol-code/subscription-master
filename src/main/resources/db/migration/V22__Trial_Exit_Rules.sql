ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_billing_known_unless_trial;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_billing_known_when_active
    CHECK (status <> 'ACTIVE'
        OR (price IS NOT NULL AND currency IS NOT NULL AND frequency IS NOT NULL AND next_payment_date IS NOT NULL));
ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_trial_end_only_on_trial;

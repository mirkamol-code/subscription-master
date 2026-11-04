ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_status_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check
    CHECK (status IN ('TRIAL','ACTIVE','CANCELLED','BILLING_REQUIRED'));

ALTER TABLE subscriptions ADD COLUMN trial_ends_on DATE;
ALTER TABLE subscriptions ALTER COLUMN price DROP NOT NULL;
ALTER TABLE subscriptions ALTER COLUMN currency DROP NOT NULL;
ALTER TABLE subscriptions ALTER COLUMN frequency DROP NOT NULL;
ALTER TABLE subscriptions ALTER COLUMN next_payment_date DROP NOT NULL;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_billing_known_unless_trial
    CHECK (status = 'TRIAL'
        OR (price IS NOT NULL AND currency IS NOT NULL AND frequency IS NOT NULL AND next_payment_date IS NOT NULL));
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_trial_end_only_on_trial
    CHECK (status = 'TRIAL' OR trial_ends_on IS NULL);

ALTER TABLE status_changes DROP CONSTRAINT status_changes_from_status_check;
ALTER TABLE status_changes ADD CONSTRAINT status_changes_from_status_check
    CHECK (from_status IN ('TRIAL','ACTIVE','CANCELLED','BILLING_REQUIRED'));
ALTER TABLE status_changes DROP CONSTRAINT status_changes_to_status_check;
ALTER TABLE status_changes ADD CONSTRAINT status_changes_to_status_check
    CHECK (to_status IN ('TRIAL','ACTIVE','CANCELLED','BILLING_REQUIRED'));

ALTER TABLE billing_emails DROP CONSTRAINT billing_emails_kind_check;
ALTER TABLE billing_emails ADD CONSTRAINT billing_emails_kind_check
    CHECK (kind IN ('RECEIPT','CANCELLATION','PAYMENT_FAILED','PRICE_CHANGE','TRIAL_STARTED','IRRELEVANT'));

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PAYMENT_DUE','TRIAL_ENDING','BILLING_REQUIRED','CANCELLED','CANCELLATION_SCHEDULED','REACTIVATED','RETRACKED',
                    'ADMIN_PAYMENT_DIGEST'));

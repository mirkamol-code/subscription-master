DROP INDEX uk_payment_history_subscription_invoice;
CREATE UNIQUE INDEX uk_payment_history_subscription_invoice_date
    ON payment_history(subscription_id, invoice_reference, payment_date) WHERE invoice_reference IS NOT NULL;

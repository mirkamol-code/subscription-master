ALTER TABLE payment_history ADD COLUMN invoice_reference VARCHAR(64);
CREATE UNIQUE INDEX uk_payment_history_subscription_invoice
    ON payment_history(subscription_id, invoice_reference) WHERE invoice_reference IS NOT NULL;

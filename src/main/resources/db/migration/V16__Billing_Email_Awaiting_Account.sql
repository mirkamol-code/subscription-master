ALTER TABLE billing_emails ADD COLUMN awaiting_account BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE billing_emails ADD COLUMN account_guessed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_billing_emails_awaiting_account ON billing_emails(user_id) WHERE awaiting_account;

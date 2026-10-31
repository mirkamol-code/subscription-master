DELETE FROM payment_history;
DELETE FROM notifications WHERE subscription_id IS NOT NULL;
DELETE FROM status_changes;
DELETE FROM billing_emails;
DELETE FROM failed_gmail_messages;
DELETE FROM subscriptions;

UPDATE gmail_connections
   SET history_id = NULL,
       last_synced_at = NULL,
       sync_state = 'IDLE',
       sync_started_at = NULL,
       sync_heartbeat_at = NULL,
       sync_processed = 0,
       sync_total = 0,
       sync_error = NULL,
       updated_at = CURRENT_TIMESTAMP;

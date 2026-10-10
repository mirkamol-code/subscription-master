-- =============================================================================
-- Seed Script: 200 Subscriptions, Users, and Sample Data
-- Target database: subscription_master_db
-- =============================================================================

-- 1. Seed Users (passwords are BCrypt hash of DemoPassword123! and AdminPassword123!)
INSERT INTO users (email, password_hash, version, created_at, updated_at)
VALUES ('admin@subscribemaster.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoO0rL5i84d62G4VpQe6Lq1B5w5B9K7eQ2', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password_hash, version, created_at, updated_at)
VALUES ('hteg9188@gmail.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoO0rL5i84d62G4VpQe6Lq1B5w5B9K7eQ2', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

-- 2. Seed User Roles
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE email = 'admin@subscribemaster.local'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role)
SELECT id, 'USER' FROM users WHERE email = 'admin@subscribemaster.local'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role)
SELECT id, 'USER' FROM users WHERE email = 'hteg9188@gmail.com'
ON CONFLICT DO NOTHING;

-- 3. Seed Exchange Rates
INSERT INTO exchange_rates (currency, rate_to_uzs, rate_date, created_at, updated_at)
VALUES ('USD', 12850.000000, CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (currency, rate_date) DO NOTHING;
INSERT INTO exchange_rates (currency, rate_to_uzs, rate_date, created_at, updated_at)
VALUES ('EUR', 13967.500000, CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (currency, rate_date) DO NOTHING;

-- 4. Seed 200 Subscriptions for hteg9188@gmail.com
DO $$
DECLARE
    target_user_id BIGINT;
BEGIN
    SELECT id INTO target_user_id FROM users WHERE email = 'hteg9188@gmail.com';
    IF target_user_id IS NOT NULL THEN
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Netflix Premium', 22.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-18 month', CURRENT_DATE + INTERVAL '5 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Spotify Individual', 11.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '12 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'YouTube Premium Family', 22.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '8 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Disney+ Standard', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '16 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'HBO Max Standard', 15.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '20 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Apple TV+', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '22 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Amazon Prime Video', 8.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '14 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Hulu No Ads', 17.99, 'USD', 'MONTHLY', 'PAUSED', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '18 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Paramount+ with Showtime', 11.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '25 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Crunchyroll Mega Fan', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '3 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Tidal HiFi Plus', 19.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '27 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Deezer Premium', 10.99, 'EUR', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '9 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Twitch Turbo', 11.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '2 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'PlayStation Plus Extra', 134.99, 'USD', 'ANNUAL', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '120 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Xbox Game Pass Ultimate', 19.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-16 month', CURRENT_DATE + INTERVAL '7 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Nintendo Switch Online', 19.99, 'USD', 'ANNUAL', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-20 month', CURRENT_DATE + INTERVAL '160 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Audible Premium Plus', 14.95, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-17 month', CURRENT_DATE + INTERVAL '11 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Peacock Premium Plus', 11.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-3 month', CURRENT_DATE + INTERVAL '29 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Discovery+ Ad-Free', 8.99, 'USD', 'MONTHLY', 'CANCELLED', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '30 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'SoundCloud Go+', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '15 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'MUBI Cinema Club', 14.99, 'EUR', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '19 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Curiosity Stream', 39.99, 'USD', 'ANNUAL', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '210 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'ESPN+ Monthly', 10.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '4 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'DAZN Standard', 29.99, 'EUR', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '26 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'NBA League Pass', 14.99, 'USD', 'MONTHLY', 'PAUSED', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '24 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Criterion Channel', 10.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '13 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Shudder Monthly', 6.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '21 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'BritBox Annual', 89.99, 'USD', 'ANNUAL', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '280 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Rakuten Viki Pass Plus', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-3 month', CURRENT_DATE + INTERVAL '17 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Yandex Plus Uzbekistan', 24999.00, 'UZS', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '6 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Kinopoisk HD Max', 39999.00, 'UZS', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '10 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Allplay TV Premium', 45000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '23 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'iTV Uzbekistan VIP', 50000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '1 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Apple Music Family', 16.99, 'USD', 'MONTHLY', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-18 month', CURRENT_DATE + INTERVAL '12 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Qobuz Studio Sublime', 179.99, 'EUR', 'ANNUAL', 'ACTIVE', 'ENTERTAINMENT', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '190 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Notion Plus Plan', 10.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '15 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Slack Pro Workspace', 8.75, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-20 month', CURRENT_DATE + INTERVAL '10 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Trello Standard', 5.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-16 month', CURRENT_DATE + INTERVAL '25 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Asana Starter', 10.99, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '8 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Monday.com Standard', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '14 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Linear Standard Team', 8.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '21 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Jira Cloud Standard', 7.75, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '28 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Zoom Pro Plan', 15.99, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-22 month', CURRENT_DATE + INTERVAL '5 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Google Workspace Business', 14.40, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-18 month', CURRENT_DATE + INTERVAL '1 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Microsoft 365 Personal', 69.99, 'USD', 'ANNUAL', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '240 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Loom Business', 12.50, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '11 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Miro Starter', 8.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '17 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Figma Professional', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '4 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Canva Pro Individual', 12.99, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '20 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Evernote Personal', 14.99, 'EUR', 'MONTHLY', 'PAUSED', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-24 month', CURRENT_DATE + INTERVAL '16 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Todoist Pro', 4.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '9 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Obsidian Sync', 8.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '23 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, '1Password Individual', 35.88, 'USD', 'ANNUAL', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-19 month', CURRENT_DATE + INTERVAL '150 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Bitwarden Premium', 10.00, 'USD', 'ANNUAL', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-25 month', CURRENT_DATE + INTERVAL '300 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Dropbox Plus 2TB', 11.99, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-17 month', CURRENT_DATE + INTERVAL '13 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Box Personal Pro', 10.00, 'EUR', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '27 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'ClickUp Unlimited', 7.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '6 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Basecamp Standard', 15.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '18 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Airtable Team Plan', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '22 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Coda Pro Maker', 10.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '30 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Craft Docs Pro', 5.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '2 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Bear Pro Notes', 14.99, 'USD', 'ANNUAL', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '110 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Raycast Pro Developer', 8.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '12 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Superhuman Email', 30.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '19 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Grammarly Business', 15.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '26 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Otter.ai Pro Transcription', 10.00, 'USD', 'MONTHLY', 'CANCELLED', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '29 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Notion AI Addon', 10.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '15 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Mailchimp Essentials', 13.00, 'USD', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '7 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Zapier Professional', 29.99, 'USD', 'MONTHLY', 'PAUSED', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '16 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Make.com Pro', 16.00, 'EUR', 'MONTHLY', 'ACTIVE', 'PRODUCTIVITY', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '24 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'ChatGPT Plus', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '9 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'GitHub Copilot Individual', 10.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-16 month', CURRENT_DATE + INTERVAL '5 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Claude Pro Anthropic', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '14 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Midjourney Standard Plan', 30.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '18 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Cursor Pro IDE', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '11 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Perplexity Pro', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '22 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Jasper AI Creator', 39.00, 'USD', 'MONTHLY', 'PAUSED', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '25 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Runway Gen-2 Standard', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '17 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'ElevenLabs Starter', 5.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '3 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Poe by Quora Subscription', 19.99, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '26 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'DeepL Pro Starter', 7.49, 'EUR', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '8 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Grammarly Premium AI', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-17 month', CURRENT_DATE + INTERVAL '20 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Tabnine Pro AI Assistant', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '13 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Phind Pro Assistant', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '27 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Replit Core Cloud', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '2 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'v0.dev Premium Vercel', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-3 month', CURRENT_DATE + INTERVAL '16 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Lovable Scale Plan', 25.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-2 month', CURRENT_DATE + INTERVAL '23 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Bolt.new Pro WebContainer', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-2 month', CURRENT_DATE + INTERVAL '28 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'OpenAI API Tier 1', 50.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '1 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Anthropic API Tier 1', 50.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '1 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Hugging Face Pro', 9.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '19 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Krea AI Pro', 30.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '24 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Leonardo AI Apprentice', 10.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '7 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Mistral Le Chat Pro', 15.00, 'EUR', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-3 month', CURRENT_DATE + INTERVAL '30 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Synthesia Starter Video', 22.00, 'USD', 'MONTHLY', 'CANCELLED', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '15 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Descript Creator Plan', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '10 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Beautiful.ai Pro Presentation', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '21 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Writesonic Small Team', 19.00, 'USD', 'MONTHLY', 'PAUSED', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '4 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Copy.ai Pro Workspace', 36.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '29 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'HeyGen Creator Plan', 29.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '12 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'PlayHT Professional', 31.20, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '18 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Suno AI Pro Plan', 8.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '6 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Udio Standard Music', 10.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-3 month', CURRENT_DATE + INTERVAL '22 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Luma Dream Machine Pro', 29.99, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-2 month', CURRENT_DATE + INTERVAL '25 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Meshy 3D AI Pro', 16.00, 'USD', 'MONTHLY', 'ACTIVE', 'AI_TOOLS', CURRENT_DATE + INTERVAL '-3 month', CURRENT_DATE + INTERVAL '14 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Coursera Plus Annual', 399.00, 'USD', 'ANNUAL', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '180 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Udemy Business Personal', 16.58, 'EUR', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '12 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Duolingo Super Family', 119.99, 'USD', 'ANNUAL', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-18 month', CURRENT_DATE + INTERVAL '90 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'DataCamp Premium Learner', 25.00, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '19 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'LeetCode Premium Annual', 159.00, 'USD', 'ANNUAL', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '230 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Brilliant.org Premium', 13.49, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '24 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Skillshare Annual', 168.00, 'USD', 'ANNUAL', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-16 month', CURRENT_DATE + INTERVAL '140 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'MasterClass Annual Solo', 120.00, 'USD', 'ANNUAL', 'PAUSED', 'EDUCATION', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '270 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Pluralsight Standard Skills', 29.00, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '8 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Codecademy Pro', 17.49, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '22 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Medium Membership', 5.00, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-21 month', CURRENT_DATE + INTERVAL '15 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Frontend Masters Monthly', 39.00, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '6 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Educative.io Unlimited', 16.66, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '28 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Babbel Standard Russian', 12.99, 'EUR', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '14 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Rosetta Stone Unlimited', 11.99, 'EUR', 'MONTHLY', 'PAUSED', 'EDUCATION', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '30 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'LinkedIn Learning', 19.99, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-17 month', CURRENT_DATE + INTERVAL '2 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Chess.com Diamond', 99.99, 'USD', 'ANNUAL', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-20 month', CURRENT_DATE + INTERVAL '115 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'CodeCrafters Membership', 40.00, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '17 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'O''Reilly Learning Platform', 49.00, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '23 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Blinkist Premium Annual', 99.99, 'EUR', 'ANNUAL', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '310 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Headway App Growth', 14.99, 'USD', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-3 month', CURRENT_DATE + INTERVAL '11 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Shortform Book Summaries', 16.00, 'USD', 'MONTHLY', 'CANCELLED', 'EDUCATION', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '26 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'ELSA Speak Pro English', 65.00, 'USD', 'ANNUAL', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '200 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Najot Ta''lim Online Pro', 350000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '5 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Mohirdev Pro Subscription', 150000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'EDUCATION', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '18 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Strava Summit Pro', 79.99, 'USD', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '80 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'MyFitnessPal Premium', 19.99, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '14 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Headspace Plus Mindfulness', 12.99, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '21 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Calm Premium Meditation', 69.99, 'USD', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-18 month', CURRENT_DATE + INTERVAL '175 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'WHOOP 4.0 Membership', 30.00, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '9 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Apple Fitness+ Individual', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '17 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Fitbod Elite Workout', 12.99, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '25 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Peloton App One', 12.99, 'USD', 'MONTHLY', 'PAUSED', 'HEALTH', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '4 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Noom Weight Program', 70.00, 'USD', 'MONTHLY', 'CANCELLED', 'HEALTH', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '29 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Flo Premium Period Tracker', 49.99, 'USD', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-16 month', CURRENT_DATE + INTERVAL '220 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Sleep Cycle Premium', 39.99, 'USD', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-20 month', CURRENT_DATE + INTERVAL '195 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Oura Ring Horizon Sub', 5.99, 'EUR', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '13 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Daily Burn 365', 19.95, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '28 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Alo Moves Yoga', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '8 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Centr by Chris Hemsworth', 29.99, 'USD', 'MONTHLY', 'PAUSED', 'HEALTH', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '19 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Sweat App Fitness', 19.99, 'EUR', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '2 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Nike Training Club Pass', 14.99, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '23 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Clue Plus Cycle Tracking', 39.99, 'EUR', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '160 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Simple Intermittent Fasting', 17.99, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '7 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Fabulous Daily Routine', 40.00, 'USD', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '290 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Freeletics Training Coach', 24.99, 'EUR', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '16 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Lifesum Premium Nutrition', 49.99, 'EUR', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '135 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Cronometer Gold', 8.99, 'USD', 'MONTHLY', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '27 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Zero Fasting Plus', 69.99, 'USD', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '185 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Waking Up Sam Harris', 139.99, 'USD', 'ANNUAL', 'ACTIVE', 'HEALTH', CURRENT_DATE + INTERVAL '-17 month', CURRENT_DATE + INTERVAL '70 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'TradingView Pro Plus', 29.95, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-16 month', CURRENT_DATE + INTERVAL '11 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'YNAB Budgeting App', 98.99, 'USD', 'ANNUAL', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-22 month', CURRENT_DATE + INTERVAL '125 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'CoinMarketCap Diamond', 15.00, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '26 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Koyfin Basic Analytics', 25.00, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '18 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Seeking Alpha Premium', 239.00, 'USD', 'ANNUAL', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '215 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'PocketGuard Plus', 7.99, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '3 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Copilot Money Mac', 95.00, 'USD', 'ANNUAL', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '190 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Monarch Money Family', 99.99, 'USD', 'ANNUAL', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '260 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Lunch Money Developer', 10.00, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '22 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Morningstar Investor', 249.00, 'USD', 'ANNUAL', 'PAUSED', 'FINANCE', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '140 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Wall Street Journal Digital', 38.99, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-18 month', CURRENT_DATE + INTERVAL '9 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Financial Times Digital', 40.00, 'EUR', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '14 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'The Economist Digital', 29.90, 'EUR', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '28 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Barron''s Digital Access', 19.99, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '20 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Simply Wall St Unlimited', 119.00, 'USD', 'ANNUAL', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '310 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Motley Fool Stock Advisor', 199.00, 'USD', 'ANNUAL', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-17 month', CURRENT_DATE + INTERVAL '180 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Finviz Elite Financials', 39.50, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '7 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'StockRover Premium', 27.99, 'USD', 'MONTHLY', 'PAUSED', 'FINANCE', CURRENT_DATE + INTERVAL '-5 month', CURRENT_DATE + INTERVAL '16 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'TrendSpider Technical', 49.00, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '24 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Benzinga Pro Basic', 79.00, 'USD', 'MONTHLY', 'CANCELLED', 'FINANCE', CURRENT_DATE + INTERVAL '-4 month', CURRENT_DATE + INTERVAL '30 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'QuickBooks Simple Start', 30.00, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-19 month', CURRENT_DATE + INTERVAL '13 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Xero Early Ledger', 15.00, 'USD', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '2 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Kapitalbank VIP Card Sub', 75000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '5 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'TBC Bank Pro Package', 50000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '15 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Payme Plus Service', 25000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'FINANCE', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '25 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Amazon Prime Delivery', 14.99, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-20 month', CURRENT_DATE + INTERVAL '12 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Uber One Membership', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-11 month', CURRENT_DATE + INTERVAL '28 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'NordVPN 2-Year Standard', 83.76, 'USD', 'ANNUAL', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '200 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'ExpressVPN 12 Months', 99.95, 'USD', 'ANNUAL', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-18 month', CURRENT_DATE + INTERVAL '170 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Surfshark VPN One', 47.88, 'EUR', 'ANNUAL', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-9 month', CURRENT_DATE + INTERVAL '290 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'CleanMyMac X License', 39.95, 'USD', 'ANNUAL', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-16 month', CURRENT_DATE + INTERVAL '140 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'JetBrains All Products Pack', 289.00, 'USD', 'ANNUAL', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-24 month', CURRENT_DATE + INTERVAL '250 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'DigitalOcean Basic Droplet', 12.00, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-14 month', CURRENT_DATE + INTERVAL '1 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Vercel Pro Developer', 20.00, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '19 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Cloudflare Pro Plan', 25.00, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '15 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'GitHub Pro Individual', 4.00, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-13 month', CURRENT_DATE + INTERVAL '22 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Proton Unlimited Privacy', 11.99, 'EUR', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-7 month', CURRENT_DATE + INTERVAL '6 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Mullvad VPN Secure', 5.00, 'EUR', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-12 month', CURRENT_DATE + INTERVAL '27 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Backblaze Computer Backup', 99.00, 'USD', 'ANNUAL', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-19 month', CURRENT_DATE + INTERVAL '130 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Setapp Mac Apps Suite', 9.99, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-15 month', CURRENT_DATE + INTERVAL '10 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Google One 2TB Storage', 99.99, 'USD', 'ANNUAL', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-17 month', CURRENT_DATE + INTERVAL '240 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'iCloud+ 200GB Family', 2.99, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-22 month', CURRENT_DATE + INTERVAL '18 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Hetzner Cloud CX22', 4.55, 'EUR', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-6 month', CURRENT_DATE + INTERVAL '29 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Supabase Pro Tier', 25.00, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-8 month', CURRENT_DATE + INTERVAL '8 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date, is_deleted, version, created_at, updated_at)
        VALUES (target_user_id, 'Ucell VIP Internet Tariff', 120000.00, 'UZS', 'MONTHLY', 'ACTIVE', 'OTHER', CURRENT_DATE + INTERVAL '-10 month', CURRENT_DATE + INTERVAL '1 day', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    END IF;
END $$;

-- 5. Seed Historical Payments for first 30 subscriptions
INSERT INTO payment_history (subscription_id, payment_date, original_amount, original_currency, exchange_rate_to_uzs, amount_uzs, created_at, updated_at)
SELECT s.id, CURRENT_DATE - INTERVAL '1 month', s.price, s.currency,
       CASE WHEN s.currency = 'USD' THEN 12850.000000 WHEN s.currency = 'EUR' THEN 13967.500000 ELSE 1.000000 END,
       ROUND(s.price * CASE WHEN s.currency = 'USD' THEN 12850.000000 WHEN s.currency = 'EUR' THEN 13967.500000 ELSE 1.000000 END, 4),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM subscriptions s
WHERE s.user_id = (SELECT id FROM users WHERE email = 'hteg9188@gmail.com')
ORDER BY s.id LIMIT 30;

INSERT INTO payment_history (subscription_id, payment_date, original_amount, original_currency, exchange_rate_to_uzs, amount_uzs, created_at, updated_at)
SELECT s.id, CURRENT_DATE - INTERVAL '2 month', s.price, s.currency,
       CASE WHEN s.currency = 'USD' THEN 12850.000000 WHEN s.currency = 'EUR' THEN 13967.500000 ELSE 1.000000 END,
       ROUND(s.price * CASE WHEN s.currency = 'USD' THEN 12850.000000 WHEN s.currency = 'EUR' THEN 13967.500000 ELSE 1.000000 END, 4),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM subscriptions s
WHERE s.user_id = (SELECT id FROM users WHERE email = 'hteg9188@gmail.com')
ORDER BY s.id LIMIT 30;
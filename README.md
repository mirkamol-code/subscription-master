# Subscription Master

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Google-OAuth2_%2B_Gmail_API-4285F4?logo=google&logoColor=white" alt="Google OAuth2 and Gmail API" />
  <img src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white" alt="Docker" />
  <img src="https://img.shields.io/badge/GitHub_Actions-CI%2FCD-2088FF?logo=githubactions&logoColor=white" alt="GitHub Actions CI/CD" />
</p>

**A backend that finds your subscriptions in Gmail, tracks their status, and reminds you before you are charged.**

Users sign in with Google. Subscription Master reads billing emails (receipts, invoices, cancellations, payment failures) from their own Gmail, groups them into subscriptions with a payment history, keeps each subscription's status up to date, and sends reminders in the app, by email from the user's own Gmail, and as browser push. Amounts are also shown in Uzbek soum (UZS) using Central Bank of Uzbekistan rates.

| Resource | Link |
|---|---|
| Production | [subscriptionmaster.app](https://subscriptionmaster.app) |
| Swagger UI | [subscriptionmaster.app/api/swagger-ui](https://subscriptionmaster.app/api/swagger-ui/index.html) |
| Health | [subscriptionmaster.app/api/actuator/health](https://subscriptionmaster.app/api/actuator/health) |

All endpoints live under the `/api` context path.

## Contents

- [How it works](#how-it-works)
- [API overview](#api-overview)
- [Privacy and security](#privacy-and-security)
- [Environment variables](#environment-variables)
- [Google Cloud Console setup](#google-cloud-console-setup)
- [Run and test locally](#run-and-test-locally)
- [Tech stack](#tech-stack)

## How it works

### Sign in with Google

```text
SPA → GET /api/oauth2/authorization/google
    → Google consent (openid, email, profile, gmail.readonly, gmail.send; offline + consent)
    → GET /api/login/oauth2/code/google               (Spring Security, state kept in an encrypted cookie)
    → user created, or linked by verified email; refresh token stored AES-256-GCM encrypted
    → 302 FRONTEND_BASE_URL/auth/callback?code=…      (single-use, 60 seconds)
SPA → POST /api/auth/exchange {code} → JWT access + refresh token
```

The Google refresh token never leaves the server. Access tokens are refreshed automatically; when Google says the grant was revoked, the Gmail connection becomes `DISCONNECTED` and the API answers `409 GMAIL_RECONNECT_REQUIRED` until the user signs in again. Emails listed in `APP_ADMIN_EMAILS` get the `ADMIN` role on every login (and lose it when removed from the list).

### Gmail sync

- **Full sync** runs right after the first Gmail connection, **incremental sync** runs every 6 hours (Gmail history), and `POST /api/gmail/sync` starts one on demand. Exactly one sync runs per user at a time.
- Billing emails are found with a configurable Gmail query over the last 13 months, plus a list of known senders (Netflix, Spotify, Apple, YouTube, Amazon Prime, Microsoft, Adobe, ChatGPT/OpenAI, Claude, Notion, GitHub, Canva, Dropbox, …) and payment processors (Stripe, Paddle, Google Payments, Google Play, Apple) in `application.yml` under `app.gmail.detection`.
- A billing email counts only when Gmail's own `Authentication-Results` show an SPF, DKIM or DMARC pass for the sender's organization, and never from a personal mailbox (gmail.com, outlook.com, …). Mailing-list threads (`List-Id`), replies and forwards, GitHub-style `[owner/repo]` threads, payment-due reminders, free-plan onboarding and newsletters without a transaction are skipped.
- Receipts sent through a payment processor are matched to the merchant named in the subject or body (for example Claude on a Stripe receipt, Google One on a Google Play receipt). A known merchant's name in the subject of an email from an unrelated sender is rejected.
- Every subscription comes from at least one receipt with an amount, currency and date; its source Gmail messages are listed under `GET /subscriptions/{id}/emails`. Subscriptions cannot be added by hand.
- Each email is classified (receipt, cancellation, payment failure, irrelevant) and parsed in this order: **schema.org JSON-LD → sender-specific parser → heuristics** for amounts, currencies (including `UZS 120 000`, `so'm`, `сум`), billing cycles and dates. Guessed values mark the subscription **Needs review**.
- Promotions and one-off purchases (newsletters, bank offers, ride or shop receipts from unknown senders) are ignored on purpose: a missed subscription is better than a false one.
- Emails from one merchant become one subscription with one payment per receipt. Re-running a sync never duplicates anything; a failing email is retried up to three times without blocking newer mail.
- Hidden subscriptions stay hidden. A deleted detected subscription is only re-created by a receipt newer than the deletion.

### Status rules

| Status | When |
|---|---|
| `ACTIVE` | A receipt arrived, or a newer receipt follows a failure or cancellation. A cancellation with a future access end keeps it active until that day. |
| `BILLING_REQUIRED` | A payment-failure email, or no receipt 5 days (`OVERDUE_GRACE_DAYS`) after the expected payment date. A receipt whose amount can't be read never counts as a payment: if it arrives from 3 days before (`RENEWAL_WINDOW_DAYS_BEFORE`) to 5 days after the due date, the subscription is marked **Needs review** ("Receipt found, amount unreadable.") instead of Billing required, until the user confirms the amount. |
| `CANCELLED` | A cancellation email, the access end date arriving, or the user marking it cancelled. |

The newest evidence always wins, and every change is kept in the status history with its reason and source email, for example "Cancelled on 2 Nov 2026 (from Spotify)". Daily rules run in each user's own time zone.

### Notifications

- **Reminders** for active subscriptions due within the user's days-before window (default 3), sent from 09:00 local time, once per billing cycle and channel.
- **Status notifications** for `BILLING_REQUIRED` (urgent), cancellations, access ending, reactivation, and a deleted subscription that charges again. Old history replayed by the first sync does not raise alerts.
- **Channels:** in-app inbox, email sent from the user's own Gmail to themselves (Gmail API, `gmail.send`), and Web Push with VAPID. Each can be switched off in the settings.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `GET /oauth2/authorization/google`, `POST /auth/exchange`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` |
| Gmail | `GET /me/gmail`, `POST /gmail/sync`, `GET /gmail/sync-status`, `DELETE /me/gmail` (disconnect and delete data) |
| Account | `DELETE /me` (delete the account and everything in it) |
| Subscriptions | `GET /subscriptions` (filters, `?hidden=true`), `GET /subscriptions/all`, `GET /subscriptions/upcoming`, `GET /subscriptions/calendar`, `GET/PUT/DELETE /subscriptions/{id}` |
| Subscription details | `GET /{id}/history` (payments), `GET /{id}/emails`, `POST /{id}/emails/{emailId}/payment` (confirm an unreadable receipt's amount), `GET /{id}/status-history`, `PUT /{id}/status`, `POST /{id}/hide`, `POST /{id}/unhide` |
| Emails | `GET /emails/{id}/attachments/{attachmentId}[?download=true]` (invoice PDF), `GET /emails/{id}/content[?images=true]` (sanitized HTML) |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |
| Settings | `GET/PUT /settings/notifications` (email, in-app, push, daysBefore 1–30, timeZone) |
| Web Push | `GET /push/vapid-public-key`, `POST /push/subscriptions`, `DELETE /push/subscriptions` |
| Statistics and reports | `GET /statistics/summary`, `GET /statistics/monthly-dynamics`, `GET /reports/annual.csv`, `GET /reports/annual.xlsx`, `GET /currencies/rates`, `GET /admin/statistics/service-usage` |

Full request and response schemas are in Swagger UI.

**For the frontend:** render `/emails/{id}/content` in an `<iframe sandbox>` (for example via `srcdoc`). The document carries its own Content-Security-Policy meta tag, but browsers ignore `sandbox` in a meta tag, so the iframe attribute is required. Ask for push permission from the settings page, not on page load.

## Privacy and security

- Only extracted fields (merchant, amount, currency, dates, status) and Gmail IDs are stored. Email bodies and PDF bytes are never stored; they are fetched from Gmail on demand and only for their owner.
- The email view is sanitized (no scripts, forms, event handlers, remote styles or overlays), served with a strict CSP, and blocks remote images unless the user opts in.
- Google refresh tokens are encrypted with AES-256-GCM (`TOKEN_ENCRYPTION_KEY`); the OAuth state cookie uses a separately derived key.
- `DELETE /me/gmail` revokes Google access and hard-deletes all subscriptions, payments, emails, attachments metadata, status history, notifications and push subscriptions. `DELETE /me` also removes the account.
- Gmail calls retry with exponential backoff on rate limits and server errors; sending email is never retried, so a reminder can't arrive twice.

## Environment variables

Copy [`.env.example`](.env.example) to `.env` and fill it in.

| Variable | Required | Purpose |
|---|---|---|
| `GOOGLE_CLIENT_ID` | yes | OAuth 2.0 client ID from Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | yes | OAuth 2.0 client secret |
| `TOKEN_ENCRYPTION_KEY` | yes | 32-byte base64 key for refresh tokens at rest (`openssl rand -base64 32`) |
| `JWT_SECRET` | yes in production | JWT signing secret, at least 32 bytes |
| `GOOGLE_REDIRECT_URI` | no | Defaults to `{baseUrl}/api/login/oauth2/code/google`; set it when behind a proxy |
| `FRONTEND_BASE_URL` | no | Where login redirects with the one-time code; default `http://localhost:5173` |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | no | PostgreSQL connection (local defaults provided) |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | docker-compose | Database container settings |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | no | Web Push keys; push is disabled while empty (`npx web-push generate-vapid-keys`) |
| `VAPID_SUBJECT` | no | Contact for push services, default `mailto:admin@subscriptionmaster.app` |
| `APP_ADMIN_EMAILS` | no | Comma-separated emails that get the ADMIN role |
| `GMAIL_SYNC_CRON` | no | Incremental sync schedule, default every 6 hours |
| `REMINDER_CRON` / `REMINDER_HOUR` | no | Reminder job schedule (hourly) and local hour reminders start (9) |
| `STATUS_RULES_CRON` / `OVERDUE_GRACE_DAYS` | no | Status rules schedule (hourly) and overdue grace (5 days) |
| `RENEWAL_WINDOW_DAYS_BEFORE` | no | Days before the due date an unreadable receipt counts as a renewal (3) |
| `GMAIL_ATTACHMENT_MAX_BYTES` | no | Largest invoice PDF served, default 25 MB |
| `APP_SEED_ENABLED` | no | Demo data for `demo@subscribemaster.local`, local only |
| `SERVER_PORT` | no | HTTP port, default `8080` |
| `HIBERNATE_SHOW_SQL` / `HIBERNATE_FORMAT_SQL` | no | SQL logging for debugging, default off |

## Google Cloud Console setup

1. **Create a project** at [console.cloud.google.com](https://console.cloud.google.com) (top bar → project picker → *New project*).
2. **Enable the Gmail API:** *APIs & Services → Library*, search *Gmail API*, click *Enable*.
3. **Branding:** *Google Auth Platform → Branding*. Set the app name, user support email, developer contact email, and add your domain (for example `subscriptionmaster.app`) under *Authorized domains*.
4. **Audience:** *Google Auth Platform → Audience*. Choose **External**, keep the publishing status **Testing**, and under *Test users* add every Google account that will sign in (up to 100).
5. **Data access (scopes):** *Google Auth Platform → Data Access → Add or remove scopes*, then add:
   - `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`
   - `https://www.googleapis.com/auth/gmail.readonly` (restricted)
   - `https://www.googleapis.com/auth/gmail.send` (sensitive)
6. **OAuth client:** *Google Auth Platform → Clients → Create client*, type **Web application**. Under *Authorized redirect URIs* add:
   - `http://localhost:8080/api/login/oauth2/code/google`
   - `https://subscriptionmaster.app/api/login/oauth2/code/google` (production)
7. **Copy the client ID and secret** into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

> **Testing mode limits.** While the app is in *Testing*, only listed test users can sign in and Google expires their refresh tokens after **7 days**; users then see the reconnect prompt and sign in again. Publishing to everyone requires Google's app verification, and because `gmail.readonly` is a restricted scope, a yearly third-party security assessment (CASA).

## Run and test locally

**Prerequisites:** Java 21, Maven 3.9+, Docker.

1. Create `.env` from `.env.example`, fill in the Google client, then generate the secrets:

   ```bash
   cp .env.example .env
   echo "TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)" >> .env
   echo "JWT_SECRET=$(openssl rand -base64 48)" >> .env
   ```

2. Start PostgreSQL (Docker Compose reads the database credentials from `.env`):

   ```bash
   docker compose -f docker-compose-db-only.yml up -d
   ```

3. Run the app with the variables loaded:

   ```bash
   set -a && source .env && set +a
   mvn spring-boot:run
   ```

4. Sign in: open `http://localhost:8080/api/oauth2/authorization/google` in a browser, approve, and copy the `code` from the address the browser lands on (`…/auth/callback?code=…`; the page itself may not exist without the frontend). Trade it for tokens within 60 seconds:

   ```bash
   curl -s -X POST http://localhost:8080/api/auth/exchange \
        -H 'Content-Type: application/json' -d '{"code":"PASTE_CODE"}'
   ```

5. Try it with the access token:

   ```bash
   TOKEN=paste-access-token
   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/gmail/sync-status
   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/subscriptions
   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/unread-count
   ```

   Or use Swagger UI at `http://localhost:8080/api/swagger-ui.html` with *Authorize → Bearer*.

**Run the tests** (Docker must be running; integration tests use Testcontainers PostgreSQL, WireMock replays recorded Google API responses, and no real Google account is needed):

```bash
mvn clean verify
```

To run the full stack in containers instead, put the same variables in `.env` and run `docker compose --profile local-db up --build -d`. Leave out `--profile local-db` when `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` in `.env` point to an external database such as RDS.

## Tech stack

| Category | Technologies |
|---|---|
| Language and framework | Java 21, Spring Boot 3.5, Spring MVC |
| Security | Spring Security 6, OAuth2 Client (Google OIDC), JJWT, AES-256-GCM |
| Google | Gmail API over `RestClient`, Resilience4j retry with backoff |
| Parsing | jsoup (HTML, schema.org JSON-LD, sanitizing) |
| Notifications | Gmail API send, Web Push (`web-push`, BouncyCastle, VAPID) |
| Database | PostgreSQL 16, Spring Data JPA, Flyway |
| Scheduling | Spring Scheduler, ShedLock |
| Currency | Central Bank of Uzbekistan API, Caffeine cache, circuit breaker |
| Reports | Apache POI |
| API docs | Springdoc OpenAPI / Swagger UI |
| Testing | JUnit 5, AssertJ, Mockito, Testcontainers, WireMock |
| Delivery | Docker, Docker Compose, Jib, Nginx, AWS EC2, GitHub Actions |

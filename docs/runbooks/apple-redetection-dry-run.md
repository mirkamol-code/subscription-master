# Apple re-detection: Gmail dry run (one-off, read-only)

Runs the Apple dry run against production **without deploying the branch or running migrations**.
It reads the legacy `apple` rows with plain SQL on the V10 schema, re-reads those emails from Gmail by
message ID (read-only scope), logs counts, and exits. The live app keeps running untouched.

Run it only after reviewing the Option A counts (`apple-redetection-counts.sql`).

## What the `apple-dry-run` profile switches off

| Setting | Value | Why |
|---|---|---|
| `spring.main.web-application-type` | `none` | No HTTP server; nothing can call it. |
| `spring.flyway.enabled` | `false` | Production stays on its current schema. |
| `spring.jpa.hibernate.ddl-auto` | `none` | No schema validation against the newer entities. |
| `spring.datasource.hikari.read-only` | `true` | Every connection is read-only. |
| `app.scheduling.enabled` | `false` | No sync, reminder, status or digest job runs. |
| `app.exchange-rates.refresh-on-startup` | `false` | No rate refresh writes. |
| `app.redetection.apple.exit-after-dry-run` | `true` | The process exits after logging. |

## 1. Read-only database role (once; review before running)

```sql
CREATE ROLE apple_dry_run LOGIN PASSWORD '<from your secret store>';
ALTER ROLE apple_dry_run SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE subscription_master_db TO apple_dry_run;
GRANT USAGE ON SCHEMA public TO apple_dry_run;
GRANT SELECT ON users, gmail_connections, subscriptions, billing_emails, payment_history, status_changes TO apple_dry_run;
```

Drop it afterwards: `DROP OWNED BY apple_dry_run; DROP ROLE apple_dry_run;`

## 2. Run the one-off container

Build the image from the branch, then run it next to production with the production secrets but the
read-only database user. The live service is not stopped or redeployed.

```bash
docker build -t subscription-master:apple-dry-run .
docker run --rm \
  --env-file /path/to/prod.env \
  -e SPRING_PROFILES_ACTIVE=prod,apple-dry-run \
  -e SPRING_DATASOURCE_USERNAME=apple_dry_run \
  -e SPRING_DATASOURCE_PASSWORD='<read-only password>' \
  subscription-master:apple-dry-run
```

`prod.env` must provide `SPRING_DATASOURCE_URL`, `TOKEN_ENCRYPTION_KEY`, `GOOGLE_CLIENT_ID` and
`GOOGLE_CLIENT_SECRET`; the role variables above override the database user.

## 3. Read the result

The log has one `Apple re-detection dry run:` totals line, then one line per user (user ID, outcome,
counts, predicted services). It never logs email addresses, card details, customer IDs or invoice IDs.

Outcomes: `READY`, `MISSING_EMAILS` (payments stay on "Apple (earlier purchases)"),
`WAITING_FOR_GMAIL` (no Gmail access; nothing fetched).

## 4. Confirm nothing changed

Re-run `apple-redetection-counts.sql`; every count must match the run before. A Google token that was revoked
is reported as `WAITING_FOR_GMAIL` and is **not** written back as disconnected.

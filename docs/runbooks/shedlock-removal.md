# ShedLock removal (V24)

ShedLock was removed because production runs exactly one app container, and every scheduled job already stops
duplicates on its own:

| Job | What stops a duplicate |
|---|---|
| Payment and trial reminders | Unique (user, dedupe key, channel) row claimed before sending |
| Admin payment digest | Same dedupe, key `ADMIN_PAYMENT_DIGEST:<date>` |
| Daily status rules | One transaction per subscription with optimistic locking; status notifications deduplicated |
| Gmail sync | Atomic per-user sync claim; a second run skips that user |
| Exchange-rate refresh | Unique (currency, rate_date) with find-or-save |

`docker-compose.yml` uses a fixed `container_name`, and the deploy stops the old container before starting the new
one, so two app instances never run at once. If the app is ever scaled out, scheduling needs a new design.

`V24__Drop_Shedlock.sql` runs `DROP TABLE IF EXISTS shedlock`. V1, which created the table, is unchanged.

## Rollback to an image that still uses ShedLock

1. Before starting the older image, recreate the table once (read-write):
   ```sql
   CREATE TABLE IF NOT EXISTS shedlock (
       name VARCHAR(64) NOT NULL PRIMARY KEY,
       lock_until TIMESTAMP NOT NULL,
       locked_at TIMESTAMP NOT NULL,
       locked_by VARCHAR(255) NOT NULL);
   ```
2. Deploy the older image, or revert the PR so CI deploys it. Flyway in the older image treats V23/V24 as future
   migrations and ignores them by default. If validation still fails, start it with
   `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:future`.
3. The older image recreates no table itself, so step 1 must come first, or its scheduled jobs fail to acquire locks.

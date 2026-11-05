# Trial re-check (one-off)

Finds free-trial emails from the last 30 days that were missed because they arrived before trial detection existed (for
example an Ancestry trial on 2026-11-04), and records them. It never touches non-trial emails, never creates a duplicate
of a trial that is already tracked, and only counts are logged (no email content).

Nothing starts it automatically: it needs `app.trial-recheck.on-startup=true`, which only the `trial-recheck` profile sets.
No web server, no schedulers, no migrations; the process exits when done.

## 1. Dry run (read-only, safe)

```bash
docker run --rm --env-file .env -e SPRING_PROFILES_ACTIVE=prod,trial-recheck <image>
```
Log line: `Trial re-check (dry run) for N users: found X, would record Y, recorded 0, skipped Z`.
The database connection is read-only. A user whose Google access was revoked is skipped with a warning (the app can't
mark them disconnected on a read-only connection); the normal app still handles them.

## 2. Apply (only after reviewing the dry run, with the owner's approval)

```bash
docker run --rm --env-file .env -e SPRING_PROFILES_ACTIVE=prod,trial-recheck,trial-recheck-apply <image>
```
It first queues each missed trial email in `failed_gmail_messages`, so nothing is lost even if the run stops. Then it deletes the old `IRRELEVANT` row (it holds only the message ID and date) and records the
email again through the normal recorder: an unknown-merchant trial appears as a Free trial marked Needs review. If
recording fails, the message is queued in `failed_gmail_messages` and the next normal sync retries it. Running it again
finds nothing new.

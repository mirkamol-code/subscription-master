# SubScribe Master Backend API

Production-oriented backend for tracking paid subscriptions in UZS, with JWT security, scheduled payment processing, CBU exchange rates, exports, and personal spending statistics.

## Prerequisites and start

Java 21 and Docker are required. Start the application and PostgreSQL together with one Docker Compose command (the image builds the JAR itself):

```bash
JWT_SECRET='a-secure-random-secret-with-at-least-32-bytes' docker compose up --build
```

The API runs at `http://localhost:8080`; interactive OpenAPI documentation is at `http://localhost:8080/swagger-ui.html`.

For local development, start PostgreSQL with `docker compose -f docker-compose-db-only.yml up -d db-local-postgres` and run `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`. The `dev` profile uses non-secret local credentials and a development-only signing key. Production secrets are supplied through environment variables.

## API overview

| Area | Endpoint | Access |
|---|---|---|
| Authentication | `POST /auth/register`, `/auth/login`, `/auth/refresh` | Public |
| Subscriptions | `POST`, `GET /subscriptions`; `GET`, `PUT`, `DELETE /subscriptions/{id}` | Authenticated owner |
| Statistics | `GET /statistics/summary`, `/statistics/monthly-dynamics?months=6` | Authenticated owner |
| Exports | `GET /reports/annual.csv`, `/reports/annual.xlsx` | Authenticated owner |
| Admin | `GET /admin/statistics/service-usage` | ADMIN |

Send the access token as `Authorization: Bearer <accessToken>`. `GET /subscriptions` supports `status`, `currency`, `minPrice`, `maxPrice`, Spring pagination parameters, and sorting.

## Important operational behavior

- Each subscription is scoped to the authenticated user at the service layer; resource IDs alone never grant access.
- Deletion is soft (`is_deleted=true`) and cancellation preserves financial history.
- The payment job records due active subscriptions at 00:05 Asia/Tashkent and advances their next payment date. The reminder job logs payments due in two days at 09:00.
- CBU rates are stored as `1 currency = N UZS`; their CBU `Date` is persisted as `rate_date`, the date the rate becomes effective. The current-cost views use the latest rate, while payment history retains the snapshot rate used at payment time.
- ShedLock makes each scheduled job single-execution across multiple application instances.

## Development seed data and Gmail notifications

The `dev` profile seeds data only when the `users` table is empty: `admin@subscribemaster.local` / `AdminPassword123!` and `hteg9188@gmail.com` / `DemoPassword123!`, plus demo subscriptions. The loader is disabled outside `dev` to prevent production default accounts.

Notifications log by default. To also send Gmail notifications, set `GMAIL_NOTIFICATIONS_ENABLED=true`, `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` (a Google App Password), and `GMAIL_FROM`. Gmail uses STARTTLS on port 587. Log notifications remain enabled unless `LOG_NOTIFICATIONS_ENABLED=false` is set. SMTP is excluded from `/actuator/health` by default because Gmail is optional; set `MAIL_HEALTH_ENABLED=true` if SMTP availability should affect application health.

## Automated tests

Run the complete suite with `mvn test`. It uses JUnit 5, Mockito, WebTestClient, and Testcontainers. Integration tests automatically start a disposable PostgreSQL 17 container, apply the same Flyway migration as production, and remove all test records after each test. Docker must be running; no local PostgreSQL database or external CBU/Gmail access is needed. Unit tests cover isolated business rules, JWT behavior, notification payloads, exports, and exchange-rate fallbacks. PostgreSQL integration tests cover filtering, ownership, validation, JWT protection, role restrictions, reports, and soft deletion.

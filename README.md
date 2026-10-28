# Subscription Master

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.4.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Spring_Security-JWT-6DB33F?logo=springsecurity&logoColor=white" alt="Spring Security and JWT" />
  <img src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white" alt="Docker" />
  <img src="https://img.shields.io/badge/AWS-EC2-232F3E?logo=amazonaws&logoColor=white" alt="AWS EC2" />
  <img src="https://img.shields.io/badge/GitHub_Actions-CI%2FCD-2088FF?logo=githubactions&logoColor=white" alt="GitHub Actions CI/CD" />
</p>

<p align="center">
  <a href="https://subscriptionmaster.app/api/swagger-ui/index.html">
    <img src="https://img.shields.io/badge/Swagger-Explore_API-85EA2D?logo=swagger&logoColor=black" alt="Explore API" />
  </a>
  <a href="https://subscriptionmaster.app/api/actuator/health">
    <img src="https://img.shields.io/badge/Live_API-Production-brightgreen" alt="Live API" />
  </a>
  <a href="https://github.com/mirkamol-code/sub-master">
    <img src="https://img.shields.io/badge/GitHub-Source_Code-181717?logo=github&logoColor=white" alt="Source code" />
  </a>
</p>

**A Java backend API for managing subscriptions, tracking recurring expenses, and understanding spending in Uzbek soum (UZS).**

Subscription Master brings recurring payments for services such as Netflix, Spotify, ChatGPT Plus, Adobe Creative Cloud, JetBrains, and AWS into one place. It helps users manage subscription lifecycles, anticipate upcoming renewals, compare costs across currencies, analyze spending, and export financial reports.

Built with **Java 21, Spring Boot 3, and PostgreSQL**, the project demonstrates backend engineering practices including stateless authentication, data ownership, dynamic querying, external API resilience, scheduled processing, integration testing, and automated deployment.

## Live Demo

| Resource | Link |
|---|---|
| Production website | [subscriptionmaster.app](https://subscriptionmaster.app) |
| Interactive API documentation | [Swagger UI](https://subscriptionmaster.app/api/swagger-ui/index.html) |
| OpenAPI specification | [OpenAPI JSON](https://subscriptionmaster.app/api/v3/api-docs) |
| Health check | [API Health](https://subscriptionmaster.app/api/actuator/health) |
| Source code | [GitHub Repository](https://github.com/mirkamol-code/sub-master) |

**Quick start:** Open Swagger UI, register an account, authorize with the returned access token, and start exploring the API.

> All application endpoints are served under the `/api` context path.

## At a Glance

- **Language:** Java 21
- **Framework:** Spring Boot 3.4.1, Spring Security 6
- **Database:** PostgreSQL 16
- **Authentication:** JWT access tokens and persistent refresh tokens
- **Data access:** Spring Data JPA, Specifications, Criteria API
- **External integration:** Central Bank of Uzbekistan (CBU) exchange rates
- **Resilience and caching:** Resilience4j, Caffeine
- **Scheduling:** Spring Scheduler and ShedLock
- **Testing:** JUnit, integration tests, Testcontainers
- **Deployment:** Docker, Nginx, HTTPS, AWS EC2, GitHub Actions

## What Problem Does It Solve?

Recurring subscriptions can be difficult to manage when they have different billing cycles, currencies, and renewal dates.

Users need to know:

- **How much am I spending?** Understand monthly commitments and estimated annual costs.
- **What is renewing soon?** Identify upcoming payment dates before they arrive.
- **What do foreign-currency subscriptions actually cost?** Compare expenses in UZS using exchange rates from the CBU.
- **What have I paid historically?** Preserve payment records when a subscription is paused, canceled, or removed from active management.

Subscription Master addresses these needs through a centralized subscription API, automated renewal processing, financial analytics, notifications, and exportable reports.

## Core Features

### 1. Authentication and Authorization

Built with Spring Security and JWT-based authentication.

- User registration and login with BCrypt password hashing.
- Short-lived access tokens with a configurable 15-minute lifetime.
- Persistent refresh-token management backed by PostgreSQL.
- Token refresh and logout endpoints.
- Authenticated user profile through `/api/auth/me`.
- Role-based access control for `USER` and `ADMIN`.
- Subscription ownership checks to prevent users from accessing other users’ data.
- Login rate limiting after repeated failed authentication attempts.

The authentication rate limiter uses an in-memory Caffeine store. In a multi-instance deployment, a shared store would be needed to enforce a single rate-limit state across all instances.

### 2. Subscription Lifecycle Management

Users can create, update, search, pause, resume, and delete subscriptions.

**Supported billing frequencies:** weekly, monthly, and annually.

**Supported statuses:** `ACTIVE`, `PAUSED`, and `CANCELLED`.

Subscriptions can be categorized as entertainment, productivity, AI tools, cloud services, utilities, education, gaming, or other.

Additional capabilities include:
- Automatic next-payment date calculation.
- Pause and resume workflows.
- Soft deletion to preserve historical records and relationships.
- Payment history containing original amounts, currencies, converted UZS values, and applied exchange rates.
- Upcoming renewal queries for a configurable number of days.
- Calendar data grouped by payment date, with daily and monthly expense totals.
- Unpaged subscription retrieval for calendar and other frontend use cases.

### 3. Advanced Search, Filtering, and Pagination

The API supports combining multiple filters in a single request.

| Filter | Example |
|---|---|
| Status | `status=ACTIVE` |
| Currency | `currency=USD` |
| Category | `category=AI_TOOLS` |
| Name search | `name=net` |
| Payment-date range | `nextPaymentDateFrom=2026-10-01&nextPaymentDateTo=2026-10-31` |
| Minimum price | `minPrice=100000` |
| Maximum price | `maxPrice=500000` |

Name and category searches are case-insensitive. Price-range filtering normalizes supported currencies to UZS so that subscriptions in different currencies can be compared.

Dynamic queries use Spring Data JPA `Specification` and the Criteria API rather than native SQL.

```http
GET /api/subscriptions?page=0&size=10&sort=nextPaymentDate,asc
```

An unpaged option is also available for use cases that require the complete filtered collection.

### 4. Currency Conversion and External API Resilience

The application integrates with the [Central Bank of Uzbekistan's exchange-rate API](https://cbu.uz/uz/arkhiv-kursov-valyut/json/).

Key implementation details:

- Converts USD and EUR subscription expenses to UZS.
- Caches exchange rates with Caffeine for 24 hours.
- Stores exchange-rate records in PostgreSQL.
- Uses Resilience4j Circuit Breaker to handle external service failures.
- Falls back to the most recently persisted rate when available.
- Defines configurable fallback values for cold-start scenarios.
- Exposes exchange rates and their update timestamp through `/api/currencies/rates`.

This design reduces repeated external requests and allows the application to continue operating during temporary CBU API outages.

**Important:** Fallback rates are a last-resort availability mechanism, not a guarantee of current market rates. Their freshness and configuration should be considered when interpreting financial calculations.

### 5. Scheduled Jobs and Payment Processing

Scheduled tasks automate renewal reminders, simulated payment processing, and currency-rate updates.

| Schedule | Task |
|---|---|
| 09:00 daily | Find subscriptions due in two days and trigger reminders |
| 00:05 daily | Process overdue subscriptions, record simulated payments, and advance billing dates |
| 00:10 daily | Refresh and persist currency rates |

All schedules use the `Asia/Tashkent` timezone.

ShedLock coordinates scheduled execution through PostgreSQL, helping prevent multiple application instances from running the same scheduled job simultaneously.

**Payment processing is simulated.** The application records payment-history entries; it does not charge real bank cards.

### 6. Notifications

The notification layer uses the Strategy Pattern behind a `NotificationService` interface.

- `LogNotificationService` — records reminder messages through structured logging.
- `GmailNotifier` — sends email reminders through Gmail SMTP using JavaMailSender and TLS.

This abstraction allows additional notification channels to be introduced without coupling subscription-processing logic to a specific delivery mechanism.

### 7. Analytics and Financial Reports

The reporting API provides information that users can use to understand recurring expenses.

**Spending analytics**
- Estimated monthly spending in UZS.
- Most expensive subscription.
- Expense breakdown by category.
- Monthly spending trends over the previous 6 or 12 months.

**Export formats**
- CSV reports generated using Java file and stream handling.
- Excel reports generated with Apache POI, including styled headers, auto-sized columns, and total formulas.

The API also provides an admin-only service-usage ranking across the platform.

## API Overview

All endpoint URLs below include the `/api` context path.

### Authentication

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Register a user |
| `POST` | `/api/auth/login` | Authenticate and obtain tokens |
| `POST` | `/api/auth/refresh` | Obtain a new access token |
| `POST` | `/api/auth/logout` | Revoke a refresh token |
| `GET` | `/api/auth/me` | Retrieve the current user profile |

### Subscriptions

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/subscriptions` | Create a subscription |
| `GET` | `/api/subscriptions` | List subscriptions with filters and pagination |
| `GET` | `/api/subscriptions/all` | Retrieve filtered subscriptions without pagination |
| `GET` | `/api/subscriptions/{id}` | Retrieve subscription details |
| `PUT` | `/api/subscriptions/{id}` | Update a subscription |
| `DELETE` | `/api/subscriptions/{id}` | Soft-delete a subscription |
| `POST` | `/api/subscriptions/{id}/pause` | Pause a subscription |
| `POST` | `/api/subscriptions/{id}/resume` | Resume a subscription |
| `GET` | `/api/subscriptions/{id}/history` | Retrieve payment history |
| `GET` | `/api/subscriptions/upcoming` | Find upcoming renewals |
| `GET` | `/api/subscriptions/calendar` | Retrieve monthly calendar data |

### Statistics, Currency, Reports, and Administration

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/statistics/summary` | Spending summary and category breakdown |
| `GET` | `/api/statistics/monthly-dynamics` | Monthly spending trends |
| `GET` | `/api/currencies/rates` | Current exchange rates |
| `GET` | `/api/reports/annual.csv` | Export an annual CSV report |
| `GET` | `/api/reports/annual.xlsx` | Export an annual Excel report |
| `GET` | `/api/admin/statistics/service-usage` | Platform-wide service usage statistics; admin only |

For request schemas, response examples, and interactive testing, use [Swagger UI](https://subscriptionmaster.app/api/swagger-ui/index.html).

## Engineering Decisions

The project emphasizes maintainability, data integrity, and clear separation of responsibilities.

| Area | Implementation |
|---|---|
| Dependency management | Constructor injection instead of field injection |
| DTOs | Java records where appropriate |
| Object mapping | MapStruct |
| Database evolution | Versioned Flyway migrations |
| Query composition | JPA Specifications and Criteria API |
| Entity fetching | Lazy relationships to limit unnecessary data loading |
| Concurrent updates | Optimistic locking using `@Version` |
| Auditing | Creation and modification timestamps through Spring Data JPA auditing |
| Transactions | Read-only transactions for query operations and transactional writes |
| Error handling | Centralized exception handling with consistent JSON responses |
| Scheduling | ShedLock-backed coordination through PostgreSQL |
| External services | Caching, circuit breaking, and persisted fallback data |
| Deployment | Docker-based deployment with automated GitHub Actions workflows |

The implementation deliberately avoids Lombok and field injection, favoring explicit Java code and constructor-based dependency management.

## Architecture

At a high level, the application separates API handling, business logic, persistence, and external integrations.

```text
API Client / Frontend
        |
        v
   Nginx + HTTPS
        |
        v
Spring Boot REST API
        |
        +---- Spring Security / JWT
        |
        +---- Subscription & Payment Services
        |
        +---- Statistics & Report Services
        |
        +---- Currency Rate Integration
        |
        v
 Spring Data JPA
        |
        v
    PostgreSQL
        |
        +---- Subscription and user data
        +---- Payment history and exchange rates
        +---- ShedLock coordination
```

Scheduled processing and email notifications complement the request-response API. Caffeine provides application-level caching, while PostgreSQL stores durable business data and coordination records.

## Technology Stack

| Category | Technologies |
|---|---|
| Language and framework | Java 21, Spring Boot 3.4.1 |
| Web and security | Spring MVC, Spring Security 6, JJWT 0.12.6, BCrypt |
| Database | PostgreSQL 16, Spring Data JPA, Hibernate |
| Migrations | Flyway |
| Mapping | MapStruct 1.6.3 |
| Caching | Spring Cache, Caffeine |
| Resilience | Resilience4j Circuit Breaker |
| Scheduling | Spring Scheduler, ShedLock 6.2.0 |
| Financial exports | Apache POI 5.3.0 |
| API documentation | Springdoc OpenAPI / Swagger UI 2.8.3 |
| Testing | JUnit, Testcontainers, PostgreSQL integration tests |
| Containerization | Docker, Docker Compose, Jib |
| Deployment | Nginx, TLS, AWS EC2, GitHub Actions |

## Getting Started

### Option 1: Try the Live API

1. Open [Swagger UI](https://subscriptionmaster.app/api/swagger-ui/index.html).
2. Execute `POST /auth/register` to create an account.
3. Copy the `accessToken` from the response.
4. Select **Authorize** in Swagger UI and enter the token in the format required by the displayed security scheme.
5. Create and manage subscriptions, then explore statistics, renewals, and reports.

Example registration request:

```json
{
  "username": "developer",
  "email": "dev@example.com",
  "password": "Password123!"
}
```

Example subscription request:

```json
{
  "name": "ChatGPT Plus",
  "price": 20.00,
  "currency": "USD",
  "frequency": "MONTHLY",
  "status": "ACTIVE",
  "category": "AI_TOOLS",
  "startDate": "2026-10-01"
}
```

Check the schemas in Swagger UI if the API's required fields differ from these examples.

### Option 2: Run with Docker Compose

**Prerequisites:** Docker 24 or later and Docker Compose v2.

Clone the repository:

```bash
git clone https://github.com/mirkamol-code/sub-master.git
cd sub-master
```

Create a `.env` file in the project root:

```env
POSTGRES_DB=subscription_master_db
POSTGRES_USER=mirkamol
POSTGRES_PASSWORD=change-this-local-password
JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
APP_SEED_ENABLED=true
```

These are local development examples. Use strong, unique credentials and externally managed secrets for deployment.

Start the application and its configured services:

```bash
docker compose up --build -d
```

Verify the application:

- Health check: `http://localhost:8080/api/actuator/health`
- Swagger UI: `http://localhost:8080/api/swagger-ui/index.html`
- PostgreSQL: `localhost:5222` if using the supplied port mapping.

Check the Docker Compose configuration for the actual port mappings and required environment variables.

### Option 3: Run for Development

Start PostgreSQL with the database-only Compose configuration:

```bash
docker compose -f docker-compose-db-only.yml up -d
```

Run Spring Boot using Maven:

```bash
./mvnw spring-boot:run
```

Alternatively, run `SubscribeMasterApplication.java` from IntelliJ IDEA.

Run the test suite:

```bash
./mvnw clean verify
```

The integration tests use Testcontainers to verify behavior against PostgreSQL rather than relying exclusively on an in-memory database.

## Configuration

Important application properties include:

| Property | Purpose |
|---|---|
| `server.servlet.context-path` | Application URL prefix, configured as `/api` |
| `spring.datasource.url` | PostgreSQL connection URL |
| `app.jwt.secret` | JWT signing secret |
| `app.jwt.access-token-minutes` | Access-token lifetime |
| `app.jwt.refresh-token-days` | Refresh-token lifetime |
| `app.rate-limiting.auth.max-failed-attempts` | Failed login attempts before blocking |
| `app.rate-limiting.auth.block-duration-seconds` | Rate-limit block duration |
| `app.seed.enabled` | Controls demo-data initialization |
| `app.cbu.base-url` | CBU exchange-rate API URL |
| `app.cbu.connect-timeout` | Max time to connect to CBU (default `2s`) |
| `app.cbu.read-timeout` | Max time to wait for a CBU response (default `3s`) |

Seed data can create 200 demonstration subscriptions. Keep it disabled in production unless intentionally required. Production secrets, SMTP credentials, and database credentials should be supplied securely rather than committed to source control.

## Testing

The project contains **25 test classes** across unit and integration testing.

**Unit tests** cover services responsible for authentication, subscription management, exchange rates, payments, notifications, statistics, reporting, and rate limiting.

**Integration tests** cover authentication, subscriptions, currencies, reports, statistics, and admin endpoints. They exercise the API with PostgreSQL through Testcontainers and verify areas such as security filters, JWT validation, database behavior, and HTTP responses.

Run all tests with:

```bash
./mvnw clean verify
```

## Deployment

The backend is deployed to AWS EC2 with Docker-based infrastructure, Nginx handling HTTPS traffic, and GitHub Actions automating deployment.

The deployment setup is designed to keep application configuration outside the codebase and provide health-check endpoints for operational visibility.

## Project Scope

Subscription Master is a portfolio project built to demonstrate backend engineering practices using a practical financial-management use case.

Its current scope includes subscription management, simulated payments, exchange-rate conversion, notifications, analytics, and report exports. It is not a payment processor and does not directly charge bank cards.

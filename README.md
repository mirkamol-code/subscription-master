# Subscription Master

A production-oriented **subscription management backend API** built with **Java 21 and Spring Boot 3**.

Subscription Master helps users manage recurring paid subscriptions such as Netflix, Spotify, ChatGPT, Adobe, YouTube Premium, and similar services in one place.

The system tracks subscription costs and renewal dates, automatically calculates the next payment date, converts expenses to **UZS** using official **Central Bank of Uzbekistan (CBU)** exchange rates, sends upcoming-payment reminders, generates statistics, and exports annual spending reports.

The project was developed as a **Junior / Junior+ Backend Developer Take-Home Assignment** with emphasis on clean architecture, security, database consistency, external API integration, scheduling, testing, and production deployment.

---

## 🌐 Live Application

### Production API

https://subscriptionmaster.app

### Swagger UI

https://subscriptionmaster.app/swagger-ui/index.html

### OpenAPI

https://subscriptionmaster.app/v3/api-docs

### Health Check

https://subscriptionmaster.app/actuator/health

> The production application is deployed on **AWS EC2** using **Docker, PostgreSQL, Nginx, and HTTPS**.

---

# 🎯 Project Objective

Users often subscribe to multiple paid services and lose visibility into:

* how much they spend every month;
* when the next payment will occur;
* how much subscriptions cost in a single base currency;
* how exchange-rate changes affect their expenses;
* which services consume the most money.

Subscription Master solves this by consolidating subscriptions into one backend system and providing:

* secure user authentication;
* user-isolated subscription management;
* automated recurring-payment calculations;
* CBU currency conversion;
* payment reminders;
* spending statistics;
* annual CSV/XLSX reports.

---

# ✨ Features

## 🔐 Authentication & Authorization

The API uses **Spring Security + JWT**.

Implemented security features include:

* User registration
* User login
* JWT access tokens
* Refresh tokens
* BCrypt password hashing
* Stateless authentication
* Permission-based authorization
* User-level resource ownership verification
* USER and ADMIN access levels

A user cannot access another user's subscriptions simply by knowing their IDs.

Authorization is verified in the application layer in addition to authentication.

---

## 💳 Subscription Management

Users can:

* Create subscriptions
* View their subscriptions
* View a subscription by ID
* Update subscriptions
* Delete subscriptions
* Filter subscriptions
* Sort subscriptions
* Use pagination

A subscription contains information such as:

* Name
* Price
* Currency
* Category
* Start date
* Billing frequency
* Status
* Next payment date

### Supported currencies

```text
USD
UZS
EUR
```

### Billing frequencies

```text
WEEKLY
MONTHLY
ANNUAL
```

The application automatically calculates `nextPaymentDate` based on the subscription's billing frequency.

### Subscription statuses

```text
ACTIVE
PAUSED
CANCELLED
```

---

## 🗑 Soft Delete

Subscriptions are not physically removed from the database.

Instead:

```text
is_deleted = true
```

This preserves historical financial information such as previous payments and reports.

Deleted subscriptions are excluded from normal user queries.

---

## 📄 Pagination & Filtering

The subscription API supports Spring `Pageable` and dynamic filtering.

Filtering is implemented using the **JPA Specification API**, rather than Native SQL.

Supported filters include:

```text
status
currency
minPrice
maxPrice
page
size
sort
```

Example:

```http
GET /subscriptions?status=ACTIVE&currency=USD&minPrice=5&maxPrice=100&page=0&size=20&sort=nextPaymentDate,asc
```

---

## 💰 Payment History

When a subscription payment becomes due, the application records the payment in a separate `PaymentHistory` table.

Payment history preserves:

* subscription information;
* payment amount;
* currency;
* exchange-rate snapshot;
* payment date.

This means historical financial information remains available even if the subscription is later cancelled or deleted.

---

# 💱 Currency Conversion

Subscription Master integrates with the official **Central Bank of Uzbekistan** exchange-rate API.

The application converts subscription expenses into the base currency:

```text
UZS
```

The system stores rates using the interpretation:

```text
1 foreign currency = N UZS
```

For example:

```text
1 USD = 12,500 UZS
```

---

## ⚡ Exchange Rate Caching

Exchange rates are cached using:

```text
Spring Cache
Caffeine
```

Rates are refreshed periodically instead of calling the CBU API for every request.

This reduces:

* unnecessary network traffic;
* latency;
* external API load.

---

## 🛡 External API Resilience

CBU API communication is protected using **Resilience4j**.

The integration includes resilience mechanisms such as:

* Circuit Breaker
* Retry
* Timeout handling
* Fallback behavior

If the CBU service is temporarily unavailable, the system can use the last available cached exchange rate instead of failing the complete request.

---

# ⏰ Scheduled Jobs

The application performs recurring background tasks using Spring Scheduling.

## Payment Processing

A scheduled job processes subscriptions whose payments are due.

When a payment becomes due, the system:

1. Records a `PaymentHistory` entry
2. Stores the exchange rate used for the payment
3. Advances `nextPaymentDate`
4. Keeps the subscription active for the next billing period

The payment-processing job currently runs at:

```text
00:05 Asia/Tashkent
```

---

## Payment Reminders

Every day at:

```text
09:00 Asia/Tashkent
```

the application checks whether users have payments due in **2 days**.

If a payment is approaching, a notification is generated.

---

## 🔒 ShedLock

Scheduled jobs are protected with **ShedLock**.

This prevents the same job from executing multiple times if the application is deployed across multiple instances.

Example:

```text
Instance A ─┐
            ├── Database Lock ──> Scheduler executes once
Instance B ─┘
```

---

# 🔔 Notification Strategy

Notifications use a strategy-based design.

Available notification mechanisms include:

```text
Log Notification
Email Notification
```

This allows notification channels to be changed without modifying the main scheduling logic.

By default, reminders can simply be written to the application log.

Optional Gmail SMTP support is also available.

---

# 📧 Gmail Notifications

To enable Gmail notifications, configure:

```bash
GMAIL_NOTIFICATIONS_ENABLED=true
GMAIL_USERNAME=your-email@gmail.com
GMAIL_APP_PASSWORD=your-google-app-password
GMAIL_FROM=your-email@gmail.com
```

Google requires an **App Password** when two-step verification is enabled.

The application uses:

```text
SMTP Host: smtp.gmail.com
Port: 587
Security: STARTTLS
```

Log notifications can be controlled with:

```bash
LOG_NOTIFICATIONS_ENABLED=true
```

---

# 📊 Statistics API

The application provides personal spending statistics.

Users can retrieve:

* Total subscription spending
* Monthly subscription costs
* Most expensive subscription
* Spending converted into UZS
* Monthly spending dynamics

Monthly dynamics can be requested for a number of previous months.

For example:

```http
GET /statistics/monthly-dynamics?months=12
```

The response is designed to be usable by a frontend chart or dashboard.

---

# 👑 Admin Statistics

Administrators can access system-wide statistics.

For example:

```http
GET /admin/statistics/service-usage
```

This allows analysis of subscription-service usage across users.

Admin endpoints require elevated authorization permissions.

---

# 📁 Report Export

Users can export annual subscription-cost analysis.

Supported formats:

```text
CSV
XLSX
```

The Excel report is generated using **Apache POI**.

Reports contain financial information such as:

* Subscription name
* Monthly equivalent price
* Base-currency equivalent
* Annual subscription cost

Available endpoints:

```http
GET /reports/annual.csv
GET /reports/annual.xlsx
```

---

# 🏗 Architecture

The project follows a layered Spring Boot architecture:

```text
Client
   │
   ▼
Controller
   │
   ▼
Service
   │
   ▼
Repository
   │
   ▼
PostgreSQL
```

Business logic is kept out of controllers.

A simplified project structure:

```text
src/main/java
│
├── config
├── controller
├── domain
│   ├── entity
│   └── enum
├── dto
│   ├── request
│   └── response
├── exception
├── integration
├── mapper
├── repository
│   └── specification
├── scheduler
├── security
└── service
```

---

# 🧠 Architectural Decisions

## Layered Architecture

The application separates responsibilities between:

```text
Controller → Service → Repository → Entity
```

Controllers handle HTTP requests.

Services contain business logic.

Repositories handle persistence.

---

## DTO Mapping

Entities are not directly exposed through the REST API.

DTO ↔ Entity mapping is handled using:

```text
MapStruct
```

---

## Constructor Injection

The project avoids field injection.

Dependencies are provided through constructor injection.

---

## Lazy Relationships

Entity relationships use lazy loading where appropriate to avoid unnecessary database queries and reduce the risk of N+1 problems.

---

## Database Migrations

Database schema changes are managed using:

```text
Flyway
```

Flyway runs migrations automatically when the application starts.

---

## Transactions

Money-related database operations use Spring transactions so that related operations succeed or fail as one unit.

This prevents partially saved financial state.

---

## Optimistic Locking

Optimistic locking is used where appropriate to reduce the risk of concurrent requests overwriting each other's data.

---

## Global Exception Handling

The application uses centralized exception handling with:

```text
@RestControllerAdvice
```

Instead of exposing Java stack traces, the API returns structured JSON errors.

---

# 🛠 Tech Stack

| Area                       | Technology                  |
| -------------------------- | --------------------------- |
| Language                   | Java 21                     |
| Framework                  | Spring Boot 3.5.5           |
| REST                       | Spring MVC                  |
| Persistence                | Spring Data JPA / Hibernate |
| Database                   | PostgreSQL                  |
| Database Migration         | Flyway                      |
| Security                   | Spring Security             |
| Authentication             | JWT / JJWT                  |
| Password Hashing           | BCrypt                      |
| Mapping                    | MapStruct                   |
| Validation                 | Jakarta Validation          |
| Currency API               | Central Bank of Uzbekistan  |
| Cache                      | Spring Cache + Caffeine     |
| Resilience                 | Resilience4j                |
| Scheduling                 | Spring Scheduling           |
| Distributed Scheduler Lock | ShedLock                    |
| Email                      | Spring Mail                 |
| Excel Export               | Apache POI                  |
| API Docs                   | Springdoc OpenAPI / Swagger |
| Monitoring                 | Spring Boot Actuator        |
| Testing                    | JUnit 5                     |
| Mocking                    | Mockito                     |
| Assertions                 | AssertJ                     |
| Integration Testing        | Testcontainers              |
| API Testing                | WebTestClient               |
| Containerization           | Docker / Docker Compose     |
| Deployment                 | AWS EC2                     |
| Reverse Proxy              | Nginx                       |

---

# 🚀 API Endpoints

Production base URL:

```text
https://subscriptionmaster.app
```

Protected endpoints require:

```http
Authorization: Bearer <access-token>
```

---

## Authentication Controller

| Method | Endpoint         | Description                  | Access |
| ------ | ---------------- | ---------------------------- | ------ |
| `POST` | `/auth/register` | Register a new user          | Public |
| `POST` | `/auth/login`    | Login and obtain JWT tokens  | Public |
| `POST` | `/auth/refresh`  | Refresh expired access token | Public |

---

## Subscription Controller

| Method   | Endpoint              | Description               | Access        |
| -------- | --------------------- | ------------------------- | ------------- |
| `POST`   | `/subscriptions`      | Create subscription       | Authenticated |
| `GET`    | `/subscriptions`      | List/filter subscriptions | Authenticated |
| `GET`    | `/subscriptions/{id}` | Get subscription by ID    | Owner / Admin |
| `PUT`    | `/subscriptions/{id}` | Update subscription       | Owner / Admin |
| `DELETE` | `/subscriptions/{id}` | Soft-delete subscription  | Owner / Admin |

---

## Statistics Controller

| Method | Endpoint                       | Description               |
| ------ | ------------------------------ | ------------------------- |
| `GET`  | `/statistics/summary`          | Personal spending summary |
| `GET`  | `/statistics/monthly-dynamics` | Monthly spending dynamics |

Example:

```http
GET /statistics/monthly-dynamics?months=6
```

---

## Report Controller

| Method | Endpoint               | Description                |
| ------ | ---------------------- | -------------------------- |
| `GET`  | `/reports/annual.csv`  | Export annual CSV report   |
| `GET`  | `/reports/annual.xlsx` | Export annual Excel report |

---

## Admin Controller

| Method | Endpoint                          | Description                          |
| ------ | --------------------------------- | ------------------------------------ |
| `GET`  | `/admin/statistics/service-usage` | System-wide service usage statistics |

---

# 📚 Swagger / OpenAPI

The easiest way to test the project is through Swagger UI.

Open:

```text
https://subscriptionmaster.app/swagger-ui/index.html
```

OpenAPI JSON:

```text
https://subscriptionmaster.app/v3/api-docs
```

---

# 🧪 How to Use the Live Application

## 1. Open Swagger

Visit:

```text
https://subscriptionmaster.app/swagger-ui/index.html
```

You will see all available controllers and endpoints.

---

## 2. Register an Account

Open:

```text
POST /auth/register
```

Click:

```text
Try it out
```

Enter the request body required by Swagger.

Example structure:

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!"
}
```

Then click:

```text
Execute
```

A successful request creates your user account.

---

## 3. Login

Use:

```text
POST /auth/login
```

Provide the email and password you registered with.

Example:

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!"
}
```

The API returns authentication information including an access token and refresh token.

Copy the **access token**.

---

## 4. Authorize Swagger

At the top of Swagger UI, click:

```text
Authorize
```

Enter your token.

Depending on the Swagger security configuration, enter either:

```text
Bearer YOUR_ACCESS_TOKEN
```

or simply:

```text
YOUR_ACCESS_TOKEN
```

Swagger will then send the token with protected requests.

---

## 5. Create a Subscription

Open:

```text
POST /subscriptions
```

Click **Try it out** and create a subscription using the request schema shown by Swagger.

A subscription includes values such as:

```text
name
price
currency
startDate
billingFrequency
category
```

For example, you could create:

```text
Name: Netflix
Price: 9.99
Currency: USD
Billing frequency: MONTHLY
Status: ACTIVE
```

The system automatically calculates the next payment date according to the billing frequency.

---

## 6. View Your Subscriptions

Use:

```http
GET /subscriptions
```

The API only returns subscriptions you are authorized to see.

You can also filter the results.

Example:

```http
GET /subscriptions?status=ACTIVE&currency=USD
```

---

## 7. Use Pagination

Example:

```http
GET /subscriptions?page=0&size=10
```

Sort by next payment date:

```http
GET /subscriptions?page=0&size=10&sort=nextPaymentDate,asc
```

---

## 8. Filter by Price

Example:

```http
GET /subscriptions?minPrice=5&maxPrice=50
```

Filters can be combined:

```http
GET /subscriptions?status=ACTIVE&currency=USD&minPrice=5&maxPrice=50&page=0&size=10
```

---

## 9. Update a Subscription

Use:

```http
PUT /subscriptions/{id}
```

For example:

```http
PUT /subscriptions/1
```

Users can update only subscriptions they are authorized to manage.

---

## 10. Delete a Subscription

Use:

```http
DELETE /subscriptions/{id}
```

For example:

```http
DELETE /subscriptions/1
```

The subscription is **soft deleted**, meaning its database record is preserved for financial history.

---

## 11. View Spending Statistics

Use:

```http
GET /statistics/summary
```

For monthly spending dynamics:

```http
GET /statistics/monthly-dynamics?months=6
```

or:

```http
GET /statistics/monthly-dynamics?months=12
```

---

## 12. Download Annual Reports

CSV:

```text
https://subscriptionmaster.app/reports/annual.csv
```

Excel:

```text
https://subscriptionmaster.app/reports/annual.xlsx
```

These endpoints require authentication.

If using Swagger, authorize first and execute the endpoint from the Swagger interface.

---

## 13. Refresh an Access Token

When your access token expires, you do not need to log in again.

Use:

```http
POST /auth/refresh
```

and send the refresh token obtained during authentication.

The server returns a new access token.

---

# 🐳 Running Locally

## Prerequisites

Install:

* Java 21
* Maven
* Docker
* Docker Compose
* Git

---

## 1. Clone the Repository

```bash
git clone https://github.com/mirkamol-code/subscription-master.git
cd subscription-master
```

---

## 2. Generate a JWT Secret

Generate a secure secret:

```bash
openssl rand -base64 48
```

Copy the generated value.

---

## 3. Start with Docker Compose

The easiest way to run the complete system is:

```bash
JWT_SECRET='your-secure-secret-at-least-32-bytes' docker compose up --build
```

Docker Compose starts:

```text
Spring Boot application
        +
PostgreSQL database
```

The application becomes available at:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
```

Health:

```text
http://localhost:8080/actuator/health
```

---

## 4. Run in Background

```bash
JWT_SECRET='your-secure-secret-at-least-32-bytes' docker compose up -d --build
```

Check running containers:

```bash
docker compose ps
```

View logs:

```bash
docker compose logs -f
```

Stop the project:

```bash
docker compose down
```

---

# 💻 Local Development Without Running the App Container

You can run PostgreSQL through Docker and the Spring Boot application directly from IntelliJ or Maven.

Start PostgreSQL:

```bash
docker compose -f docker-compose-db-only.yml up -d db-local-postgres
```

Run the application:

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Then open:

```text
http://localhost:8080/swagger-ui/index.html
```

---

# ⚙️ Environment Variables

Important production environment variables include:

```text
POSTGRES_USER
POSTGRES_PASSWORD
POSTGRES_DB

JWT_SECRET

LOG_NOTIFICATIONS_ENABLED

GMAIL_NOTIFICATIONS_ENABLED
GMAIL_USERNAME
GMAIL_APP_PASSWORD
GMAIL_FROM

MAIL_HEALTH_ENABLED
```

Example:

```bash
export POSTGRES_USER=mirkamol
export POSTGRES_PASSWORD=strong-database-password
export POSTGRES_DB=subscription_master_db

export JWT_SECRET="$(openssl rand -base64 48)"

export GMAIL_NOTIFICATIONS_ENABLED=false
```

Then:

```bash
docker compose up --build
```

> Do not commit real database passwords, Gmail App Passwords, or JWT secrets to Git.

---

# 🗄 Database

The project uses:

```text
PostgreSQL
```

Database migrations are managed by:

```text
Flyway
```

Flyway applies migrations automatically during application startup.

Docker Compose also creates a persistent PostgreSQL volume so data survives container restarts.

---

# 🧪 Automated Tests

The project contains both **unit tests** and **integration tests**.

Run all tests:

```bash
mvn test
```

Docker must be running because integration tests use Testcontainers.

---

## Unit Testing

Unit tests use:

```text
JUnit 5
Mockito
AssertJ
```

They test business logic in isolation.

---

## Integration Testing

Integration tests use:

```text
Spring Boot Test
WebTestClient
Testcontainers
PostgreSQL
```

Testcontainers automatically launches an isolated PostgreSQL instance.

You do not need to manually create a test database.

The integration tests validate functionality including:

* Authentication
* JWT authorization
* Subscription CRUD
* User ownership
* Filtering
* Validation
* Role restrictions
* Reports
* Soft deletion

---

# ❤️ Actuator Health Check

Check application health:

### Production

```text
https://subscriptionmaster.app/actuator/health
```

### Local

```text
http://localhost:8080/actuator/health
```

---

# 🔐 Security Design

The project follows several security principles:

* Passwords are hashed using BCrypt
* JWT authentication is stateless
* Access and refresh tokens are separated
* Resource IDs do not bypass ownership checks
* Users can manage only authorized resources
* Admin functionality requires elevated permissions
* Production secrets are supplied externally
* Sensitive configuration should not be committed to Git

---

# 🚀 Production Deployment

The current production architecture is:

```text
                       Internet
                           │
                           │ HTTPS
                           ▼
                    ┌─────────────┐
                    │    Nginx    │
                    │ Reverse     │
                    │ Proxy       │
                    └──────┬──────┘
                           │
                           ▼
                 ┌───────────────────┐
                 │ Subscription      │
                 │ Master            │
                 │ Spring Boot       │
                 └────────┬──────────┘
                          │
                ┌─────────┴─────────┐
                ▼                   ▼
        ┌──────────────┐     ┌──────────────┐
        │ PostgreSQL   │     │ CBU API      │
        │              │     │ Exchange     │
        │              │     │ Rates        │
        └──────────────┘     └──────────────┘
```

Infrastructure:

```text
AWS EC2
Docker
Docker Compose
PostgreSQL
Nginx
HTTPS
```

---

# ✅ Assignment Requirements Coverage

| Requirement                     | Implementation      |
| ------------------------------- | ------------------- |
| JWT authentication              | ✅                   |
| Registration/login              | ✅                   |
| Refresh token                   | ✅                   |
| BCrypt passwords                | ✅                   |
| User subscription isolation     | ✅                   |
| Subscription CRUD               | ✅                   |
| Automatic next payment date     | ✅                   |
| Weekly/monthly/annual billing   | ✅                   |
| Pagination                      | ✅                   |
| Filtering                       | ✅ JPA Specification |
| Soft delete                     | ✅                   |
| Payment history                 | ✅                   |
| CBU exchange-rate integration   | ✅                   |
| UZS conversion                  | ✅                   |
| Caffeine caching                | ✅                   |
| Resilience4j fallback           | ✅                   |
| Scheduled reminders             | ✅                   |
| Log/email notification strategy | ✅                   |
| ShedLock                        | ✅                   |
| CSV export                      | ✅                   |
| XLSX export                     | ✅ Apache POI        |
| Statistics API                  | ✅                   |
| Admin statistics                | ✅                   |
| PostgreSQL                      | ✅                   |
| Flyway migrations               | ✅                   |
| MapStruct                       | ✅                   |
| Swagger/OpenAPI                 | ✅                   |
| Docker Compose                  | ✅                   |
| Unit tests                      | ✅                   |
| Integration tests               | ✅ Testcontainers    |
| Global exception handling       | ✅                   |
| Production deployment           | ✅ AWS EC2           |

---

# 🔗 Useful Links

| Resource                      | URL                                                  |
| ----------------------------- | ---------------------------------------------------- |
| 🌐 Production API             | https://subscriptionmaster.app                       |
| 📖 Swagger UI                 | https://subscriptionmaster.app/swagger-ui/index.html |
| 📄 OpenAPI JSON               | https://subscriptionmaster.app/v3/api-docs           |
| ❤️ Health Check               | https://subscriptionmaster.app/actuator/health       |
| 💻 GitHub Repository          | https://github.com/mirkamol-code/subscription-master |
| 💱 Central Bank of Uzbekistan | https://cbu.uz                                       |

---

# 👨‍💻 Author

**Mirkamol**

Backend Java Developer

Technologies demonstrated in this project:

```text
Java
Spring Boot
Spring Security
JWT
PostgreSQL
Flyway
Docker
AWS
Nginx
Testcontainers
Resilience4j
Caffeine
ShedLock
MapStruct
```

---

# 📌 Repository

https://github.com/mirkamol-code/subscription-master

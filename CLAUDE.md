# NovaBank Core — Project Context for Claude Code

## What this is
NovaBank is a **fictional digital bank** built as a portfolio platform. This repo is Project 1:
the core banking APIs. Later projects (Azure/AKS deployment, AI enquiry assistant, KYC,
fraud model) consume these APIs, so API quality and consistency matter more than speed.

All data is **synthetic**. Never add real customer data, real card numbers, or real bank names.

## Owner & working style
- The owner is an enterprise architect. Explain design choices briefly, in architecture terms.
- **Work in small steps.** Propose a plan first, then implement one step at a time.
- After each step: run the build and tests, show the result, and stop for review.
- Never batch many unrelated changes into one step.

## Tech stack
- Java 21, latest stable Spring Boot, Maven multi-module
- Spring Web, Spring Data JPA, Bean Validation, Spring Boot Actuator
- PostgreSQL (one database per service), Flyway for all schema changes
- springdoc-openapi (Swagger UI on every service)
- Tests: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL)
- Local run: Docker Compose

## Repository layout
```
novabank-core/
├── CLAUDE.md
├── BUILD_PLAN.md
├── pom.xml                  # parent POM
├── docker-compose.yml
├── customer-service/        # port 8081
├── account-service/         # port 8082 (accounts + transactions + transfers)
├── card-service/            # port 8083
└── data-generator/          # synthetic data seeding
```

## Service boundaries (do not change without asking)
- **customer-service** owns customers (profile, contact details, KYC status).
- **account-service** owns accounts, balances, transactions, and transfers.
  Accounts and transactions live together on purpose: a balance and its postings
  must change in the same database transaction.
- **card-service** owns cards (debit/credit), card status, limits.
- Services never read another service's database. Cross-service data goes via REST.

## Coding conventions
- Package structure per service: `api` (controllers, DTOs), `domain` (entities, services),
  `repository`, `config`.
- Never expose JPA entities in APIs; always map to DTOs (Java records).
- Money: `BigDecimal` with explicit currency code (ISO 4217, default AED). Never `double`.
- IDs: UUIDs. Timestamps: `Instant`, stored in UTC.
- Errors: RFC 7807 `ProblemDetail` via a `@RestControllerAdvice` in every service.
- Validate all request bodies with Bean Validation.
- Every endpoint documented with OpenAPI annotations (summary + example).

## Banking rules (important)
- **Card numbers:** never store or return a full PAN. Store only `maskedPan`
  (e.g. `4111 **** **** 1234`) and `last4`.
- **Customer scoping:** every read of accounts, transactions, or cards is scoped to a
  customer, e.g. `/customers/{customerId}/accounts`. Later, the customer ID will come
  from a JWT; design so that swap is easy.
- **Transfers:** require an `Idempotency-Key` header. Replaying the same key must return
  the original result, not post twice.
- **Balances:** a transfer must fail with a clear error if funds are insufficient.
  Debit and credit postings happen in one DB transaction.
- Transactions have a free-text `narration` field (it will be used later for
  prompt-injection testing in the AI project — store it as-is, validate length only).

## Definition of done (per service)
- `mvn verify` passes, including Testcontainers integration tests
- Flyway migrations create the schema from scratch
- Swagger UI works at `http://localhost:<port>/swagger-ui.html`
- Actuator health at `/actuator/health`
- A `.http` file with example requests for every endpoint

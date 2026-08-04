# Car Wash Booking Queue System

Spring Boot backend foundation for car wash booking and queue management. The current codebase exposes local-development APIs for user records, vehicles, service catalog items, bookings, queue entries, in-app notification records, and daily summary reporting over in-memory repositories.

## Current Backend Foundation

Implemented in the current backend:

- User record CRUD APIs.
- Vehicle CRUD APIs with owner lookup and duplicate plate validation per owner.
- Service catalog CRUD APIs with activate/deactivate workflows.
- Booking CRUD APIs with confirm/cancel workflows and service-layer validation.
- Queue entry APIs for creation, position updates, call/start/complete transitions, and deletion.
- In-app notification record lookup for users.
- Daily summary report API computed from current in-memory booking and queue data.
- Swagger/OpenAPI documentation for local API exploration.
- Docker/local development setup.
- Service-layer, repository, and API integration tests.

## Important Current Limitations

- Storage is currently in-memory only for the running application; data is not durable across restarts.
- Stateless JWT authentication, secure credential storage, RBAC, and repository-verified ownership are implemented.
- Staff and business-owner operational access is global until tenant isolation is implemented.
- Notification records are stored in-app; external SMS/email delivery is not implemented.
- Daily summary reporting is basic and in-memory; analytics dashboards and revenue reporting are future work.
- Payments, business registration, multi-tenancy, production observability, and production SaaS hardening are planned/future work.

## Tech Stack

- Java 21
- Spring Boot 3
- Maven
- Spring Web
- Spring Validation
- Springdoc OpenAPI / Swagger UI
- JUnit 5
- Docker / Docker Compose

## Run Locally

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` or `http://localhost:8080/swagger-ui/index.html`.

The raw OpenAPI document is available at `http://localhost:8080/v3/api-docs`.

## Run with Docker Compose

```bash
docker compose up --build
```

Validate the Compose file without starting containers:

```bash
docker compose -f docker-compose.yml config
```

## Testing

Run the unit and integration test suite:

```bash
./mvnw clean test
```

Run the full Maven verification lifecycle:

```bash
./mvnw clean verify
```

## API Areas

- `/api/users` - user record management.
- `/api/vehicles` - vehicle registration, lookup, update, and deletion.
- `/api/services` - service catalog management and activation state.
- `/api/bookings` - booking creation, lookup, update, confirmation, cancellation, and deletion-as-cancel.
- `/api/queue-entries` - queue entry creation, position updates, status transitions, and deletion.
- `/api/notifications` - in-app notification record lookup by user.
- `/api/reports/daily-summary` - basic daily summary from current in-memory data.

See [API Documentation](documentation/API-DOCUMENTATION.md) and Swagger UI for endpoint details.

## Product Documentation

- [Architecture](documentation/ARCHITECTURE.md)
- [Product Specification](documentation/SPECIFICATION.md)
- [Roadmap](documentation/ROADMAP.md)
- [API Documentation](documentation/API-DOCUMENTATION.md)
- [Domain Model](documentation/DOMAIN-MODEL.md)
- [System Requirements](documentation/SYSTEM-REQUIREMENTS.md)
- [User Stories](documentation/USER-STORIES.md)
- [Product Backlog](documentation/PRODUCT-BACKLOG.md)

## Planned and Future Capabilities

Planned near-term work focuses on backend hardening: stronger booking/queue rules, broader API tests, improved error contracts, persistent storage design, and clearer notification boundaries.

Future SaaS hardening includes refresh-token design, brute-force protection, PostgreSQL persistence, migrations, tenant-aware business registration, external SMS/email providers, payment workflows, operational dashboards, monitoring/observability, and production deployment hardening.

## Bearer Authentication

Copy `.env.example` to `.env`, replace its deliberately invalid JWT placeholder with output from
`openssl rand -base64 32`, and then start the application. The signing secret must decode to at least
32 bytes. Access tokens use HS256, are issued by `carwash-booking-queue-system`, and expire after 20
minutes by default (`carwash.security.jwt.access-token-ttl`).

1. Register with `POST /api/users`.
2. Login with `POST /api/auth/login` using the registered email and exact password.
3. Copy `accessToken` from the response.
4. Send `Authorization: Bearer <token>`.
5. Call a protected API such as `GET /api/users`.

Only registration, login, OpenAPI/Swagger, browser preflight, and error handling are public. All other
`/api/**` routes require a valid token. Because storage is in-memory, a restart removes users and makes
their tokens invalid; tokens also stop working when an account becomes inactive. There are no refresh
tokens or revocation list. Tenant isolation and brute-force protection remain future security work.

## Authorization and administrator bootstrap

Public registration always creates a `CUSTOMER`; request JSON cannot select a role. `STAFF`,
`BUSINESS_OWNER`, and `PLATFORM_ADMIN` are server assigned through the platform-admin-only
`PUT /api/admin/users/{userId}/role`. Role changes invalidate old tokens and require login again.
Customers access only their own private resources; staff operate vehicles, bookings and queues; owners
also manage services and reports; platform administrators manage users and roles.

Bootstrap is disabled by default. Configure every `SECURE_BOOTSTRAP_ADMIN_*` value in the uncommitted
`.env` file to enable it. Never commit `.env` or passwords. In-memory data, including bootstrap data, is
recreated after restart. Staff and owner access is not tenant-scoped; TENANT-001 remains separate work.

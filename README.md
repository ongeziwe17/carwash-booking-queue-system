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
- Authentication, login sessions, JWTs, RBAC enforcement, and secure credential storage are not implemented.
- Roles exist as domain data but are not enforced by Spring Security or controller authorization.
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

Future SaaS hardening includes authentication, secure credential storage, RBAC enforcement, PostgreSQL persistence, migrations, tenant-aware business registration, external SMS/email providers, payment workflows, operational dashboards, monitoring/observability, and production deployment hardening.
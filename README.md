# Car Wash Booking Queue System

A Spring Boot backend for a SaaS-oriented car wash booking and queue management platform. The system helps customers register vehicles, browse wash services, create bookings, join service queues, and receive operational notifications while giving car wash businesses API building blocks for service catalog, booking, queue, and customer management workflows.

## Who It Serves

- **Customers** who want convenient digital booking, vehicle management, queue visibility, and service updates.
- **Car wash operators** who need structured booking intake, queue control, service catalog management, and customer communication workflows.
- **Product contributors** who are extending the backend toward a maintainable SaaS platform.

## Current Backend Capabilities

- REST APIs for users, vehicles, services, bookings, queue entries, and notifications.
- In-memory repository implementations for local development and testable business workflows.
- Service-layer validation for booking creation, queue joining, and resource lookup rules.
- OpenAPI/Swagger documentation for interactive API exploration.
- Docker and Docker Compose setup for reproducible local runs.
- Maven-based test and verification workflow.

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

The API starts on:

```text
http://localhost:8080
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI document is available at:

```text
http://localhost:8080/v3/api-docs
```

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

- `/api/users` - customer and account data workflows.
- `/api/vehicles` - customer vehicle registration and lookup.
- `/api/services` - car wash service catalog workflows.
- `/api/bookings` - service booking workflows.
- `/api/queue` - queue entry and queue-position workflows.
- `/api/notifications` - customer notification records.

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

## Contribution Workflow

1. Create a focused branch, for example `feature/queue-estimates` or `chore/document-api-workflows`.
2. Keep changes scoped to one product or maintenance concern.
3. Run `./mvnw clean test` before opening a pull request.
4. Run `./mvnw clean verify` for broader validation when changing backend behavior.
5. Include API or documentation updates when behavior changes.
6. Open a pull request with a clear summary, testing notes, and any follow-up work.

## Product Direction

This repository is being maintained as a SaaS-ready backend foundation. Near-term work focuses on strengthening booking, queue, notification, and service-management workflows while preparing the codebase for secure authentication, persistence, observability, deployment readiness, and multi-tenant operations in future releases.

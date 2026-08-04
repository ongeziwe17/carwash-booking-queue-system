# Car Wash Booking Queue System

Spring Boot backend foundation for car wash booking and queue management. The current codebase exposes APIs for users, vehicles, services, bookings, queues, notifications, and daily reporting over in-memory repositories.

## Current backend foundation

- User registration and safe profile-management APIs.
- Stateless JWT authentication and role-based authorization.
- Vehicle management with ownership and duplicate-plate validation.
- Service catalogue management with activation workflows.
- Booking management with ownership, lifecycle, time, capacity, and vehicle validation.
- Queue lifecycle operations.
- In-app notification lookup.
- Daily summary reporting.
- Swagger/OpenAPI documentation.
- Java 21 Maven, Docker, Docker Compose, and GitHub Actions delivery support.

## Important current limitations

- Storage is in-memory and is lost when the application restarts.
- Staff and business-owner operational access remains global until tenant isolation is implemented.
- External SMS/email delivery is not implemented.
- Payments, business registration, PostgreSQL, production observability, and deployment hardening remain future work.
- The application does not yet expose dedicated Actuator liveness or readiness endpoints.

## Tech stack

- Java 21
- Spring Boot 4
- Maven Wrapper
- Spring Web MVC and Validation
- Spring Security OAuth2 Resource Server
- Springdoc OpenAPI / Swagger UI
- JUnit 5 and JaCoCo
- Docker / Docker Compose
- GitHub Actions

## Local application setup

Create your local environment file:

```bash
cp .env.example .env
```

Generate a JWT signing secret:

```bash
openssl rand -base64 32
```

Replace `REPLACE_WITH_BASE64_ENCODED_32_BYTE_SECRET` in `.env`. The decoded secret must contain at least 32 random bytes. Never commit `.env`.

Load the variables into Bash and run the application:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Docker Compose

Validate the resolved Compose configuration before starting containers:

```bash
docker compose config
```

Build the local image:

```bash
docker compose build
```

Start in the foreground:

```bash
docker compose up
```

Start or rebuild in the background:

```bash
docker compose up --build -d
```

Follow application logs:

```bash
docker compose logs -f carwash-api
```

Stop the application:

```bash
docker compose down
```

Remove stopped services and orphan containers without deleting persistent data:

```bash
docker compose down --remove-orphans
```

The Compose file intentionally contains only the API. PostgreSQL remains tracked under DATA-002.

## Testing

Run tests:

```bash
./mvnw clean test
```

Run the full verification lifecycle, including the JaCoCo report and coverage gate:

```bash
./mvnw clean verify
```

Generated outputs include:

- `target/surefire-reports/`
- `target/failsafe-reports/` when integration-test executions are added
- `target/site/jacoco/`
- `target/carwash-api.jar`

## Branch and delivery workflow

`staging` is the active integration branch:

```text
feature/fix/security/ci branch
        ↓ pull request
staging
        ↓ controlled promotion
develop
        ↓ release promotion
master
        ↓
v* release tag
```

Pull requests into `staging`, `develop`, and `master` run Maven, workflow, Docker Compose, Docker image, smoke-test, and vulnerability validation. Registry login and publishing run only for approved branch or tag pushes.

See [Delivery and Docker Workflow](documentation/DELIVERY-AND-DOCKER.md) for branch protection recommendations, image tags, required secrets, CI jobs, artifacts, and local Docker guidance.

## Authentication and authorization

1. Register through `POST /api/users`.
2. Login through `POST /api/auth/login`.
3. Copy the returned `accessToken`.
4. Send `Authorization: Bearer <token>` to protected endpoints.

Public registration always creates a `CUSTOMER`. `STAFF`, `BUSINESS_OWNER`, and `PLATFORM_ADMIN` roles are server-assigned through the platform-admin-only role endpoint. Role changes invalidate older tokens.

Bootstrap administration is disabled by default. Configure all `SECURE_BOOTSTRAP_ADMIN_*` values in the uncommitted `.env` file before enabling it.

## API areas

- `/api/users`
- `/api/auth`
- `/api/admin/users`
- `/api/vehicles`
- `/api/services`
- `/api/bookings`
- `/api/queue-entries`
- `/api/notifications`
- `/api/reports/daily-summary`

## Product documentation

- [Architecture](documentation/ARCHITECTURE.md)
- [Product Specification](documentation/SPECIFICATION.md)
- [Roadmap](documentation/ROADMAP.md)
- [API Documentation](documentation/API-DOCUMENTATION.md)
- [Domain Model](documentation/DOMAIN-MODEL.md)
- [System Requirements](documentation/SYSTEM-REQUIREMENTS.md)
- [User Stories](documentation/USER-STORIES.md)
- [Product Backlog](documentation/PRODUCT-BACKLOG.md)
- [Delivery and Docker Workflow](documentation/DELIVERY-AND-DOCKER.md)

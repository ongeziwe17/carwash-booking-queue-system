# Car Wash Booking Queue System

Spring Boot backend foundation for car wash booking, queue management, and Marketplace onboarding/scheduling. The code is organized as a capability-based modular monolith with explicit `bootstrap`, `shared`, `identity`, `access`, `vehicle`, `catalog`, `booking`, `queue`, `notification`, `reporting`, `marketplace`, `discovery`, and `recommendation` boundaries enforced by ArchUnit. The current codebase exposes APIs for users, vehicles, reusable global services, branch-specific offerings, bookings, queues, notifications, daily reporting, Marketplace businesses, physical branches, weekly branch hours, temporary closures, timezone-aware open-status decisions, authenticated nearby branch discovery, branch-aware availability, and explainable rule-based recommendations. Storage is selectable: the lightweight default/test profile uses in-memory adapters, while the `postgres` profile uses durable module-owned JPA adapters and Flyway migrations.

## Current backend foundation

- User registration and safe profile-management APIs.
- Stateless JWT authentication and role-based authorization.
- Canonical server-side tenant memberships for staff/business owners, trusted `tenant_id` JWT validation, and tenant-scoped Marketplace/operational access.
- Vehicle management with ownership and duplicate-plate validation.
- Service catalogue management with activation workflows.
- Branch-scoped booking management with canonical service offerings, ownership, lifecycle, time, capacity, and vehicle validation.
- Read-only single-location service availability with configured operating hours, interval slots, service duration, and remaining global capacity.
- Branch-aware exact-instant availability using effective public branches, operating-window-anchored slot grids, unambiguous branch-local starts, full service-window hours/closures, offering price/duration/capacity, overlapping active bookings, same-day branch queue estimates, and optional raw-distance radius filtering.
- Branch-partitioned queue lifecycle, ordering, call-next, and offering-duration wait estimation.
- In-app notification lookup with bounded branch/offering context.
- Explicit branch- or business-scoped daily summary reporting.
- Marketplace business/branch registration, bounded lifecycle management, coordinates, timezones, and basic active/public branch discovery.
- Marketplace branch scheduling with atomic weekly intervals, overnight/week-boundary support, temporary closure history, and explicit-instant open-status decisions.
- Catalog-owned branch service offerings with independent price, duration, configured concurrent capacity, lifecycle, and parent-aware discovery.
- Nearby branch discovery with deterministic Haversine distance, optional raw-distance radius filtering, service-offering and explicit-instant open filters, and bounded customer responses.
- Explainable branch recommendations with five deterministic preferences, raw-value ranking, normalized component scores, validated weights, stable ties, and AVAIL-002-owned eligibility.
- PostgreSQL persistence with migration-owned schema, database transactions, optimistic versions, cross-instance booking/queue locks, lossless nanosecond mappings, and restart durability.
- Swagger/OpenAPI documentation.
- Java 21 Maven, Docker, Docker Compose, and GitHub Actions delivery support.

## Important current limitations

- The default profile is intentionally in-memory and loses data on restart; select `postgres` for durability.
- Existing operational users are not assigned a tenant implicitly; platform administrators must explicitly onboard them before they can authenticate as staff or business owners.
- External SMS/email delivery is not implemented.
- Nearby distance is straight-line only; no routing, traffic, geocoding, or external maps provider is used.
- Payments, capacity reservations, concurrent bay/staff scheduling, production observability, backups/restore automation, and deployment hardening remain future work.
- Recommendations are point-in-time rules only; personalization, machine learning, sponsored ranking, dynamic pricing, traffic-aware routing, and holds are not implemented.
- The application does not yet expose dedicated Actuator liveness or readiness endpoints.

## Tech stack

- Java 21
- Spring Boot 4
- Maven Wrapper
- Spring Web MVC and Validation
- Spring Security OAuth2 Resource Server
- Spring Data JPA, PostgreSQL, and Flyway
- Springdoc OpenAPI / Swagger UI
- JUnit Jupiter and JaCoCo
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

Replace `REPLACE_WITH_BASE64_ENCODED_32_BYTE_SECRET` in `.env`. If using PostgreSQL directly, also replace both database-password placeholders and set `SPRING_PROFILES_ACTIVE=postgres`. Never commit `.env`.

Load the variables into Bash and run the application:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

This command uses the in-memory profile unless `SPRING_PROFILES_ACTIVE=postgres` and the three datasource variables are supplied. PostgreSQL startup runs Flyway and validates its schema with `ddl-auto=validate`; Hibernate never creates or updates tables.

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

Set both local placeholders in `.env` (JWT secret and PostgreSQL password), then start the API and PostgreSQL in the foreground:

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

Inspect PostgreSQL and applied migrations:

```bash
docker compose exec postgres psql -U "${POSTGRES_USER:-carwash}" -d "${POSTGRES_DB:-carwash}"
docker compose exec postgres psql -U "${POSTGRES_USER:-carwash}" -d "${POSTGRES_DB:-carwash}" \
  -c 'select installed_rank, version, description, success from flyway_schema_history order by installed_rank'
```

Stop the application:

```bash
docker compose down
```

Remove stopped services and orphan containers without deleting persistent data:

```bash
docker compose down --remove-orphans
```

The named `carwash-postgres-data` volume survives those commands and API/container restarts. Deliberately erase local database data only with:

```bash
docker compose down --volumes --remove-orphans
```

That last command is destructive and cannot recover the removed local volume.

## Runtime policy configuration

Booking, availability, notification, queue, recommendation, and application-time policies use validated typed Spring configuration with safe defaults and environment-variable overrides. See [Runtime Policy Configuration](documentation/CONFIGURATION.md) for the supported properties, scheduling-window rules, recommendation weights/radius, validation, cancellation cutoff semantics, duration syntax, and override examples.

## Testing

The test suite has two Maven responsibilities:

- **Surefire** runs unit, repository, security, and service tests named `*Test` or `*Tests`.
- **Failsafe** runs Spring/API integration tests named `*IntegrationTest` during `integration-test` and `verify`.

Run only the Surefire test category:

```bash
./mvnw test
```

Run unit tests followed by all integration tests, the JaCoCo report, and the 80% line-coverage gate:

```bash
./mvnw verify
```

Start from a clean build directory and run the complete release-gate verification:

```bash
./mvnw clean verify
```

Tests use the dedicated `test` Spring profile from `src/test/resources/application-test.properties`. It contains only deterministic test-safe configuration, disables bootstrap administration, lowers BCrypt cost for test execution, and uses a clearly test-only signing key. Tests do **not** require a developer `.env` file.

PostgreSQL-specific Failsafe tests use the pinned real PostgreSQL image through Testcontainers; they never use H2. They verify clean/incremental Flyway migration, repository parity, constraints, rollback, restart/authentication durability, nanosecond precision, optimistic conflicts, capacity serialization, and branch queue concurrency. A local Docker daemon is required for those tests; hosted CI is authoritative when Docker is unavailable.

Every Spring API integration test inherits the shared test foundation and begins with empty in-memory application data. Cleanup happens inside one `InMemoryDataCoordinator` write operation in dependency order: notifications, queue entries, bookings, Catalog offerings, Marketplace closures/schedules, branches/businesses, vehicles, services, then users. The Spring application context is reused; `@DirtiesContext` is not the default isolation mechanism.

Integration fixtures use a fresh `TestIdFactory` per test method. IDs are readable and local to that test, for example `bookingworkflowintegrationtest-create-user-001`, rather than global IDs such as `u1` or timestamp-only values. Notification IDs are generated through an injectable abstraction; integration tests reset only the test implementation before each method while production exposes no reset operation.

JUnit class and method order are randomized deterministically. CI verifies the full suite with seeds `11001` and `11002`. Reproduce an order-specific failure with the recorded seed:

```bash
./mvnw --batch-mode -Dtest.order.seed=11001 clean verify
```

Selected tests can be executed independently with Maven selectors:

```bash
./mvnw --batch-mode -Dtest=UserManagementServiceTest test
./mvnw --batch-mode -Djacoco.skip=true -Dit.test=BookingWorkflowIntegrationTest verify
./mvnw --batch-mode -Djacoco.skip=true -Dit.test=RbacAuthorizationIntegrationTest verify
```

The JaCoCo HTML report is generated at `target/site/jacoco/index.html`. The enforced line-coverage minimum is **80%**; CI artifacts contain the measured report for each verified commit.

Generated outputs include:

- `target/surefire-reports/`
- `target/failsafe-reports/`
- `target/site/jacoco/`
- `target/carwash-api.jar`

GitHub Actions uploads Surefire/Failsafe reports, JaCoCo coverage, the verified application JAR, OpenAPI JSON, and Docker vulnerability results from the main verification path. The repeatability matrix uploads seed-labelled test reports when a seed fails, making the failing order reproducible without rerunning Docker validation for each seed.

## API Acceptance Testing with Bruno

The repository includes a Git-versioned Bruno OpenCollection suite for external HTTP acceptance, RBAC, ownership, validation, security, data-integrity, and end-to-end workflow testing. It complements the Java unit and Spring integration tests and runs against a real application process.

See [Bruno API Acceptance Suite](tests/bruno/carwash-api/README.md) for local setup, secret handling, targeted tags, reports, endpoint coverage, and the RBAC matrix.

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

Pull requests into `staging`, `develop`, and `master` run Maven, workflow, test-repeatability, Docker Compose, Docker image, smoke-test, and vulnerability validation. Registry login and publishing run only for approved branch or tag pushes.

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
- `/api/marketplace/businesses`
- `/api/marketplace/branches`
- `/api/marketplace/offerings`
- `/api/recommendations/branches`
- `/api/notifications`
- `/api/reports/daily-summary`

## API validation and errors

All JSON request bodies use explicit request DTOs and Bean Validation. Unknown JSON properties are rejected,
including attempts to submit server-controlled fields. Query and path parameters use typed binding and safe
validation; report dates use ISO `yyyy-MM-dd` format.

Every error response contains `status`, a stable application `code`, a safe `message`, an ISO-8601 UTC
`timestamp`, the request `path`, and a `fieldErrors` array. Validation errors use `VALIDATION_FAILED` and
sorted field entries. Malformed JSON uses `MALFORMED_REQUEST`; missing resources use `RESOURCE_NOT_FOUND`;
missing or invalid authentication remains 401; authenticated authorization failures remain 403; unexpected
failures use `INTERNAL_ERROR` with the generic message `An unexpected error occurred`.

See [API Documentation](documentation/API-DOCUMENTATION.md#standard-response-and-error-conventions) for the full code table
and representative 400, 401, 403, 404, and 500 responses.

## Product documentation

- [Architecture](documentation/ARCHITECTURE.md)
- [Marketplace Tenant Isolation](documentation/TENANT-ISOLATION.md)
- [Product Specification](documentation/SPECIFICATION.md)
- [Roadmap](documentation/ROADMAP.md)
- [API Documentation](documentation/API-DOCUMENTATION.md)
- [Runtime Policy Configuration](documentation/CONFIGURATION.md)
- [Testing](documentation/TESTING.md)
- [Bruno API Acceptance Suite](tests/bruno/carwash-api/README.md)
- [Domain Model](documentation/DOMAIN-MODEL.md)
- [System Requirements](documentation/SYSTEM-REQUIREMENTS.md)
- [User Stories](documentation/USER-STORIES.md)
- [Product Backlog](documentation/PRODUCT-BACKLOG.md)
- [Delivery and Docker Workflow](documentation/DELIVERY-AND-DOCKER.md)
- [Coverage Baseline](documentation/COVERAGE.md)

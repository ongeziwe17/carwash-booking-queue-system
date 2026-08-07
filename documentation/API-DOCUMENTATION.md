# API Documentation

## Swagger and OpenAPI

Run the application:

```bash
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Authentication and authorization

`POST /api/users` and `POST /api/auth/login` are public. OpenAPI/Swagger, OPTIONS preflight, and `/error` are also public. Other `/api/**` operations require a bearer JWT.

A successful login returns `accessToken`, `tokenType`, `expiresInSeconds`, `expiresAt`, and the bounded `UserResponse`. Invalid credentials and inactive accounts return the same safe 401 response. Roles and permissions are server controlled. Customer access is ownership based; staff, business owners, and platform administrators receive the documented operational permissions. Tenant isolation is not yet implemented, so elevated operational access remains global.

## Current API areas

### Users

Base path: `/api/users`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/users` | List users subject to authorization. |
| GET | `/api/users/{id}` | Retrieve a user. |
| POST | `/api/users` | Register a customer. |
| PUT | `/api/users/{id}` | Update bounded profile fields. |
| DELETE | `/api/users/{id}` | Delete an eligible user. |

Registration accepts only `userId`, `fullName`, `email`, `phone`, and raw `password`. Passwords are validated without trimming and stored only as BCrypt encodings. Responses never include credentials or nested aggregate graphs. User deletion is rejected while vehicles or bookings reference the user. The last active platform administrator cannot be deleted.

### Vehicles

Base path: `/api/vehicles`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/vehicles` | List vehicles. |
| GET | `/api/vehicles/{id}` | Retrieve a vehicle. |
| POST | `/api/vehicles` | Create a vehicle for the request user. |
| PUT | `/api/vehicles/{id}` | Update vehicle details without changing ownership. |
| DELETE | `/api/vehicles/{id}` | Delete an unreferenced vehicle. |

A vehicle ID cannot be reused. Plate numbers are unique per owner during create and update, compared case-insensitively after trimming. Vehicle deletion is rejected while any booking references it.

### Services

Base path: `/api/services`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/services` | List service catalogue entries. |
| GET | `/api/services/{id}` | Retrieve a service. |
| POST | `/api/services` | Create a service. |
| PUT | `/api/services/{id}` | Update service details. |
| DELETE | `/api/services/{id}` | Delete an unreferenced service. |
| POST | `/api/services/{id}/activate` | Activate a service. |
| POST | `/api/services/{id}/deactivate` | Deactivate a service. |

Referenced services cannot be physically deleted. Deactivate them to preserve booking and queue history.

### Bookings

Base path: `/api/bookings`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/bookings` | List bookings. |
| GET | `/api/bookings/{id}` | Retrieve a booking. |
| POST | `/api/bookings` | Create a booking. |
| PUT | `/api/bookings/{id}` | Update an editable booking. |
| DELETE | `/api/bookings/{id}` | Execute the current cancellation workflow. |
| POST | `/api/bookings/{id}/confirm` | Confirm a booking. |
| POST | `/api/bookings/{id}/cancel` | Cancel a booking. |

The path ID is authoritative and ownership cannot be transferred through update. Detail updates are allowed only while status is `CREATED` or `CONFIRMED`. `IN_SERVICE`, `CANCELLED`, and `COMPLETED` bookings reject ordinary updates.

Internal physical deletion is limited to cancelled bookings with no queue entry. It removes the booking from the repository and owner collection and cleans associated notifications. No new public hard-delete endpoint is introduced by DATA-001.

### Queue entries

Base path: `/api/queue-entries`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/queue-entries` | List queue entries. |
| GET | `/api/queue-entries/{id}` | Retrieve a queue entry. |
| POST | `/api/queue-entries` | Create and attach a queue entry. |
| PUT | `/api/queue-entries/{id}/position` | Reposition a waiting entry. |
| POST | `/api/queue-entries/{id}/call-next` | Mark a waiting entry as called. |
| POST | `/api/queue-entries/{id}/start` | Start service for a called entry. |
| POST | `/api/queue-entries/{id}/complete` | Complete an in-progress entry. |
| DELETE | `/api/queue-entries/{id}` | Delete a waiting entry and clear its booking link. |

Only `WAITING` entries may be repositioned or deleted. Automated ordering and eligibility remain under QUEUE-001 and QUEUE-002.

### Notifications

Base path: `/api/notifications`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/notifications/user/{userId}` | List recent in-app notifications for an authorized user. |

External SMS/email delivery is not implemented.

### Reports

Base path: `/api/reports`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/reports/daily-summary?date={yyyy-MM-dd}` | Return the current in-memory daily booking and queue summary. |

## Standard error contract

Every API error has the same structure:

```json
{
  "status": 400,
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "Vehicle ID already exists",
  "timestamp": "2026-08-05T08:30:00Z",
  "path": "/api/vehicles",
  "fieldErrors": []
}
```

Stable codes include `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `MISSING_PARAMETER`, `BUSINESS_RULE_VIOLATION`, `RESOURCE_NOT_FOUND`, `INVALID_CREDENTIALS`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, and `INTERNAL_ERROR`.

Error responses never include rejected values, request bodies, Java exception names, stack traces, passwords, encoded credentials, JWTs, authorization headers, or signing secrets.

## DATA-001 integrity behaviour

The in-memory repository contract distinguishes atomic creation from update:

```text
insert -> fails when ID exists
update -> fails when ID is missing
deleteById -> reports whether deletion occurred
```

Duplicate IDs do not overwrite records. They return HTTP 400 with `BUSINESS_RULE_VIOLATION`, for example:

```json
{
  "status": 400,
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "User ID already exists",
  "timestamp": "2026-08-05T08:30:00Z",
  "path": "/api/users",
  "fieldErrors": []
}
```

Multi-repository operations use a shared single-JVM read/write coordination boundary. This prevents supported service operations from exposing partially updated repository and aggregate relationships within one application process. It is not a database transaction or distributed lock.

PostgreSQL constraints, transactions, and multi-instance consistency remain tracked under DATA-002.

## Planned API areas

Not yet implemented:

- refresh tokens, logout, and token revocation
- tenant/business/branch registration and isolation
- PostgreSQL-backed persistence
- payment and refund workflows
- external email/SMS delivery
- ratings and feedback
- rich analytics beyond the daily summary
- pagination/filtering/sorting for list APIs

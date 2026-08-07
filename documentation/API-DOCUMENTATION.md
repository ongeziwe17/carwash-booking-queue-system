# API Documentation

## Swagger UI

Run the application:

```bash
./mvnw spring-boot:run
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## Current API Areas

The current backend exposes the endpoints below. Bearer-token authentication, RBAC, ownership checks, request validation, and the standard error contract are enforced where applicable.

### Users

Base path: `/api/users`

| Method | Path              | Status      | Purpose                       |
|--------|-------------------|-------------|-------------------------------|
| GET    | `/api/users`      | Implemented | List user records.            |
| GET    | `/api/users/{id}` | Implemented | Retrieve a user record by ID. |
| POST   | `/api/users`      | Implemented | Create a user record.         |
| PUT    | `/api/users/{id}` | Implemented | Update a user record.         |
| DELETE | `/api/users/{id}` | Implemented | Delete a user record.         |

User endpoints use bounded API contracts rather than binding or returning the domain model:

- `CreateUserRequest` accepts only `userId`, `fullName`, `email`, `phone`, and `password`.
  Account status, creation/login timestamps, roles, permissions, vehicles, bookings, and notifications are server controlled.
- `UpdateUserRequest` accepts only the editable `fullName`, `email`, and `phone` profile fields. The path parameter is always the authoritative user ID.
- `UserResponse` contains `userId`, `fullName`, `email`, `phone`, `accountStatus`, `createdAt`, `lastLoginAt`, and the nullable scalar `roleName`.
  Credentials and nested role, vehicle, booking, and notification objects are never included.

Registration accepts a raw `password` at the request boundary. The service validates it without trimming and uses BCrypt to create a salted encoded credential before constructing and storing the user. Credential data is never included in user responses. Registration also activates the account and sets its creation timestamp on the server. Email addresses and surrounding profile whitespace are normalized before in-memory persistence, and duplicate email addresses are rejected case-insensitively.

The repository remains in-memory, so all users and their encoded credentials are lost when the application restarts. JWT authentication, token issuance, role-based authorization, and ownership enforcement are implemented for the current API.

### Vehicles

Base path: `/api/vehicles`

| Method | Path                            | Status      | Purpose                          |
|--------|---------------------------------|-------------|----------------------------------|
| GET    | `/api/vehicles`                 | Implemented | List vehicle records.            |
| GET    | `/api/vehicles/{id}`            | Implemented | Retrieve a vehicle record by ID. |
| POST   | `/api/vehicles`                 | Implemented | Create a vehicle for a user using `CreateVehicleRequest`. |
| PUT    | `/api/vehicles/{id}`            | Implemented | Update a vehicle record.         |
| DELETE | `/api/vehicles/{id}`            | Implemented | Delete a vehicle record.         |

### Services

Base path: `/api/services`

| Method | Path                            | Status      | Purpose                          |
|--------|---------------------------------|-------------|----------------------------------|
| GET    | `/api/services`                 | Implemented | List service catalog records.    |
| GET    | `/api/services/{id}`            | Implemented | Retrieve a service by ID.        |
| POST   | `/api/services`                 | Implemented | Create a service catalog record. |
| PUT    | `/api/services/{id}`            | Implemented | Update a service catalog record. |
| DELETE | `/api/services/{id}`            | Implemented | Delete a service catalog record. |
| POST   | `/api/services/{id}/activate`   | Implemented | Mark a service active.           |
| POST   | `/api/services/{id}/deactivate` | Implemented | Mark a service inactive.         |

### Bookings

Base path: `/api/bookings`

| Method | Path                         | Status      | Purpose                                                  |
|--------|------------------------------|-------------|----------------------------------------------------------|
| GET    | `/api/bookings`              | Implemented | List booking records.                                    |
| GET    | `/api/bookings/{id}`         | Implemented | Retrieve a booking by ID.                                |
| POST   | `/api/bookings`              | Implemented | Create a booking.                                        |
| PUT    | `/api/bookings/{id}`         | Implemented | Update a booking.                                        |
| DELETE | `/api/bookings/{id}`         | Implemented | Cancel/delete through the current cancellation workflow. |
| POST   | `/api/bookings/{id}/confirm` | Implemented | Confirm a booking.                                       |
| POST   | `/api/bookings/{id}/cancel`  | Implemented | Cancel a booking.                                        |

### Queue Entries

Base path: `/api/queue-entries`

| Method | Path                                | Status      | Purpose                                       |
|--------|-------------------------------------|-------------|-----------------------------------------------|
| GET    | `/api/queue-entries`                | Implemented | List queue entries.                           |
| GET    | `/api/queue-entries/{id}`           | Implemented | Retrieve a queue entry by ID.                 |
| POST   | `/api/queue-entries`                | Implemented | Create a queue entry.                         |
| PUT    | `/api/queue-entries/{id}/position`  | Implemented | Update queue position.                        |
| POST   | `/api/queue-entries/{id}/call-next` | Implemented | Mark a waiting queue entry as called.         |
| POST   | `/api/queue-entries/{id}/start`     | Implemented | Mark a called queue entry as in progress.     |
| POST   | `/api/queue-entries/{id}/complete`  | Implemented | Mark an in-progress queue entry as completed. |
| DELETE | `/api/queue-entries/{id}`           | Implemented | Delete a queue entry.                         |

### Notifications

Base path: `/api/notifications`

| Method | Path                               | Status      | Purpose                                             |
|--------|------------------------------------|-------------|-----------------------------------------------------|
| GET    | `/api/notifications/user/{userId}` | Implemented | List recent in-app notification records for a user. |

External SMS/email delivery is not implemented.

### Reports

Base path: `/api/reports`

| Method | Path                                           | Status                | Purpose                                                                              |
|--------|------------------------------------------------|-----------------------|--------------------------------------------------------------------------------------|
| GET    | `/api/reports/daily-summary?date={yyyy-MM-dd}` | Implemented | Return a basic daily summary computed from current in-memory booking and queue data. |

## Planned Endpoints Not Yet Implemented

The following API areas are planned/future and should not be treated as current functionality:

- Logout and refresh-token endpoints.
- Additional permission-management endpoints beyond platform-admin role assignment.
- Business registration and tenant-management endpoints.
- PostgreSQL-backed administrative persistence endpoints beyond current CRUD behavior.
- Payment checkout, webhook, refund, or receipt endpoints.
- External notification provider webhook or retry endpoints.
- Ratings and feedback endpoints.
- Rich analytics/dashboard endpoints beyond the basic daily summary.


## Standard Error Contract

Every API error is returned as JSON with the same shape:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-08-05T08:30:00Z",
  "path": "/api/vehicles",
  "fieldErrors": [
    {
      "field": "plateNumber",
      "message": "must not be blank"
    }
  ]
}
```

`timestamp` is an ISO-8601 UTC instant. `fieldErrors` is always present, is sorted by field name, and is
empty for non-validation errors. Responses never contain rejected values, request bodies, Java exception
class names, stack traces, passwords, encoded credentials, JWTs, authorization headers, or signing secrets.

Stable error codes are:

| Code | HTTP status | Meaning |
|------|-------------|---------|
| `VALIDATION_FAILED` | 400 | Bean Validation failed for a request body, path variable, or method parameter. |
| `MALFORMED_REQUEST` | 400 | JSON is missing, syntactically invalid, contains an invalid type/enum/date, or contains an unknown property. |
| `INVALID_PARAMETER` | 400 | A query or path parameter cannot be converted to its declared type. |
| `MISSING_PARAMETER` | 400 | A required request parameter is absent. |
| `BUSINESS_RULE_VIOLATION` | 400 | A safe, deliberate domain rule rejected the operation. |
| `RESOURCE_NOT_FOUND` | 404 | A requested resource or `/api/**` route does not exist. |
| `INVALID_CREDENTIALS` | 401 | Login credentials are invalid. |
| `AUTHENTICATION_REQUIRED` | 401 | A bearer token is missing or invalid. |
| `ACCESS_DENIED` | 403 | The authenticated caller is not authorized. |
| `METHOD_NOT_ALLOWED` | 405 | The HTTP method is not supported for the route. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | The request content type is unsupported. |
| `INTERNAL_ERROR` | 500 | An unexpected server failure occurred; details are logged internally only. |

Unknown JSON properties are rejected. This prevents clients from silently attempting to set
server-controlled fields such as roles, permissions, account status, timestamps, encoded passwords,
activation state, or queue relationships.

### Representative errors

Validation failure:

```json
{"status":400,"code":"VALIDATION_FAILED","message":"Request validation failed","timestamp":"2026-08-05T08:30:00Z","path":"/api/vehicles","fieldErrors":[{"field":"plateNumber","message":"must not be blank"}]}
```

Malformed body:

```json
{"status":400,"code":"MALFORMED_REQUEST","message":"Malformed or invalid request body","timestamp":"2026-08-05T08:30:00Z","path":"/api/services","fieldErrors":[]}
```

Unauthenticated request:

```json
{"status":401,"code":"AUTHENTICATION_REQUIRED","message":"Authentication is required","timestamp":"2026-08-05T08:30:00Z","path":"/api/bookings","fieldErrors":[]}
```

Forbidden request:

```json
{"status":403,"code":"ACCESS_DENIED","message":"Access denied","timestamp":"2026-08-05T08:30:00Z","path":"/api/admin/users/u1/role","fieldErrors":[]}
```

Missing resource:

```json
{"status":404,"code":"RESOURCE_NOT_FOUND","message":"Vehicle not found: v-404","timestamp":"2026-08-05T08:30:00Z","path":"/api/vehicles/v-404","fieldErrors":[]}
```

Unexpected failure:

```json
{"status":500,"code":"INTERNAL_ERROR","message":"An unexpected error occurred","timestamp":"2026-08-05T08:30:00Z","path":"/api/example","fieldErrors":[]}
```

## Export OpenAPI JSON

```bash
curl http://localhost:8080/v3/api-docs -o docs/openapi.json
```

or:

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/v3/api-docs" -OutFile "docs/openapi.json"
```
## Authentication (SEC-002)

`POST /api/auth/login` is public and accepts `email` and `password`. Email is trimmed and normalized
case-insensitively; passwords are never altered. A successful response contains `accessToken`, the
literal token type `Bearer`, `expiresInSeconds`, `expiresAt`, and the bounded `UserResponse`. Invalid
credentials and inactive accounts all return the same `401` message: `Invalid email or password`.
Validation errors return `400` without echoing the supplied password.

Send the token on protected requests as `Authorization: Bearer <accessToken>`. Registration, login,
`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, OPTIONS preflight, and `/error` are public. Every
other `/api/**` operation is protected. Invalid, expired, malformed, incorrectly signed, missing-user,
or inactive-user tokens receive a bounded JSON `401` response.

Tokens contain only `iss`, user ID `sub`, `iat`, `exp`, and `jti`; they expire after the configured access
token TTL. No refresh token exists. Current in-memory storage means tokens cannot survive an application
restart. RBAC, tenant isolation, ownership enforcement, rate limiting, and brute-force protection remain
future work.

## Role-based authorization

Registration, login, OpenAPI/Swagger, OPTIONS, and `/error` are public. Other APIs require a bearer JWT.
Missing/invalid authentication returns JSON 401; authenticated callers lacking role or ownership receive
JSON 403. Registration creates customers only.

| Area                                                | CUSTOMER | STAFF | BUSINESS_OWNER | PLATFORM_ADMIN |
|-----------------------------------------------------|----------|-------|----------------|----------------|
| Own profile, vehicle, booking, queue, notifications | Yes      | Yes   | Yes            | Yes            |
| All vehicles/bookings/queues and operations         | No       | Yes   | Yes            | Yes            |
| Manage service catalogue and read reports           | No       | No    | Yes            | Yes            |
| Manage all users and assign roles                   | No       | No    | No             | Yes            |
| Another user's notifications                        | No       | No    | No             | Yes            |

Role assignment uses `PUT /api/admin/users/{userId}/role` with a built-in role name. Ownership is resolved
from repositories, not trusted request IDs. Role changes invalidate old tokens. Staff/owner access remains
global until tenant isolation is implemented.

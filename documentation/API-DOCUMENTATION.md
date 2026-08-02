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

The current backend exposes the endpoints below. Authentication and RBAC are not enforced by these endpoints yet.

### Users

Base path: `/api/users`

| Method | Path | Status | Purpose |
| --- | --- | --- | --- |
| GET | `/api/users` | Implemented | List user records. |
| GET | `/api/users/{id}` | Implemented | Retrieve a user record by ID. |
| POST | `/api/users` | Implemented | Create a user record. |
| PUT | `/api/users/{id}` | Implemented | Update a user record. |
| DELETE | `/api/users/{id}` | Implemented | Delete a user record. |

User endpoints use bounded API contracts rather than binding or returning the domain model:

- `CreateUserRequest` accepts only `userId`, `fullName`, `email`, `phone`, and `password`.
  Account status, creation/login timestamps, roles, permissions, vehicles, bookings, and notifications are server controlled.
- `UpdateUserRequest` accepts only the editable `fullName`, `email`, and `phone` profile fields. The path parameter is always the authoritative user ID.
- `UserResponse` contains `userId`, `fullName`, `email`, `phone`, `accountStatus`, `createdAt`, `lastLoginAt`, and the nullable scalar `roleName`.
  Credentials and nested role, vehicle, booking, and notification objects are never included.

Registration accepts a raw `password` at the request boundary. The service validates it without trimming and uses BCrypt to create a salted encoded credential before constructing and storing the user. Credential data is never included in user responses. Registration also activates the account and sets its creation timestamp on the server. Email addresses and surrounding profile whitespace are normalized before in-memory persistence, and duplicate email addresses are rejected case-insensitively.

The repository remains in-memory, so all users and their encoded credentials are lost when the application restarts. Authentication, token issuance, and role-based access control (RBAC) remain unimplemented.

### Vehicles

Base path: `/api/vehicles`

| Method | Path | Status | Purpose |
| --- | --- | --- | --- |
| GET | `/api/vehicles` | Implemented | List vehicle records. |
| GET | `/api/vehicles/{id}` | Implemented | Retrieve a vehicle record by ID. |
| POST | `/api/vehicles?userId={userId}` | Implemented | Create a vehicle for a user. |
| PUT | `/api/vehicles/{id}` | Implemented | Update a vehicle record. |
| DELETE | `/api/vehicles/{id}` | Implemented | Delete a vehicle record. |

### Services

Base path: `/api/services`

| Method | Path | Status | Purpose |
| --- | --- | --- | --- |
| GET | `/api/services` | Implemented | List service catalog records. |
| GET | `/api/services/{id}` | Implemented | Retrieve a service by ID. |
| POST | `/api/services` | Implemented | Create a service catalog record. |
| PUT | `/api/services/{id}` | Implemented | Update a service catalog record. |
| DELETE | `/api/services/{id}` | Implemented | Delete a service catalog record. |
| POST | `/api/services/{id}/activate` | Implemented | Mark a service active. |
| POST | `/api/services/{id}/deactivate` | Implemented | Mark a service inactive. |

### Bookings

Base path: `/api/bookings`

| Method | Path | Status | Purpose |
| --- | --- | --- | --- |
| GET | `/api/bookings` | Implemented | List booking records. |
| GET | `/api/bookings/{id}` | Implemented | Retrieve a booking by ID. |
| POST | `/api/bookings` | Implemented | Create a booking. |
| PUT | `/api/bookings/{id}` | Implemented | Update a booking. |
| DELETE | `/api/bookings/{id}?customerId={customerId}` | Implemented | Cancel/delete through the current cancellation workflow. |
| POST | `/api/bookings/{id}/confirm` | Implemented | Confirm a booking. |
| POST | `/api/bookings/{id}/cancel?customerId={customerId}` | Implemented | Cancel a booking. |

### Queue Entries

Base path: `/api/queue-entries`

| Method | Path | Status | Purpose |
| --- | --- | --- | --- |
| GET | `/api/queue-entries` | Implemented | List queue entries. |
| GET | `/api/queue-entries/{id}` | Implemented | Retrieve a queue entry by ID. |
| POST | `/api/queue-entries` | Implemented | Create a queue entry. |
| PUT | `/api/queue-entries/{id}/position` | Implemented | Update queue position. |
| POST | `/api/queue-entries/{id}/call-next` | Implemented | Mark a waiting queue entry as called. |
| POST | `/api/queue-entries/{id}/start` | Implemented | Mark a called queue entry as in progress. |
| POST | `/api/queue-entries/{id}/complete` | Implemented | Mark an in-progress queue entry as completed. |
| DELETE | `/api/queue-entries/{id}` | Implemented | Delete a queue entry. |

### Notifications

Base path: `/api/notifications`

| Method | Path | Status | Purpose |
| --- | --- | --- | --- |
| GET | `/api/notifications/user/{userId}` | Implemented | List recent in-app notification records for a user. |

External SMS/email delivery is not implemented.

### Reports

Base path: `/api/reports`

| Method | Path | Status | Purpose |
| --- | --- | --- | --- |
| GET | `/api/reports/daily-summary?date={yyyy-MM-dd}` | Partially implemented | Return a basic daily summary computed from current in-memory booking and queue data. |

## Planned Endpoints Not Yet Implemented

The following API areas are planned/future and should not be treated as current functionality:

- Authentication/login/logout/token refresh endpoints.
- RBAC or permission-management endpoints.
- Business registration and tenant-management endpoints.
- PostgreSQL-backed administrative persistence endpoints beyond current CRUD behavior.
- Payment checkout, webhook, refund, or receipt endpoints.
- External notification provider webhook or retry endpoints.
- Ratings and feedback endpoints.
- Rich analytics/dashboard endpoints beyond the basic daily summary.

## Export OpenAPI JSON

```bash
curl http://localhost:8080/v3/api-docs -o docs/openapi.json
```

or:

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/v3/api-docs" -OutFile "docs/openapi.json"
```

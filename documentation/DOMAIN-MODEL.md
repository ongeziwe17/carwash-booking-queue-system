# Domain Model

## 1. Overview

The domain model represents the current backend foundation for user records, vehicles, services, bookings, queue entries, roles, and notification records. Some fields support future security or SaaS work, but their presence in domain classes does not mean authentication, RBAC, durable persistence, external notifications, payments, or multi-tenancy are implemented.

## 2. Current Domain Entities

| Entity           | Current Attributes / Responsibilities                                                                                                                                                           | Current Status                                              | Notes                                                                                       |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **User**         | `userId`, `fullName`, `email`, `phone`, internal `encodedPassword`, account status, timestamps, role reference, vehicles, bookings, notifications. Supports profile updates and helper methods. | Implemented as domain record with BCrypt credential storage | JWT login and RBAC are implemented; storage remains in-memory.                              |
| **Role**         | `roleId`, `roleName`, `description`, `permissions`. Supports permission checks in the domain object.                                                                                            | Partially implemented                                       | Role data exists, but RBAC is not enforced at controllers/services through Spring Security. |
| **Vehicle**      | Vehicle identity, plate, type, brand, model, color, notes, user ID. Supports detail updates.                                                                                                    | Implemented                                                 | Service layer associates vehicles with users and checks duplicate plates per owner.         |
| **Service**      | Service identity, name, description, price, estimated duration, active flag, creation timestamp.                                                                                                | Implemented                                                 | Supports catalog CRUD and activate/deactivate workflows.                                    |
| **Booking**      | Booking identity, user, vehicle, service, scheduled date/time, status, special request, queue entry.                                                                                            | Implemented                                                 | Supports create, confirm, cancel, start, and complete status helper behavior.               |
| **QueueEntry**   | Queue identity, booking, service, position, status timestamps, estimated wait.                                                                                                                  | Implemented                                                 | Supports position updates and waiting/called/in-progress/completed transitions.             |
| **Notification** | Notification identity, user, booking, type, message, channel, sent/read timestamps, delivery status.                                                                                            | Implemented as in-app record                                | No external SMS/email delivery provider is implemented.                                     |                                                                                            | Implemented as in-app record                                | No external SMS/email delivery provider is implemented.                                     |

## 3. Implemented Relationships

- Users can own vehicles.
- Bookings reference a user, vehicle, and service.
- Queue entries reference booking and service context.
- Notifications can reference a user and booking.
- Roles can be attached to users as domain data.

## 4. Partially Implemented or Planned Model Areas

- **Identity and access**: `User` and `Role` provide a model foundation, but authentication and RBAC enforcement are planned work.
- **Persistence**: Repository interfaces and in-memory repositories exist. PostgreSQL-backed persistence for the running application is planned to work.
- **Notifications**: Notification records exist. Provider delivery, retries, and webhook reconciliation are future work.
- **Reporting**: Daily summary response data exists. Rich reporting models are future work.
- **SaaS tenancy**: No tenant, business registration, location, subscription, or tenant-isolation model is currently implemented.
- **Payments**: No payment, invoice, receipt, refund, or webhook domain model is currently implemented.
- **Ratings/feedback**: No rating or feedback entity is currently implemented.

## 5. Design Notes

The current model is intentionally small, so backend workflows can be validated before production hardening. Future additions should preserve clear boundaries between domain behavior, service orchestration, repository persistence, and API DTOs.
## Authentication Boundary

Credentials are stored only as BCrypt encodings, and raw passwords are accepted only at registration and
login boundaries. BCrypt inputs are limited to 72 UTF-8 bytes. Authentication issues a short-lived JWT
whose subject is the user ID; domain graphs and credentials are not token claims. JWT validation resolves
the subject against the in-memory repository and requires an `ACTIVE` account. `lastLoginAt` is updated
and saved after successful login. Roles remain domain data only: SEC-003 RBAC and tenant isolation are
not implemented, and there are no refresh tokens or token revocation lists.

## Built-in authorization model

Every user has one server-controlled role: `CUSTOMER`, `STAFF`, `BUSINESS_OWNER`, or `PLATFORM_ADMIN`.
Permissions are derived from the centralized role catalogue, following customer < staff < owner < admin.
Stored vehicle ownership and booking/queue relationships drive customer authorization. Storage remains
in-memory. No tenant relationship exists, so elevated operational access is global and TENANT-001 remains
outstanding.

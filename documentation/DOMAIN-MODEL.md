# Domain Model

## 1. Overview

The application is a Spring Boot modular monolith using in-memory repositories. The current domain covers users, roles, vehicles, services, bookings, queue entries, and in-app notifications. JWT authentication, role-based authorization, bounded API DTOs, and the standard API error contract are implemented. PostgreSQL persistence, tenant isolation, payments, and external notification delivery remain future work.

## 2. Current Entities

| Entity | Main responsibility | Integrity notes |
|---|---|---|
| `User` | Profile, encoded credential, account state, role, and owned aggregate collections | Vehicles, bookings, and notifications are managed through focused add/remove methods. |
| `Role` | Built-in role identity and permission catalogue | Runtime authorization derives permissions from the server-side role catalogue. |
| `Vehicle` | Customer-owned vehicle details | Ownership cannot change through an ordinary update; plate uniqueness is enforced per owner on create and update. |
| `Service` | Global service-catalogue entry | Referenced services must be deactivated rather than physically deleted. |
| `Booking` | Customer, vehicle, service, schedule, status, and optional queue link | Owner is preserved; only `CREATED` and `CONFIRMED` bookings are editable. |
| `QueueEntry` | Booking queue state and position | The canonical entry is attached to its booking; only waiting entries may be repositioned or deleted. |
| `Notification` | In-app notification record for a user and optional booking | User and booking references are resolved to canonical repository objects before insertion. |

## 3. Repository Contract

Repositories distinguish creation from mutation:

```java
boolean insert(T entity);
boolean update(T entity);
Optional<T> findById(ID id);
List<T> findAll();
boolean deleteById(ID id);
boolean existsById(ID id);
```

- `insert` is atomic and returns `false` when the ID already exists.
- `update` returns `false` when the ID does not exist and never creates a record.
- `deleteById` reports whether a record was removed.
- `findAll` returns an immutable, deterministic ID-sorted snapshot.
- In-memory storage uses `ConcurrentHashMap`; callers cannot access the mutable backing map.

Duplicate IDs for users, vehicles, services, bookings, queue entries, and notifications are rejected as `BUSINESS_RULE_VIOLATION` errors without replacing the existing record.

## 4. Single-JVM Coordination Boundary

`InMemoryDataCoordinator` provides one shared `ReentrantReadWriteLock` for the Spring runtime.

- Multi-repository and aggregate mutations run under the write lock.
- Reads that require a consistent aggregate view run under the read lock.
- The last-active-platform-administrator rule uses the same write boundary.
- The lock protects one application process only.
- It is not a database transaction or distributed lock.

DATA-002 must replace this mechanism with PostgreSQL constraints, transactions, and suitable database locking before multi-instance deployment.

## 5. Aggregate Ownership

### User and vehicles

Successful vehicle creation results in:

- one record in `VehicleRepository`;
- the canonical user ID on the vehicle;
- one matching entry in `User.vehicles`.

Vehicle deletion removes both references and is rejected while a booking references the vehicle.

### User and bookings

Successful booking creation resolves the canonical user, vehicle, and service before insertion. The booking is inserted once and added once to `User.bookings`.

A booking may be physically deleted only when it is cancelled and has no queue entry. Deletion removes it from the repository, removes it from the owning user, and removes associated notifications.

### Booking and queue entry

Queue creation resolves the canonical booking and service, inserts the entry, and attaches it to `Booking.queueEntry` inside one write boundary.

Only a waiting queue entry may be physically deleted. Deletion removes the repository record and clears the booking link.

### User and notifications

Notification creation resolves the canonical user and optional booking, inserts the notification, and adds it once to `User.notifications`.

Notifications may be cascade-deleted when their user or cancelled booking is physically removed.

## 6. Deletion Rules

| Resource | Physical deletion rule |
|---|---|
| User | Rejected while vehicles or bookings reference the user; notifications may be removed with the user; the last active platform administrator remains protected. |
| Vehicle | Rejected while any booking references it; successful deletion also removes it from the owner collection. |
| Service | Rejected while any booking or queue entry references it; deactivate referenced services instead. |
| Booking | Allowed only for a cancelled booking with no queue entry. Active and completed history is retained. |
| Queue entry | Allowed only while status is `WAITING`; successful deletion clears the booking link. |
| Notification | Internal cleanup operation only; no broad public delete API is introduced. |

## 7. Lifecycle Integrity

Booking detail updates are allowed only for:

```text
CREATED
CONFIRMED
```

They are rejected for:

```text
IN_SERVICE
CANCELLED
COMPLETED
```

Queue position updates are allowed only while the entry is `WAITING`. Called, in-progress, completed, or exited entries cannot be repositioned or physically deleted.

Queue ordering automation, queue eligibility, and dedicated rescheduling remain separate roadmap work. Booking cancellation already honors the deployment-configurable cancellation window documented in [CONFIGURATION.md](CONFIGURATION.md).

## 8. Mutable Reference Limitation

Repositories still hold mutable Java object references. The supported application path is therefore:

```text
controller -> service -> shared coordinator -> repositories/aggregates
```

Direct mutation outside the service layer is unsupported. Repository list and map results are immutable snapshots, but stored domain objects are not deep-cloned. Durable isolation is deferred to DATA-002.

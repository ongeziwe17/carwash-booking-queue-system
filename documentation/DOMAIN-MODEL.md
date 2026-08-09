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
| `Booking` | Customer, vehicle, service, schedule, status, and optional queue link | Owner is preserved; only `CREATED` and unqueued `CONFIRMED` bookings are editable. |
| `QueueEntry` | Booking queue state, global active position, and estimated wait | Requires a confirmed booking and active matching service; one active entry is allowed per booking and active metrics are server managed. |
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
- Queue repositories additionally expose active operational ordering by position, joined time, and queue-entry ID; public queue lists place active records before terminal history.
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

Queue creation resolves the canonical booking and service inside one write boundary. The booking must be `CONFIRMED`, the matching service must be active, and the queue repository must not contain another active entry for the booking. Active queue states are `WAITING`, `CALLED`, and `IN_PROGRESS`; `COMPLETED` and `EXITED` are terminal/non-active.

After all eligibility checks pass, the service normalizes the new entry to `WAITING`, appends it at active position `N + 1`, supplies its lifecycle timestamp, inserts it, and attaches it to `Booking.queueEntry`. Creation ignores any position on an internal caller-provided object, and the HTTP creation DTO has no position field. The existing single queue reference is retained; queue history and re-entry semantics are not redesigned.

The current single-location runtime has one global active queue across all services. `WAITING`, `CALLED`, and `IN_PROGRESS` entries receive consecutive positions `1..N`. Their wait is the cumulative effective duration of active predecessors; each predecessor uses its positive service duration or the configured default-service-duration fallback. Changing a service's estimated duration rebalances active waits within the same coordinator write operation. `COMPLETED`, `EXITED`, and physically deleted entries do not contribute to active positions or waits. Terminal records may retain their last historical position, which does not block active position reuse, and their wait is zero. Public queue responses return detached snapshots created under the applicable coordinator read or write lock.

Only a waiting queue entry may be physically deleted through the public queue operation. Deletion removes the repository record, clears the booking link, and rebalances the remaining active queue inside the same coordinator write operation. Booking cancellation is a separate cross-aggregate workflow: a valid cancellation may internally remove a `WAITING` or `CALLED` entry, detach it, cancel the booking, and rebalance. It never removes `IN_PROGRESS` work.

Operational lifecycle pairs are synchronized inside the coordinator write boundary:

| Booking | Queue | Meaning |
|---|---|---|
| `CONFIRMED` | `WAITING` | Eligible booking joined the queue. |
| `CONFIRMED` | `CALLED` | Customer was called; service has not started. |
| `IN_SERVICE` | `IN_PROGRESS` | Service started. |
| `COMPLETED` | `COMPLETED` | Service and queue work completed. |

Start and completion resolve the canonical booking from `BookingRepository`, validate both aggregates before mutation, update both repositories, and create the lifecycle notification only after synchronized state exists. Completion then rebalances the remaining active queue. Focused state snapshots restore lifecycle fields and queue metrics if a required in-memory repository update or rebalance fails. `BOOKING_CANCELLED`, `SERVICE_STARTED`, and `SERVICE_COMPLETED` notification persistence is best-effort in this phase: failure is logged after the synchronized lifecycle commits and does not produce a misleading failed workflow response.

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

Queue position updates are allowed only while the entry is `WAITING`. The requested target must be within the current active queue size; movement reorders the full active list and recalculates all affected positions and waits. Called, in-progress, completed, or exited entries cannot be repositioned or physically deleted.

Queue entry eligibility, active uniqueness, global ordering, recalculation, cumulative wait estimates, and booking/queue lifecycle synchronization are implemented. Queue creation and call keep the booking `CONFIRMED`; start and completion synchronize both aggregates. Valid booking cancellation removes `WAITING`/`CALLED` queue work, while in-service cancellation is rejected. Generic booking updates are blocked whenever an active queue entry exists. True server-selected call-next remains QUEUE-003; `/call-next` is still ID-specific. Dedicated booking rescheduling also remains roadmap work. Booking cancellation continues to honor the deployment-configurable cancellation window documented in [CONFIGURATION.md](CONFIGURATION.md).

## 8. Mutable Reference Limitation

Repositories still hold mutable Java object references. The supported application path is therefore:

```text
controller -> service -> shared coordinator -> repositories/aggregates
```

Direct mutation outside the service layer is unsupported. Repository list and map results are immutable snapshots, but stored domain objects are not deep-cloned. Durable isolation is deferred to DATA-002.

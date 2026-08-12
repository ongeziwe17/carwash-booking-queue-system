# Domain Model

## 1. Overview

The application is a Spring Boot modular monolith organized by the identity, access, vehicle, catalog, booking, queue, notification, reporting, and marketplace capabilities. Repository contracts and implementations belong to their owning capabilities, while narrow application queries expose authorization/reporting/Marketplace reads and one shared coordinator protects the in-memory repositories. Identity owns roles, permissions, and the credential contract; Access implements authentication, BCrypt/JWT infrastructure, and authorization. The current domain covers users, roles, vehicles, services, bookings, queue entries, in-app notifications, car wash businesses, and physical branches. PostgreSQL persistence, tenant isolation, branch-aware operations, payments, and external notification delivery remain future work.

## 2. Current Entities

| Entity | Main responsibility | Integrity notes |
|---|---|---|
| `User` | Profile, encoded credential, account state, role, and owned aggregate collections | Vehicles, bookings, and notifications are managed through focused add/remove methods. |
| `Role` | Built-in role identity and permission catalogue | Runtime authorization derives permissions from the server-side role catalogue. |
| `Vehicle` | Customer-owned vehicle details | Ownership cannot change through an ordinary update; plate uniqueness is enforced per owner on create and update. |
| `Service` | Global service-catalogue entry | Referenced services must be deactivated rather than physically deleted. |
| `Booking` | Customer, vehicle, service, schedule, status, and optional queue link | Owner is preserved; only future `CREATED` and unqueued `CONFIRMED` bookings are editable or reschedulable. |
| `QueueEntry` | Booking queue state, global active position, and estimated wait | Requires a confirmed booking and active matching service; one active entry is allowed per booking and active metrics are server managed. |
| `Notification` | In-app notification record for a user and optional booking | User and booking references are resolved to canonical repository objects before insertion. |
| `CarWashBusiness` | Independent Marketplace business identity, contact/onboarding metadata, and active/inactive lifecycle | Identity is immutable; updates preserve registration time and lifecycle state; deactivation retains the record. |
| `CarWashBranch` | Physical business-owned location, address, coordinates, timezone, discovery preference, and lifecycle | `businessId` is immutable, coordinates/timezone are validated, and effective activity requires both branch and owner business to be active. |

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

Duplicate IDs for users, vehicles, services, bookings, queue entries, notifications, businesses, and branches are rejected as `BUSINESS_RULE_VIOLATION` errors without replacing the existing record.

## 4. Single-JVM Coordination Boundary

`InMemoryDataCoordinator` provides one shared `ReentrantReadWriteLock` for the Spring runtime.

- Multi-repository and aggregate mutations run under the write lock.
- Reads that require a consistent aggregate view run under the read lock.
- Cross-module query composition may re-enter the same read lock; write workflows do not perform read-to-write lock upgrades.
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

Focused rescheduling changes only `Booking.scheduledDateTime` and preserves its canonical owner, vehicle, service, and `CREATED`/`CONFIRMED` status. The current booking must still be future work and its configured booking-change cutoff must remain open. Canonical ownership and active-service eligibility plus customer/vehicle conflicts and exact-slot capacity are revalidated under the coordinator write lock before mutation. Active queue work blocks the operation, so successful rescheduling never changes queue order or metrics. A repository update failure restores the original schedule despite mutable in-memory references; the post-commit `BOOKING_RESCHEDULED` notification is best-effort.

### Single-location slot policy and availability

`BookingSlotPolicyService` is the one scheduling decision component for creation, rescheduling, generic service changes, and availability. It enforces strict future starts, the configured global opening/closing window, interval alignment relative to opening, service completion at or before closing, non-cancelled exact-start capacity, same-booking exclusion where applicable, and customer/vehicle conflict checks when those identities are available.

`AvailabilityService` resolves one canonical active service and computes the complete response inside the coordinator read lock. Candidate starts are generated in ascending interval order; past/current starts and full slots are omitted, and response DTOs contain only start, estimated end, and remaining capacity values. Capacity is global across services at an exact start, while service duration is used only for estimated end and closing-time fit—not adjacent-slot overlap. `CANCELLED` bookings release capacity immediately.

Availability does not mutate or reserve domain state. Booking creation remains the authoritative write operation and rechecks capacity plus customer/vehicle rules under the coordinator write lock. Marketplace branches now exist, but booking/availability do not yet consume them; there is still no staff, bay, holiday, overlap, recommendation, or temporary-hold aggregate.

### Booking and queue entry

Queue creation resolves the canonical booking and service inside one write boundary. The booking must be `CONFIRMED`, the matching service must be active, and the queue repository must not contain another active entry for the booking. Active queue states are `WAITING`, `CALLED`, and `IN_PROGRESS`; `COMPLETED` and `EXITED` are terminal/non-active.

After all eligibility checks pass, the service normalizes the new entry to `WAITING`, appends it at active position `N + 1`, supplies its lifecycle timestamp, inserts it, and attaches it to `Booking.queueEntry`. Creation ignores any position on an internal caller-provided object, and the HTTP creation DTO has no position field. The existing single queue reference is retained; queue history and re-entry semantics are not redesigned.

The current single-location runtime has one global active queue across all services. `WAITING`, `CALLED`, and `IN_PROGRESS` entries receive consecutive positions `1..N`. Their wait is the cumulative effective duration of active predecessors; each predecessor uses its positive service duration or the configured default-service-duration fallback. Changing a service's estimated duration rebalances active waits within the same coordinator write operation. `COMPLETED`, `EXITED`, and physically deleted entries do not contribute to active positions or waits. Terminal records may retain their last historical position, which does not block active position reuse, and their wait is zero. Public queue responses return detached snapshots created under the applicable coordinator read or write lock.

True call-next selection uses the repository's same deterministic active order (`position`, then `joinedAt`, then `queueEntryId`) and selects only the first `WAITING` entry. `CALLED` and `IN_PROGRESS` remain active but are skipped, while terminal entries are already excluded. Selection, transition, persistence, notification, and response snapshot creation all occur inside one coordinator write operation. The explicit `/{id}/call` action uses the same transition helper but selects the operator-supplied waiting entry. Calling keeps the booking `CONFIRMED` and does not rebalance positions or ETAs.

Only a waiting queue entry may be physically deleted through the public queue operation. Deletion removes the repository record, clears the booking link, and rebalances the remaining active queue inside the same coordinator write operation. Booking cancellation is a separate cross-aggregate workflow: a valid cancellation may internally remove a `WAITING` or `CALLED` entry, detach it, cancel the booking, and rebalance. It never removes `IN_PROGRESS` work.

Operational lifecycle pairs are synchronized inside the coordinator write boundary:

| Booking | Queue | Meaning |
|---|---|---|
| `CONFIRMED` | `WAITING` | Eligible booking joined the queue. |
| `CONFIRMED` | `CALLED` | Customer was called; service has not started. |
| `IN_SERVICE` | `IN_PROGRESS` | Service started. |
| `COMPLETED` | `COMPLETED` | Service and queue work completed. |

Start and completion resolve the canonical booking from `BookingRepository`, validate both aggregates before mutation, update both repositories, and create the lifecycle notification only after synchronized state exists. Completion then rebalances the remaining active queue. Focused state snapshots restore lifecycle fields and queue metrics if a required in-memory repository update or rebalance fails. `BOOKING_CANCELLED`, `SERVICE_STARTED`, and `SERVICE_COMPLETED` notification persistence is best-effort in this phase: failure is logged after the synchronized lifecycle commits and does not produce a misleading failed workflow response.

Queue calling retains strict `QUEUE_CALLED` notification semantics. A notification failure restores the selected entry's `WAITING` status, prior call timestamp, position, and ETA before the original failure is rethrown, so retry does not advance to a later customer.

### Marketplace business and branches

Each `CarWashBranch` stores exactly one immutable `businessId`; update DTOs and commands intentionally omit both branch identity and business ownership. Business and branch creation/update/lifecycle transitions run under the shared coordinator and use explicit repository `insert`/`update` semantics. No public delete operation is exposed because these records may later be referenced by operational history.

Businesses and branches each have their own `ACTIVE`/`INACTIVE` lifecycle. Deactivating a business does not rewrite or cascade-delete its branches. A branch is effective active only when both records are active, and discoverable only when effective active plus `publicDiscoveryEnabled`. The basic discoverable list performs no distance ranking, service filtering, hours evaluation, or availability calculation.

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
| Marketplace business/branch | No public physical delete operation; use activate/deactivate lifecycle actions. |

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

Queue entry eligibility, active uniqueness, global ordering, recalculation, cumulative wait estimates, true server-selected call-next, focused booking rescheduling, single-location availability, and booking/queue lifecycle synchronization are implemented. Queue creation and call keep the booking `CONFIRMED`; start and completion synchronize both aggregates. Valid booking cancellation removes `WAITING`/`CALLED` queue work, while in-service cancellation is rejected. Generic booking updates and rescheduling are blocked whenever an active queue entry exists. Creation, rescheduling, and service changes reuse the same operating-window/slot policy; cancellation and rescheduling share the deployment-configurable booking-change cutoff documented in [CONFIGURATION.md](CONFIGURATION.md).

## 8. Mutable Reference Limitation

Repositories still hold mutable Java object references. The supported application path is therefore:

```text
controller -> service -> shared coordinator -> repositories/aggregates
```

Direct mutation outside the service layer is unsupported. Repository list and map results are immutable snapshots, but stored domain objects are not deep-cloned. Durable isolation is deferred to DATA-002.

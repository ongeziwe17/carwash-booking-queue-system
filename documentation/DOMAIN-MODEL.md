# Domain Model

## 1. Overview

The application is a Spring Boot modular monolith organized by capability. Persistence-agnostic repository contracts remain in domain packages; each capability owns in-memory and PostgreSQL infrastructure adapters. Narrow immutable application queries expose cross-capability reads, while a shared transaction port selects either the fair single-JVM in-memory coordinator or real PostgreSQL transactions. Identity owns roles, permissions, operational tenant memberships, and the credential contract; Access implements authentication, BCrypt/JWT infrastructure, canonical tenant validation, and authorization. Audit owns immutable security/operational history, redaction, append/query ports, and scoped reads. PostgreSQL and equivalent in-memory tenant isolation/audit semantics are implemented; payments, capacity reservations, SIEM/archive integration, and external notification delivery remain future work.

## 2. Current Entities

| Entity | Main responsibility | Integrity notes |
|---|---|---|
| `User` | Profile, encoded credential, account state, role, and owned aggregate collections | Vehicles, bookings, and notifications are managed through focused add/remove methods. |
| `Role` | Built-in role identity and permission catalogue | Runtime authorization derives permissions from the server-side role catalogue. |
| `AuditRecord` | Immutable actor/action/target/outcome snapshot for a sensitive event | Append-only; exact UTC time; historical scalar IDs; safe reason code; server correlation; bounded allowlisted metadata; no cascading operational foreign keys. |

### Audit catalogue

Actor types are `USER`, `SYSTEM`, and `ANONYMOUS`; outcomes are `SUCCESS`, `DENIED`, and `FAILURE`; sources are `API`, `SECURITY`, and `SYSTEM`. Stable action names cover login/bearer/authorization failure; role and tenant-membership assignment/replacement/removal; business and branch create/update/activate/deactivate; schedule replacement and closure create/cancel; service definition/offering administration; booking create/update/reschedule/cancel/confirm/delete; vehicle create/update/delete; queue create/reposition/call-next/explicit-call/start/complete/remove; platform administration; and authorized audit reads.

`REFUND_REQUESTED`, `REFUND_COMPLETED`, and `REFUND_FAILED` are reserved names only. No payment/refund capability or API exists, so the current system cannot emit them and makes no claim that refunds are audited.
| `TenantMembership` | Identity-owned assignment of one operational user to one canonical Marketplace business | Required for `STAFF`/`BUSINESS_OWNER`, forbidden for `CUSTOMER`/`PLATFORM_ADMIN`, unique by user, and changed only through platform-administrator transactions. |
| `Vehicle` | Customer-owned vehicle details | Ownership cannot change through an ordinary update; plate uniqueness is enforced per owner on create and update. |
| `Service` | Reusable global service/wash-type definition | Booking/queue references and branch offerings prevent physical deletion; deactivate instead. Legacy global price/duration remain transitional for AVAIL-001 and internal compatibility. |
| `ServiceOffering` | One branch's price, estimated duration, configured concurrent capacity, and activation state for one reusable service | Offering/branch/service identity is immutable; one record per branch/service pair; inactive records are reactivated, not recreated or deleted; changed duration/capacity cannot undercut the peak overlap of active bookings. |
| `Booking` | Customer, vehicle, immutable branch, selected branch offering, derived reusable service, schedule, status, and optional queue link | New records cannot be unscoped; offering replacement must stay in the immutable branch; only future `CREATED` and unqueued `CONFIRMED` bookings are editable or reschedulable. |
| `QueueEntry` | Booking queue state, immutable inherited branch/offering, branch position, and estimated wait | Requires a confirmed canonically scoped booking and active associations; one active entry is allowed per booking and active metrics are server managed per branch. |
| `Notification` | In-app notification record for a user and optional booking | Stores scalar branch/offering context; `SENT -> READ` is valid, repeated READ is a timestamp-preserving no-op, and public paths expose immutable snapshots rather than aggregate graphs. |
| `CarWashBusiness` | Independent Marketplace business identity, contact/onboarding metadata, and active/inactive lifecycle | Identity is immutable; updates preserve registration time and lifecycle state; deactivation retains the record. |
| `CarWashBranch` | Physical business-owned location, address, coordinates, timezone, discovery preference, and lifecycle | `businessId` is immutable, coordinates/timezone are validated, and effective activity requires both branch and owner business to be active. |
| `BranchOperatingSchedule` | One branch's complete recurring weekly interval set | Intervals are immutable, bounded, deterministically ordered, and atomically replaced; duplicate and overlapping ranges are invalid. |
| `WeeklyOperatingInterval` | One local-time weekly opening period anchored to a `DayOfWeek` | Uses half-open boundaries, supports overnight continuation, and rejects equal opening/closing times. |
| `TemporaryBranchClosure` | Absolute branch closure with reason and lifecycle history | Uses half-open instant boundaries; active same-branch records may not overlap; cancellation is retained rather than deleted. |

## 3. Repository Contract

Audit is intentionally narrower than the generic CRUD repository contract: `AuditRepository` exposes only `append`, tenant-predicated query, and explicit platform query operations. It has no update or delete method. Tenant queries cannot omit `businessId`, while global reads are a separately named platform operation authorized by explicit administrator scope.

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
- Queue repositories additionally expose branch retrieval and active operational ordering by branch, position, joined time, and queue-entry ID; public queue lists place active records before terminal history.
- In-memory storage uses `ConcurrentHashMap`; callers cannot access the mutable backing map. PostgreSQL adapters map flat module-owned JPA records explicitly and preserve the same insert/update/missing semantics.
- Tenant-owned repositories additionally require `businessId` in lookup/list/mutation predicates. Customer-private repositories use authenticated subject predicates. Explicit platform-administrator paths remain distinct from operator tenant paths.

Duplicate IDs for users, vehicles, services, service offerings, bookings, queue entries, notifications, businesses, branches, and temporary closures are rejected as `BUSINESS_RULE_VIOLATION` errors without replacing the existing record. Offering queries are branch-scoped and offering-ID ordered; the application also rejects a second record for the same branch/service pair. The schedule repository is keyed by `branchId`; PUT explicitly inserts when absent and updates when present rather than silently upserting.

## 4. Transaction and concurrency boundary

Application services depend on `DataTransactionOperations` and `MutationLock`, not a persistence implementation.

- The default in-memory adapter runs multi-repository mutations under one fair write lock and consistent reads under its read lock.
- Cross-module query composition may re-enter the same read lock; write workflows do not perform read-to-write lock upgrades.
- The last-active-platform-administrator rule uses the same write boundary.
- The `postgres` adapter uses REQUIRED database transactions and read-only transactions where applicable. Rollback is authoritative; in-memory-only mutable compensation is not run.
- Transaction-scoped PostgreSQL advisory locks serialize offering capacity, customer/vehicle conflicts, branch queue ordering, schedules, and last-admin decisions across application instances. Keys use the documented global order.
- Optimistic versions detect ordinary lost updates. Database constraints enforce canonical ownership/scope, uniqueness, status, precision, and deletion restrictions.
- Optional notifications run after commit in REQUIRES_NEW; required lifecycle notifications remain atomic with their parent workflow.

## 5. Aggregate Ownership

### User and vehicles

Successful vehicle creation results in:

- one record in `VehicleRepository`;
- the canonical user ID on the vehicle;
- one matching entry in `User.vehicles`.

Vehicle deletion removes both references and is rejected while a booking references the vehicle.

### User and bookings

Successful booking creation resolves the canonical user, vehicle, Marketplace branch, Catalog offering, and reusable service definition before insertion. `branchId` and `serviceOfferingId` are canonical; the retained `Service` projection is derived from the offering's `serviceId`, never trusted from the client. The branch and owning business plus offering and reusable service must be effective active, while `publicDiscoveryEnabled` is intentionally irrelevant to operational validity. The booking is inserted once and added once to `User.bookings`.

A booking may be physically deleted only when it is cancelled and has no queue entry. Deletion removes it from the repository, removes it from the owning user, and removes associated notifications.

Focused rescheduling changes only `Booking.scheduledDateTime` and preserves canonical owner, vehicle, branch, offering, derived service, and `CREATED`/`CONFIRMED` status. Generic update may select another effective offering only when it belongs to the same immutable branch. Canonical ownership, lifecycle, conflicts, and overlapping capacity are revalidated inside the selected transaction boundary. PostgreSQL acquires old/new offering locks in stable order; active queue work blocks changes. Mutable compensation remains only for in-memory references, while PostgreSQL rolls back. The post-commit `BOOKING_RESCHEDULED` notification is best-effort.

### Legacy and branch-aware availability

`BookingSlotPolicyService` retains AVAIL-001's legacy date/slot generation and the booking-specific customer/vehicle conflict rule. `BranchAvailabilityDecisionService` is the canonical AVAIL-002 policy used by new booking creation, same-branch offering changes, rescheduling, branch availability search, and future recommendation consumers. It resolves the branch and offering, requires the resolved branch-local start to have exactly one valid timezone offset, converts branch-local booking input without accepting DST gaps or ambiguous offsets, aligns the configurable whole-minute slot grid from Marketplace's applicable continuous-window opening, asks Marketplace to evaluate the complete service window, and calculates offering-scoped overlapping capacity.

`AvailabilityService` resolves one canonical active service and computes the complete response inside the coordinator read lock. Candidate starts are generated in ascending interval order; past/current starts and full slots are omitted, and response DTOs contain only start, estimated end, and remaining capacity values. Capacity is global across services at an exact start, while service duration is used only for estimated end and closing-time fit—not adjacent-slot overlap. `CANCELLED` bookings release capacity immediately.

Availability does not mutate or reserve domain state. Booking creation remains authoritative and re-evaluates the same branch decision plus customer/vehicle conflicts under the coordinator write lock. Occupancy includes `CREATED`, `CONFIRMED`, and `IN_SERVICE` bookings whose half-open service windows overlap the candidate at the same branch/offering; `CANCELLED` and `COMPLETED` bookings do not consume configured capacity. AVAIL-001 remains backward-compatible and single-location/global; there is still no staff, bay, holiday, personalization, prediction, or temporary-hold aggregate.

Branch-aware search requires an explicit offset-aware start instant. Each branch resolves that instant in its own timezone; both occurrences are excluded when the resolved local start is ambiguous during a fall-back overlap, because the current booking command accepts only a local date-time. This guarantees every advertised result is representable by and revalidates through the authoritative booking contract. Service duration advances the absolute instant and Marketplace verifies continuous hours plus any active closure intersection across the complete window. Exactly adjacent intervals are one window with one slot anchor, split intervals reset the anchor, and overnight windows keep the opening on their originating local date. Results require an effective/public branch and discoverable effective offering, are ordered by raw distance then branch ID when an origin is present (otherwise branch ID), round response distance to two decimals with `HALF_UP`, and expose a branch-only active-queue duration sum only when the requested date is the branch's current local date.

### Explainable recommendations

`BranchAvailabilityCandidateQuery` is a Booking-owned detached projection over the same AVAIL-002 search path. It supplies effective eligible branch/offering identity, business/branch/service names, branch timezone, available start/end, offering price/duration/capacity, queue context, and the raw Haversine distance. The existing public availability response remains rounded and unchanged. Recommendation owns no repository, aggregate, availability reason, or mutable collection.

Every preference scores the identical candidate list. Distance, queue wait, total time (`queue wait + service duration`), and price are independent `RecommendationMetricProvider` values. Known lower-is-better values normalize as `(max - value) / (max - min)` over the eligible set; equal known values score `1`. Future branch-local dates keep queue and total time null, assign those components `0`, and remain eligible. `NEAREST`, `SHORTEST_QUEUE`, `FASTEST_TOTAL_TIME`, and `LOWEST_PRICE` rank by the raw objective; `BEST_OVERALL` ranks by the DECIMAL128 weighted sum. All ties finish with `branchId`, then `serviceOfferingId`.

Responses round distance/price to their public scales and scores/contributions to six decimals. BEST_OVERALL first sums the unrounded DECIMAL128 contributions, bounds the result to `[0, 1]`, and rounds the final score once. Display contributions use largest-remainder reconciliation without leaving those bounds; equal fractional remainders use the fixed order distance, queue wait, total time, then price. The four displayed contributions therefore sum exactly to the displayed overall score while internal ranking remains unchanged. Deterministic customer-safe templates explain the applied metric and explicitly describe unavailable queue/total data. Recommendations are non-reserving point-in-time reads; booking creation may reject a stale result and always revalidates branch, offering, local time, capacity, ownership, and vehicle conflicts under the write boundary.

### Booking and queue entry

Queue creation resolves the canonical booking and inherits its immutable branch/offering inside one write boundary. It revalidates the branch/business, offering/service state, branch/offering relationship, and retained derived service. The request's legacy `serviceId` is only a consistency field and mismatches are rejected. The booking must be `CONFIRMED`, and the queue repository must not contain another active entry for it. Active queue states are `WAITING`, `CALLED`, and `IN_PROGRESS`; `COMPLETED` and `EXITED` are terminal/non-active.

After all eligibility checks pass, the service normalizes the new entry to `WAITING`, appends it at branch-active position `N + 1`, supplies its lifecycle timestamp, inserts it, and attaches it to `Booking.queueEntry`. Creation ignores any position on an internal caller-provided object, and the HTTP creation DTO has no branch, offering, or position field. The existing single queue reference is retained; queue history and re-entry semantics are not redesigned.

Each branch has an independent active queue. Within a branch, `WAITING`, `CALLED`, and `IN_PROGRESS` entries receive consecutive positions `1..N`; entries in another branch never affect them. Wait is the cumulative configured offering duration of active predecessors. Legacy service duration/default fallback remains only for internal legacy test objects without offering identity. Global service duration changes therefore do not rewrite offering-derived waits. `COMPLETED`, `EXITED`, and physically deleted entries do not contribute to active positions or waits. Terminal records may retain their historical position, and their wait is zero. Public queue responses are detached; `QueueQuery` publishes immutable scalar snapshots.

True call-next requires `branchId`, uses that branch's deterministic active order (`position`, then `joinedAt`, then `queueEntryId`), and selects only its first `WAITING` entry. It revalidates canonical operational associations before transition. `CALLED` and `IN_PROGRESS` remain active but are skipped, while terminal entries are excluded. Selection, transition, persistence, notification, and response snapshot creation occur inside one coordinator write operation. The explicit `/{id}/call` action uses the same transition helper for the operator-supplied entry. Calling keeps the booking `CONFIRMED` and does not rebalance positions or ETAs.

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

Businesses and branches each have their own `ACTIVE`/`INACTIVE` lifecycle. Deactivating a business does not rewrite or cascade-delete its branches. A branch is effective active only when both records are active, and discoverable only when effective active plus `publicDiscoveryEnabled`. The basic discoverable list remains unchanged and performs no distance ranking, service filtering, hours evaluation, or availability calculation; GEO-001 adds a separate bounded nearby projection.

### Nearby branch discovery

Discovery is an orchestration capability, not a Marketplace or Catalog aggregate. `GeoCoordinate` validates finite latitude `[-90, 90]` and longitude `[-180, 180]`; `DistanceCalculator` isolates the distance policy from its Haversine adapter. The adapter uses the IUGG mean Earth radius `6371.0088 km`, normalizes longitude deltas for the antimeridian, and clamps the Haversine intermediate to `[0, 1]` for stable polar and nearly antipodal results.

`NearbyBranchDiscoveryService` starts from detached effective/public `BranchSnapshot` values, defensively skips an invalid stored coordinate, and optionally composes `ServiceOfferingQuery` and `BranchScheduleQuery`. Service filtering requires a canonical active reusable service and an effective discoverable offering owned by that branch. Open filtering is applied only when an explicit instant is supplied and delegates timezone, weekly-hours, overnight, and temporary-closure decisions to Marketplace scheduling. Radius inclusion and distance sorting use the raw calculated distance; only the response value is rounded to two decimal kilometres with `HALF_UP`. Default ordering is `branchId`; distance ties also use `branchId`.

### Marketplace branch scheduling

`BranchOperatingSchedule` stores zero or more local recurring intervals anchored to `DayOfWeek`. An interval is open at its start and closed at its end. An end earlier than the start means the interval continues into the following day; Sunday-to-Monday wrapping uses the same rule. Validation projects intervals onto a cyclic seven-day timeline, splits the weekly wrap where necessary, and rejects every true intersection while allowing exact adjacency.

`TemporaryBranchClosure` stores `startAt`/`endAt` as absolute instants even though the HTTP request is offset-aware. Active closures use `[startAt, endAt)` and override weekly hours; cancelled records remain queryable but do not affect decisions. Creation and overlap validation run under the single coordinator write lock, so an active overlap cannot pass concurrently in this single-JVM runtime.

`BranchSchedulingService` converts required instants into the branch's current `ZoneId` before applying weekly recurrence. `BranchScheduleQuery` publishes both an instant decision and a complete half-open service-window decision; the latter includes the immutable branch-local start of the applicable continuous operating window. Window evaluation unions adjacent weekly intervals, supports overnight/DST transitions deterministically, requires continuous coverage, and rejects any active closure intersection. Public discovery preference remains independent of operational open state; AVAIL-002 applies discovery separately.

### User and notifications

Notification creation resolves the canonical user and optional booking, inserts the notification, and adds it once to `User.notifications`.

Inbox queries use immutable `NotificationSnapshot` values with only notification, recipient, booking, branch,
offering, type, bounded message, channel, timestamp, and status scalars. Purpose-specific repositories perform
newest-first keyset queries, unread counts, recipient-guarded mark-one, and atomic recipient mark-all without hydrating
User or Booking aggregates. The key is exact `sentAt` followed by `notificationId`; PostgreSQL reconstructs the Java
nanoseconds from its timestamp plus remainder before producing or applying a cursor.

`SENT -> READ` assigns the application-clock time. `READ -> READ` retains the first time. Other transitions fail and
the V6 database constraint plus equivalent in-memory validation prohibit disagreement between `deliveryStatus` and
`readAt`. Concurrent mark-one and mark-all operations use guarded updates; the first row transition is authoritative.

Notifications may be deleted only through existing dependency cleanup when their user or an eligible cancelled
booking is physically removed. There is no public delete/purge API, scheduler, time-based expiry, or deletion of unread
records. External SMS, email, push, and marketing delivery remain separate capabilities.

## 6. Deletion Rules

Audit history is never deleted through a domain/application operation. Operational aggregate deletion cannot cascade into `audit_records`; actor, business, role, target, and source identifiers remain historical snapshots. Retention is a separately approved database-operations procedure.

| Resource | Physical deletion rule |
|---|---|
| User | Rejected while vehicles or bookings reference the user; notifications may be removed with the user; the last active platform administrator remains protected. |
| Vehicle | Rejected while any booking references it; successful deletion also removes it from the owner collection. |
| Service | Rejected while any booking, queue entry, or active/inactive offering references it; deactivate referenced services instead. |
| Booking | Allowed only for a cancelled booking with no queue entry. Active and completed history is retained. |
| Queue entry | Allowed only while status is `WAITING`; successful deletion clears the booking link. |
| Notification | Internal cleanup operation only; no broad public delete API is introduced. |
| Marketplace business/branch | No public physical delete operation; use activate/deactivate lifecycle actions. |
| Branch schedule | No delete operation; replace with an empty complete schedule to make every day closed. |
| Temporary closure | No physical delete operation; use cancellation and retain history. |

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

Queue position updates are allowed only while the entry is `WAITING`. The requested target must be within its branch's active queue size; movement reorders and recalculates only that branch. Called, in-progress, completed, or exited entries cannot be repositioned or physically deleted.

Queue entry eligibility, active uniqueness, branch-partitioned ordering/recalculation/cumulative waits/call-next, focused booking rescheduling, legacy and branch-aware availability, Marketplace scheduling, and booking/queue lifecycle synchronization are implemented. Canonical queue scope integrity is distinct from operational activity: creation, call, and start require both, while completion of already `IN_PROGRESS` work requires canonical identity/referential integrity but remains possible after parent deactivation. Queue creation and call keep the booking `CONFIRMED`; start and completion synchronize both aggregates. Valid booking cancellation removes `WAITING`/`CALLED` queue work and rebalances only the booking branch, while in-service cancellation is rejected. Generic booking updates and rescheduling are blocked whenever an active queue entry exists. Operational creation/rescheduling/offering changes reuse the shared branch availability decision; cancellation and rescheduling retain the deployment-configurable booking-change cutoff.

### Reusable services and branch offerings

Catalog owns both the reusable `Service` definition and immutable `ServiceOffering` aggregate. Each offering stores immutable `offeringId`, `branchId`, and `serviceId`, plus its own non-negative two-decimal price, `1..1440` minute estimate, `1..1000` configured concurrent capacity, active/inactive status, and creation/update metadata. Only one record may exist for a branch/service pair; deactivation/reactivation preserves that relationship.

Catalog application code resolves the branch exclusively through the published `MarketplaceQuery` and resolves the reusable service inside Catalog. Its detached projection derives:

```text
effectiveActive = offering.active AND service.active AND branch.effectiveActive
discoverable    = effectiveActive AND branch.publicDiscoveryEnabled
```

Parent changes never rewrite offering status. The customer discovery DTO omits lifecycle/metadata and internal object graphs, while the published `ServiceOfferingQuery` retains bounded immutable state and configured capacity for future branch-aware consumers. Operating hours and temporary closures are deliberately not part of offering discovery. `concurrentCapacity` is configured capacity—not remaining capacity—and no booking, queue, staff, or bay data is inspected.

Existing global `Service.price` and `Service.estimatedDurationMin` remain transitional for the unchanged global Service API and AVAIL-001. New bookings derive reusable identity from the offering, validate slot fit with offering duration, and never present global price/duration as offering terms; queue waits also use offering duration. The retained internal Service projection is a compatibility bridge for older lifecycle/serialization code and is not client authority. Any offering, including an inactive one, blocks physical deletion of its reusable service definition.

## 8. Mutable Reference Limitation

Repositories still hold mutable Java object references. The supported application path is therefore:

```text
controller -> service -> shared coordinator -> repositories/aggregates
```

Direct mutation outside the service layer is unsupported. In-memory repository list and map results are immutable collections, but stored domain objects are not deep-cloned. PostgreSQL adapters reconstruct detached bounded graphs and never expose JPA entities, proxies, or lazy collections. Cross-module consumers use immutable snapshots; public booking/queue reads remain detached.

# Marketplace Tenant Isolation

## Trust boundary

`CarWashBusiness.businessId` is the canonical tenant identifier. Identity owns a single active `TenantMembership` for each operational user. `STAFF` and `BUSINESS_OWNER` must have exactly one membership; `CUSTOMER` and `PLATFORM_ADMIN` must have none. No tenant value is accepted from an HTTP body, query parameter, header, or permission claim for authorization decisions.

The access module resolves a validated JWT once into `TenantAccessContext(userId, role, businessId)`. Operational tokens contain `tenant_id`; customer and platform-administrator tokens do not. Every authenticated request revalidates the token subject against canonical user status, canonical `RoleCatalog` role, and current membership. A missing, forged, removed, or reassigned tenant claim, a stale role, an inactive account, or a deleted subject is invalid authentication and returns `401`.

Platform administrators never receive a wildcard tenant. Cross-tenant work is exposed only through explicit administrator application paths that require a concrete business or resource scope. The reusable `Service` catalogue is platform-wide and only `PLATFORM_ADMIN` may mutate it. A business owner may manage `ServiceOffering` records only for branches owned by that owner's tenant.

## Membership lifecycle and secure onboarding

Only a platform administrator can create, replace, or remove an operational membership:

| Operation | Contract | Atomic result |
| --- | --- | --- |
| Promote or reassign role | `PUT /api/admin/users/{userId}/role` with `roleName` and, for an operational role, `businessId` | Role and required membership change in one transaction. Non-operational roles reject `businessId` and remove any existing membership. |
| Assign or replace membership | `PUT /api/admin/users/{userId}/tenant-membership` with `businessId` | Existing `STAFF` or `BUSINESS_OWNER` membership changes to the named existing business. |
| Remove membership | `DELETE /api/admin/users/{userId}/tenant-membership` | Membership is removed and the user is demoted to `CUSTOMER` in one transaction. |

After any role or membership change, previously issued tokens are stale and fail with `401`. The operator must authenticate again. A business owner cannot register or claim another business; business creation is an explicit platform-administrator operation.

V4 deliberately does not infer assignments for existing data. Upgrade procedure:

1. Inventory every active `STAFF` and `BUSINESS_OWNER` and verify the intended `business_id` out of band.
2. Deploy V4 while legacy unassigned operational identities remain fail-closed; they cannot log in or use old tokens.
3. As a platform administrator, call the role-assignment operation with the verified `businessId` for each user. Do not bulk-derive membership from bookings, branches, names, emails, or registration data.
4. Have each assigned operator authenticate again and verify a same-tenant read plus a foreign-tenant safe `404` check.
5. Treat any ambiguous identity as unassigned until ownership is resolved.

## Application and repository enforcement

Controllers pass `TenantAccessContext` to application services. Services choose one of three bounded paths:

| Identity | Allowed scope |
| --- | --- |
| `CUSTOMER` | Subject-scoped private data such as booking, queue, vehicle, and notification by both resource ID and authenticated user ID; authenticated discovery-safe reads. |
| `STAFF` | Operational booking, queue, notification, and permitted Marketplace reads by resource/branch plus the membership `businessId`. |
| `BUSINESS_OWNER` | The same tenant boundary plus tenant management, schedules, closures, offerings, and reports; no global service-catalogue mutation and no second business registration. |
| `PLATFORM_ADMIN` | Explicit administrator operations with a concrete business/resource scope; no implicit wildcard tenant. |

Tenant-owned repositories expose predicates such as `findByIdAndBusinessId`, `findByBranchIdAndBusinessId`, and `findByBusinessId`. PostgreSQL adapters include the tenant predicate in SQL or Spring Data queries. Application services do not fetch a caller-supplied ID globally and authorize the returned aggregate afterward. Reporting consumes business/branch-scoped booking and queue queries; it does not load all records and filter in memory.

The boundary covers businesses, branches, schedules, closures, offerings, booking lifecycle, queue lifecycle/call-next/order, daily reports, operational notifications, vehicles reached by operational workflows, and staff/owner membership queries. Customer self-service remains subject-scoped.

### Authoritative mutation boundary

Every externally reachable tenant- or subject-owned mutation now enters one `DataTransactionOperations.write(...)` boundary before authorization. Inside that boundary the service normalizes the resource ID, acquires its logical mutation lock, performs the canonical scoped read, mutates that exact aggregate, and persists it through a guarded repository operation. Tenant operators use resource ID plus authenticated `businessId`; customers use resource ID plus authenticated `userId`; platform administrators use a separate explicit administrator operation. There is no wildcard tenant and a failed scoped operation never retries through `findById`.

Queue-entry commands may perform an unscoped scalar lookup solely to discover the immutable booking lock key. That lookup is not an authorization decision and its value is never returned: the booking and queue-entry locks are acquired together and the queue entry is then loaded canonically with the tenant/subject predicate before mutation. A foreign resource-ID replacement between discovery and the canonical read therefore returns safe `404`.

The corrected boundary applies to business details/status; branch create/details/status; operating schedules; temporary closures; offering create/terms/status; booking create/update/reschedule/confirm/cancel/delete; queue join/reposition/call-next/explicit-call/start/complete/delete/rebalance; vehicle customer/tenant update/delete; and notification create/delete reached by those workflows. `BookingNotificationPublisher` is the narrow trusted cross-module contract for notifications: it accepts the already-authorized canonical booking and must not reload it by unscoped ID.

Repository mutation rules are fail-closed:

- tenant writes and deletes retain the authenticated `businessId` predicate;
- customer writes and deletes retain the authenticated `userId` predicate;
- administrator writes and deletes use explicitly named administrator operations;
- optimistic versions remain part of PostgreSQL entity updates after the scoped canonical load;
- zero affected rows produce `404`, roll back the transaction, and never trigger an unscoped retry; and
- read-only reports and notification queries retain their existing tenant predicates.

The in-memory profile performs the same sequence under its shared fair write lock and evaluates guarded predicates atomically in `updateMatching`/`deleteMatching`. PostgreSQL uses one REQUIRED transaction, transaction-scoped advisory locks, scoped repository queries, optimistic versions, and database rollback. Flyway V1–V4 remain unchanged; additive V5 introduces audit history only and does not alter the PR #182 tenant-locking/guarded-write schema.

## Safe error policy

| Status | Meaning |
| --- | --- |
| `401` | Authentication is absent, invalid, forged, stale, inactive, or inconsistent with canonical role/membership state. |
| `403` | The authenticated canonical role lacks permission for the operation. |
| `404` | A tenant-scoped resource is absent or belongs to another tenant for an otherwise-authorized operator. |

Foreign and missing tenant-owned IDs use the same status, error code, and generic resource message. Responses never disclose the owning business, tenant ID, business name, or whether the foreign resource exists.

## Discovery allowlist

"Public" means customer-safe projection, not anonymous access. Existing discovery endpoints remain authenticated.

| Projection | Allowed JSON fields |
| --- | --- |
| `DiscoverableBranchResponse` | `branchId`, `businessId`, `branchName`, `addressLine1`, `addressLine2`, `city`, `province`, `postalCode`, `countryCode`, `latitude`, `longitude`, `timezone` |
| `DiscoverableServiceOfferingResponse` | `offeringId`, `branchId`, `serviceId`, `serviceName`, `serviceDescription`, `price`, `estimatedDurationMin`, `concurrentCapacity` |
| Nearby branch discovery | The branch discovery fields required for location/booking decisions plus computed `distanceKm`; no management lifecycle metadata. |
| Availability and recommendation | Bounded branch/offering identity and display names, bookable time/duration/capacity/price, relevant distance/queue estimates, and explanation/score fields only. |

Discovery never exposes business contact data, registration numbers, staff/membership data, audit data, private notifications, persistence versions, internal lifecycle metadata, or internal timestamps. `BusinessResponse`, full `BranchResponse`, and full `ServiceOfferingResponse` remain management-only DTOs.

## PostgreSQL V4 enforcement

`V4__tenant_isolation.sql` is additive; V1-V3 remain byte-for-byte immutable. It adds:

- `tenant_memberships`, keyed by `user_id`, with foreign keys to users/businesses and an index on `(business_id, user_id)`;
- deferred role/membership constraint triggers so operational roles finish a transaction with exactly one membership and non-operational roles finish with none;
- composite unique and foreign-key scopes across branch, offering, booking, queue, and notification relationships;
- notification completeness checks so operational notification scope is all-null or canonically complete;
- tenant filtering indexes for business-to-branch, branch-to-offering, branch booking/status/time, branch queue/status/position, and branch notification/time paths;
- removal of global `SERVICE_MANAGE` from the `BUSINESS_OWNER` role.

PostgreSQL 17.6 Testcontainers covers clean V4 migration, incremental V1-V3 upgrade, checksum validation, unassigned legacy operators, uniqueness, deferred constraint rollback, scoped repository reads, and cross-tenant relational mismatch rejection. The same application contracts are exercised against in-memory adapters.

## Audit boundary

## Audit trust boundary

Issue #125 is implemented by the dedicated Audit capability. Other capabilities publish scalar commands only; they never pass mutable aggregates, JWT objects, requests, or persistence entities. Actor membership and event tenant are distinct trusted snapshots. Operational actors retain their authenticated membership tenant; customers and platform administrators retain a null actor-membership tenant. For a successful tenant-owned booking mutation, the event `businessId` is derived from the canonically locked booking/branch, so customer and administrator activity is visible in the target business history without inventing actor membership. A cross-tenant safe-`404` attempt stores the actor-safe scope plus the requested target ID and never performs an unscoped lookup to discover the victim tenant.

The mandatory SUCCESS insert and protected mutation share the authoritative write transaction. Booking success-scope resolution acquires the same deterministic booking, offering, vehicle/customer, queue-branch, branch, and business lock set before the append; the protected mutation then reuses those transaction-scoped locks and its PR #182 guarded predicates. The append remains before the first business mutation. DENIED/FAILURE writes run only after rollback through an isolated transaction and use the pre-resolution actor-safe command. Tenant audit reads call `queryByBusinessId`, whose PostgreSQL statement always contains the tenant predicate; the separate platform query is reachable only after explicit `scope=PLATFORM` authorization. Foreign and missing targets remain indistinguishable.

Billing, database-per-tenant isolation, federation, SIEM, archives, compliance certification, and payment/refund implementation remain out of scope.

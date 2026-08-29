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

Issue #125 remains out of scope: this change introduces no audit-record aggregate, table, repository, or query. A later audit implementation must store `businessId` on every tenant-owned audit record and require a tenant predicate on every operator-facing audit query. Billing, database-per-tenant isolation, and federation are also out of scope.

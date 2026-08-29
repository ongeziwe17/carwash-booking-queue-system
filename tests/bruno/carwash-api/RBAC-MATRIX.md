# RBAC Matrix

This matrix is derived from `RoleCatalog`, controller `@PreAuthorize` expressions, and `ResourceAuthorizationService` on the current `staging` source. The Bruno requests in `10-rbac/` and `11-ownership-security/` exercise the meaningful allow/deny cells with real tokens obtained from `/api/auth/login`.

| Capability | CUSTOMER | STAFF | BUSINESS_OWNER | PLATFORM_ADMIN |
|---|:---:|:---:|:---:|:---:|
| Own profile read/update/delete | ✅ | ✅ | ✅ | ✅ |
| Another user's profile | ❌ | ❌ | ❌ | ✅ |
| List all users | ❌ | ❌ | ❌ | ✅ |
| Create/manage own vehicle | ✅ | ✅ | ✅ | ✅ |
| Operate/list another user's vehicles | ❌ | ✅ assigned tenant | ✅ assigned tenant | ✅ explicit admin path |
| Read service catalogue | ✅ | ✅ | ✅ | ✅ |
| Read branch-aware availability | ✅ | ✅ | ✅ | ✅ |
| Manage global service catalogue | ❌ | ❌ | ❌ | ✅ |
| Create/manage own booking | ✅ | ✅ | ✅ | ✅ |
| Operate/list another user's bookings | ❌ | ✅ assigned tenant | ✅ assigned tenant | ✅ explicit admin path |
| Confirm bookings | ❌ | ✅ | ✅ | ✅ |
| Read own queue entry | ✅ | ✅ | ✅ | ✅ |
| Operate/list queue entries | ❌ | ✅ assigned tenant | ✅ assigned tenant | ✅ explicit admin path |
| Read own notifications | ✅ | ✅ | ✅ | ✅ |
| Read another user's operational notifications | ❌ | ✅ assigned tenant | ✅ assigned tenant | ✅ explicit admin path |
| Read daily reports | ❌ | ❌ | ✅ assigned tenant | ✅ explicit scope |
| Read discoverable Marketplace branches | ✅ | ✅ | ✅ | ✅ |
| Read nearby Marketplace branch discovery | ✅ | ✅ | ✅ | ✅ |
| Read Marketplace branch recommendations | ✅ | ✅ | ✅ | ✅ |
| Read Marketplace branch open status | ✅ | ✅ | ✅ | ✅ |
| Read discoverable Marketplace offerings | ✅ | ✅ | ✅ | ✅ |
| Manage Marketplace businesses/branches/hours/closures/offerings | ❌ | ❌ | ✅ assigned tenant | ✅ explicit admin path |
| Register a Marketplace business | ❌ | ❌ | ❌ | ✅ |
| Assign roles or tenant memberships | ❌ | ❌ | ❌ | ✅ |

## Permission catalogue

- **CUSTOMER:** `USER_SELF_MANAGE`, `VEHICLE_SELF_MANAGE`, `SERVICE_READ`, `BOOKING_SELF_MANAGE`, `QUEUE_SELF_READ`, `NOTIFICATION_SELF_READ`, `MARKETPLACE_READ`
- **STAFF:** all CUSTOMER permissions plus `VEHICLE_OPERATE`, `BOOKING_OPERATE`, `QUEUE_OPERATE`
- **BUSINESS_OWNER:** all STAFF permissions plus `REPORT_READ` and `MARKETPLACE_MANAGE`; no global `SERVICE_MANAGE`
- **PLATFORM_ADMIN:** all current permissions, including `USER_ADMIN` and `ROLE_ASSIGN`

`STAFF` and `BUSINESS_OWNER` are operational tenant roles and require exactly one canonical membership. Their vehicle, booking, queue, notification, report, and Marketplace operations use that membership. `PLATFORM_ADMIN` has no membership or wildcard tenant and uses explicit administrator paths/scopes. Customers remain subject-scoped.

Branch filters, branch call-next, and branch/business report scopes are validated against the operator's tenant and carried into repository predicates. A foreign valid ID and missing ID both return a generic `404`; a role lacking the permission receives `403`.

Marketplace management is tenant-scoped for owners and explicitly scoped for platform administrators. `MARKETPLACE_READ` exposes bounded branch/offering/nearby discovery, recommendations, and operational open-status decisions; the public-discovery flag does not make an endpoint anonymous. Discovery uses customer-safe DTO allowlists. Recommendations use the least-privilege `MARKETPLACE_READ` convention; a token without it receives `403`.

Operating-window slot alignment, ambiguous-local-time exclusion, capacity, booking, and queue invariants remain unchanged inside the tenant boundary.

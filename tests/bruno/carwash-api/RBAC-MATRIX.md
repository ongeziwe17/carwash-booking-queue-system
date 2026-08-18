# RBAC Matrix

This matrix is derived from `RoleCatalog`, controller `@PreAuthorize` expressions, and `ResourceAuthorizationService` on the current `staging` source. The Bruno requests in `10-rbac/` and `11-ownership-security/` exercise the meaningful allow/deny cells with real tokens obtained from `/api/auth/login`.

| Capability | CUSTOMER | STAFF | BUSINESS_OWNER | PLATFORM_ADMIN |
|---|:---:|:---:|:---:|:---:|
| Own profile read/update/delete | ✅ | ✅ | ✅ | ✅ |
| Another user's profile | ❌ | ❌ | ❌ | ✅ |
| List all users | ❌ | ❌ | ❌ | ✅ |
| Create/manage own vehicle | ✅ | ✅ | ✅ | ✅ |
| Operate/list another user's vehicles | ❌ | ✅ | ✅ | ✅ |
| Read service catalogue | ✅ | ✅ | ✅ | ✅ |
| Manage service catalogue | ❌ | ❌ | ✅ | ✅ |
| Create/manage own booking | ✅ | ✅ | ✅ | ✅ |
| Operate/list another user's bookings | ❌ | ✅ | ✅ | ✅ |
| Confirm bookings | ❌ | ✅ | ✅ | ✅ |
| Read own queue entry | ✅ | ✅ | ✅ | ✅ |
| Operate/list queue entries | ❌ | ✅ | ✅ | ✅ |
| Read own notifications | ✅ | ✅ | ✅ | ✅ |
| Read another user's notifications | ❌ | ❌ | ❌ | ✅ |
| Read daily reports | ❌ | ❌ | ✅ | ✅ |
| Read discoverable Marketplace branches | ✅ | ✅ | ✅ | ✅ |
| Read nearby Marketplace branch discovery | ✅ | ✅ | ✅ | ✅ |
| Read Marketplace branch open status | ✅ | ✅ | ✅ | ✅ |
| Read discoverable Marketplace offerings | ✅ | ✅ | ✅ | ✅ |
| Manage Marketplace businesses/branches/hours/closures/offerings | ❌ | ❌ | ✅ | ✅ |
| Assign roles | ❌ | ❌ | ❌ | ✅ |

## Permission catalogue

- **CUSTOMER:** `USER_SELF_MANAGE`, `VEHICLE_SELF_MANAGE`, `SERVICE_READ`, `BOOKING_SELF_MANAGE`, `QUEUE_SELF_READ`, `NOTIFICATION_SELF_READ`, `MARKETPLACE_READ`
- **STAFF:** all CUSTOMER permissions plus `VEHICLE_OPERATE`, `BOOKING_OPERATE`, `QUEUE_OPERATE`
- **BUSINESS_OWNER:** all STAFF permissions plus `SERVICE_MANAGE`, `REPORT_READ`, `MARKETPLACE_MANAGE`
- **PLATFORM_ADMIN:** all current permissions, including `USER_ADMIN` and `ROLE_ASSIGN`

Operational roles are STAFF, BUSINESS_OWNER, and PLATFORM_ADMIN for vehicle, booking, and queue resource authorization. Notifications are stricter: only the user themselves or PLATFORM_ADMIN may read them.

OPS-001 branch filters, branch call-next, and branch/business report scopes constrain returned or selected data, but they do not establish tenant ownership authorization. STAFF and BUSINESS_OWNER operational access remains global until TENANT-001.

Marketplace management is intentionally global for BUSINESS_OWNER and PLATFORM_ADMIN until TENANT-001 introduces owner/business scoping. `MARKETPLACE_READ` exposes bounded branch/offering/nearby discovery and operational open-status decisions; the public-discovery flag does not change whether a branch is operationally open or make the endpoint anonymous. Nearby discovery may explicitly reuse open status and effective offerings, while basic branch/offering discovery retains its existing semantics.

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
| Assign roles | ❌ | ❌ | ❌ | ✅ |

## Permission catalogue

- **CUSTOMER:** `USER_SELF_MANAGE`, `VEHICLE_SELF_MANAGE`, `SERVICE_READ`, `BOOKING_SELF_MANAGE`, `QUEUE_SELF_READ`, `NOTIFICATION_SELF_READ`
- **STAFF:** all CUSTOMER permissions plus `VEHICLE_OPERATE`, `BOOKING_OPERATE`, `QUEUE_OPERATE`
- **BUSINESS_OWNER:** all STAFF permissions plus `SERVICE_MANAGE`, `REPORT_READ`
- **PLATFORM_ADMIN:** all current permissions, including `USER_ADMIN` and `ROLE_ASSIGN`

Operational roles are STAFF, BUSINESS_OWNER, and PLATFORM_ADMIN for vehicle, booking, and queue resource authorization. Notifications are stricter: only the user themselves or PLATFORM_ADMIN may read them.

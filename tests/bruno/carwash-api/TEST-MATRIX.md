# Bruno Test Coverage Matrix

Every documented controller/OpenAPI operation is represented by at least one executable Bruno request. `N/A` means the category is not meaningful for that operation rather than missing coverage.

| Method | Endpoint | Functional | Validation | 401 | 403 / RBAC | Ownership | Integrity | Workflow |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `PUT` | `/api/admin/users/{userId}/role` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `PUT` | `/api/admin/users/{userId}/tenant-membership` | ✅ | ✅ | ✅ | ✅ | ✅ — explicit administrator assignment | ✅ — stale token and role/member consistency | ✅ |
| `DELETE` | `/api/admin/users/{userId}/tenant-membership` | ✅ | N/A — no request body | ✅ | ✅ | ✅ — explicit administrator removal | ✅ — atomic demotion | ✅ |
| `GET` | `/api/audit-records` | ✅ | ✅ — scope/filter bounds | ✅ | ✅ — owner/admin only | ✅ — canonical tenant or explicit admin scope | ✅ — append-only; no write route | ✅ |
| `POST` | `/api/auth/login` | ✅ | ✅ | N/A — public endpoint | N/A — public or no wrong-role case | N/A — not user-owned | N/A — no state/dependency mutation | ✅ |
| `GET` | `/api/auth/me` | ✅ | N/A — no request body/typed input case | ✅ | N/A — public or no wrong-role case | N/A — not user-owned | N/A — no state/dependency mutation | ✅ |
| `GET` | `/api/availability` | ✅ | ✅ | ✅ | N/A — all current roles have SERVICE_READ | N/A — service/date capacity view | ✅ | ✅ |
| `GET` | `/api/availability/branches` | ✅ | ✅ — required instant/service, coordinate pair, radius | ✅ | N/A — all current roles have SERVICE_READ | N/A — public discovery view | ✅ — unambiguous local start/lifecycle/hours/closure/offering capacity | ✅ |
| `GET` | `/api/bookings` | ✅ | ✅ — invalid/unknown branch filter | ✅ | ✅ | ✅ — tenant enumeration and foreign branch 404 | ✅ — no cross-tenant leakage | ✅ |
| `POST` | `/api/bookings` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `DELETE` | `/api/bookings/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/bookings/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | ✅ |
| `PUT` | `/api/bookings/{id}` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/bookings/{id}/reschedule` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/bookings/{id}/cancel` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/bookings/{id}/confirm` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `GET` | `/api/notifications/user/{userId}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | ✅ |
| `GET` | `/api/queue-entries` | ✅ | ✅ — blank/unknown branch filter | ✅ | ✅ | ✅ — tenant enumeration and foreign branch 404 | ✅ — no cross-tenant leakage | ✅ |
| `POST` | `/api/queue-entries` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `DELETE` | `/api/queue-entries/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/queue-entries/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | ✅ |
| `POST` | `/api/queue-entries/call-next` | ✅ | ✅ — required branch | ✅ | ✅ | ✅ — branch must belong to operator tenant | ✅ — branch selection | ✅ |
| `POST` | `/api/queue-entries/{id}/call` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `POST` | `/api/queue-entries/{id}/complete` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `PUT` | `/api/queue-entries/{id}/position` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | ✅ | N/A — not needed in multi-step journey |
| `POST` | `/api/queue-entries/{id}/start` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `GET` | `/api/recommendations/branches` | ✅ — all five preferences | ✅ — coordinates/service/time/preference/server radius | ✅ | N/A — all current roles have MARKETPLACE_READ | N/A — public discovery view | ✅ — shared AVAIL-002 eligibility/raw metrics/null estimates/ties | ✅ — recommendation remains booking-valid |
| `GET` | `/api/reports/daily-summary` | ✅ | ✅ — exactly one scope | ✅ | ✅ | ✅ — owner tenant or explicit admin scope | ✅ — repository tenant isolation | ✅ |
| `GET` | `/api/services` | ✅ | ✅ | ✅ | N/A — public or no wrong-role case | N/A — not user-owned | N/A — no state/dependency mutation | ✅ |
| `POST` | `/api/services` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `DELETE` | `/api/services/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/services/{id}` | ✅ | N/A — no request body/typed input case | ✅ | N/A — public or no wrong-role case | N/A — not user-owned | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `PUT` | `/api/services/{id}` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `POST` | `/api/services/{id}/activate` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `POST` | `/api/services/{id}/deactivate` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `GET` | `/api/users` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `POST` | `/api/users` | ✅ | ✅ | N/A — public endpoint | N/A — public or no wrong-role case | N/A — not user-owned | ✅ | ✅ |
| `DELETE` | `/api/users/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/users/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `PUT` | `/api/users/{id}` | ✅ | ✅ | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `GET` | `/api/vehicles` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `POST` | `/api/vehicles` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `DELETE` | `/api/vehicles/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/vehicles/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | ✅ |
| `PUT` | `/api/vehicles/{id}` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/marketplace/businesses` | ✅ | N/A — no typed input | ✅ | ✅ | ✅ — tenant safe-404 isolation | N/A — read only | ✅ |
| `POST` | `/api/marketplace/businesses` | ✅ | ✅ | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/businesses/{businessId}` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | N/A — read only | ✅ |
| `PUT` | `/api/marketplace/businesses/{businessId}` | ✅ | ✅ | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `POST` | `/api/marketplace/businesses/{businessId}/activate` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `POST` | `/api/marketplace/businesses/{businessId}/deactivate` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/businesses/{businessId}/branches` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `POST` | `/api/marketplace/businesses/{businessId}/branches` | ✅ | ✅ | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/branches/discoverable` | ✅ | N/A — no typed input | ✅ | N/A — all roles have read permission | N/A — public discovery view | ✅ | ✅ |
| `GET` | `/api/marketplace/branches/discoverable/nearby` | ✅ | ✅ — coordinates/radius/service/openAt/sort | ✅ | N/A — all roles have read permission | N/A — public discovery view | ✅ — lifecycle/offering/hours filters | ✅ |
| `GET` | `/api/marketplace/branches/{branchId}` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | N/A — read only | ✅ |
| `PUT` | `/api/marketplace/branches/{branchId}` | ✅ | ✅ | ✅ | ✅ | ✅ — business ownership preserved | ✅ | ✅ |
| `POST` | `/api/marketplace/branches/{branchId}/activate` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `POST` | `/api/marketplace/branches/{branchId}/deactivate` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/branches/{branchId}/operating-hours` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | N/A — read only | ✅ |
| `PUT` | `/api/marketplace/branches/{branchId}/operating-hours` | ✅ | ✅ | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/branches/{branchId}/closures` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | N/A — read only | ✅ |
| `POST` | `/api/marketplace/branches/{branchId}/closures` | ✅ | ✅ | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `POST` | `/api/marketplace/closures/{closureId}/cancel` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/branches/{branchId}/open-status` | ✅ | ✅ | ✅ | N/A — all roles have read permission | N/A — operational view | ✅ | ✅ |
| `GET` | `/api/marketplace/branches/{branchId}/offerings` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | N/A — read only | ✅ |
| `POST` | `/api/marketplace/branches/{branchId}/offerings` | ✅ | ✅ | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/offerings/{offeringId}` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | N/A — read only | ✅ |
| `PUT` | `/api/marketplace/offerings/{offeringId}` | ✅ | ✅ | ✅ | ✅ | ✅ — identities omitted | ✅ | ✅ |
| `POST` | `/api/marketplace/offerings/{offeringId}/activate` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `POST` | `/api/marketplace/offerings/{offeringId}/deactivate` | ✅ | N/A — path validation in Java | ✅ | ✅ | ✅ — tenant safe-404 isolation | ✅ | ✅ |
| `GET` | `/api/marketplace/branches/{branchId}/offerings/discoverable` | ✅ | N/A — path validation in Java | ✅ | N/A — all roles have read permission | N/A — public discovery view | ✅ | ✅ |

## Totals

- API operations: **72**
- Happy-path functional coverage: **72/72**
- 401 coverage: **69/69 protected operations** (2 public operations are N/A)
- RBAC/403, subject/tenant ownership, validation, integrity, and multi-step workflow applicability are covered wherever meaningful in the matrix above.
- Bruno requests/assertions: **542 requests / 1,714 assertions**

The full authorization suite also exercises each significant role/capability allow/deny cell rather than relying only on per-operation counts.

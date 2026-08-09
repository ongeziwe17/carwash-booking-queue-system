# Bruno Test Coverage Matrix

Every documented controller/OpenAPI operation is represented by at least one executable Bruno request. `N/A` means the category is not meaningful for that operation rather than missing coverage.

| Method | Endpoint | Functional | Validation | 401 | 403 / RBAC | Ownership | Integrity | Workflow |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `PUT` | `/api/admin/users/{userId}/role` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `POST` | `/api/auth/login` | ✅ | ✅ | N/A — public endpoint | N/A — public or no wrong-role case | N/A — not user-owned | N/A — no state/dependency mutation | ✅ |
| `GET` | `/api/auth/me` | ✅ | N/A — no request body/typed input case | ✅ | N/A — public or no wrong-role case | N/A — not user-owned | N/A — no state/dependency mutation | ✅ |
| `GET` | `/api/bookings` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `POST` | `/api/bookings` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `DELETE` | `/api/bookings/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/bookings/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | ✅ |
| `PUT` | `/api/bookings/{id}` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/bookings/{id}/reschedule` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/bookings/{id}/cancel` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/bookings/{id}/confirm` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `GET` | `/api/notifications/user/{userId}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | ✅ |
| `GET` | `/api/queue-entries` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | N/A — no state/dependency mutation | N/A — not needed in multi-step journey |
| `POST` | `/api/queue-entries` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `DELETE` | `/api/queue-entries/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | N/A — not needed in multi-step journey |
| `GET` | `/api/queue-entries/{id}` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | ✅ | N/A — no state/dependency mutation | ✅ |
| `POST` | `/api/queue-entries/call-next` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `POST` | `/api/queue-entries/{id}/call` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `POST` | `/api/queue-entries/{id}/complete` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `PUT` | `/api/queue-entries/{id}/position` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | ✅ | N/A — not needed in multi-step journey |
| `POST` | `/api/queue-entries/{id}/start` | ✅ | N/A — no request body/typed input case | ✅ | ✅ | N/A — not user-owned | ✅ | ✅ |
| `GET` | `/api/reports/daily-summary` | ✅ | ✅ | ✅ | ✅ | N/A — not user-owned | N/A — no state/dependency mutation | ✅ |
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

## Totals

- API operations: **39**
- Happy-path functional coverage: **39/39**
- 401 coverage: **37/37 protected operations** (2 public operations are N/A)
- RBAC/403 applicability covered: **34 operations/capabilities**
- Ownership applicability covered: **15 operations**
- Validation applicability covered: **15 operations**
- HTTP-visible integrity applicability covered: **23 operations**
- Operations used in multi-step workflows: **24**

The full authorization suite also exercises each significant role/capability allow/deny cell rather than relying only on per-operation counts.

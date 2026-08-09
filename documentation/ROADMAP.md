# Product Roadmap

## Approved Product Direction

The approved product direction is a **Marketplace-enabled multi-tenant SaaS platform**.

Customers should eventually be able to discover and compare independent car wash businesses and branches based on location, service availability, queue conditions, total completion time, price, and wash type. Business owners and staff should manage only their own operational data.

The current application remains a Spring Boot modular-monolith backend foundation. It is not yet a production-ready Marketplace or SaaS platform.

## Current Implemented Foundation

Implemented on `staging`:

- User record CRUD and duplicate-email validation.
- Vehicle CRUD, user ownership, and duplicate plate validation during creation.
- Global service catalogue CRUD with active/inactive workflows.
- Booking creation, guarded update, confirmation, queue-aware cancellation, future-time validation, ownership validation, inactive-service rejection, and exact-slot capacity validation.
- Eligible queue entry creation with server-managed global ordering, cumulative wait estimates, full manual rebalance, synchronized call/start/complete transitions, and deletion.
- In-app notification creation and recent lookup by user.
- Basic daily booking and queue summary reporting.
- BCrypt credential storage, JWT authentication, RBAC, ownership authorization, and platform-admin role assignment.
- Bounded user/request contracts and standardized safe API errors.
- Validated runtime policy configuration for booking, notification, queue, and application-time behavior.
- Swagger/OpenAPI with CI contract export/quality gates, Maven tests, Docker, Docker Compose, and Bruno HTTP acceptance coverage.

Current limitations include some non-user domain response schemas, in-memory-only storage, a global single-location queue with no branch model or tenant isolation, no payments/external notification delivery, and no production observability platform. Authentication and RBAC are implemented but do not provide tenant isolation.

## Phase 0 — API, Data, Test, and Delivery Hardening

Complete before major Marketplace domain expansion:

1. **API-001** — Protect user registration and response contracts (#12) — implemented.
2. **SEC-001** — Store credentials securely (#24) — implemented.
3. **API-002** — Standardize request validation and error contracts (#106) — implemented.
4. **DATA-001** — Enforce aggregate and repository integrity (#107) — implemented.
5. **CI-001** — Align CI/CD and branch promotion with `staging` delivery (#109) — implemented.
6. **TEST-001** — Improve test isolation and quality gates (#110) — implemented.
7. **CONFIG-001** — Externalize runtime policy configuration (#111) — implemented.
8. **DOCS-001** — Align OpenAPI and written API contracts (#108) — implemented by this alignment change.

**SEC-002** (#13) JWT authentication and **SEC-003** (#22) RBAC/ownership authorization were also completed ahead of their original Phase 4 sequencing. They are implemented foundations, not missing future capabilities.

Expected outcome: safe bounded DTOs, predictable validation/errors, no silent ID overwrites, deterministic tests, documented configuration, and an integration pipeline that validates the active branch flow.

## Phase 1 — Complete the Single-Location Booking and Queue Foundation

The recommendation system must not be built before queue and availability behaviour is reliable.

1. **QUEUE-001** — Enforce queue-entry eligibility and uniqueness (#16) — implemented.
2. **QUEUE-002** — Automate queue ordering, position recalculation, and wait estimates (#17) — implemented.
3. **WORKFLOW-001** — Synchronize booking and queue lifecycles (#20) — implemented.
4. **QUEUE-003** — Implement true call-next behaviour (#112).
5. **BOOKING-001** — Add focused booking rescheduling; the configurable cancellation cutoff is already implemented by CONFIG-001 (#113).
6. **AVAIL-001** — Add a single-location service availability API (#114).

Expected outcome: confirmed bookings enter one ordered queue, positions and ETAs are server-managed, queue transitions keep booking state consistent, and customers can check availability before attempting a booking.

## Phase 2 — Marketplace Business, Branch, and Availability Foundation

1. **MKT-001** — Add Marketplace business and branch registration (#115).
2. **MKT-002** — Add branch operating hours and temporary closures (#116).
3. **SERVICE-001** — Add branch-specific service offerings, prices, durations, and capacity (#117).
4. **OPS-001** — Scope bookings, queues, notifications, and reports to branches (#118).
5. **GEO-001** — Add branch distance calculation and public discovery (#119).
6. **AVAIL-002** — Add branch-aware availability search (#120).

Expected outcome: the backend understands which business and branch is being considered, which services are offered there, whether the branch is open, what capacity is available, and how far it is from the customer.

## Phase 3 — Explainable Rule-Based Recommendations

1. **REC-001** — Build explainable rule-based Marketplace recommendations (#121).

The first recommendation release should:

- Filter out inactive, closed, unsupported, unavailable, and full branches.
- Support `NEAREST`, `SHORTEST_QUEUE`, `FASTEST_TOTAL_TIME`, `LOWEST_PRICE`, and `BEST_OVERALL` preferences.
- Calculate distance, estimated queue wait, service duration, estimated total completion time, price, and remaining capacity.
- Use externalized weights and deterministic tie-breaking.
- Return ranked options with a score breakdown and human-readable reason.

Machine learning is intentionally excluded from the first recommendation release.

## Phase 4 — Persistence, Tenant Isolation, and Production Security Hardening

Required before public production use:

1. **DATA-002** — Add PostgreSQL persistence, migrations, and transaction boundaries (#122).
2. **TEST-002** — Add PostgreSQL integration tests with Testcontainers (#123).
3. **SEC-002** — Authenticate users securely (#13) — implemented foundation; retain and harden as the product evolves.
4. **SEC-003** — Enforce role-based access control (#22) — implemented foundation; tenant-scoped authorization is still pending.
5. **TENANT-001** — Enforce Marketplace tenant isolation (#124).
6. **AUDIT-001** — Add security and operational audit logging (#125).

Expected outcome: durable and transactionally safe data plus verified separation between independent car wash businesses, building on the existing protected API and customer/staff/owner/admin role foundation.

## Phase 5 — Product and Platform Expansion

- **NOTIFY-001** — Complete the in-app notification lifecycle (#126).
- **NOTIFY-002** — Integrate external email and SMS delivery (#127).
- **PAY-001** — Add Marketplace payment and refund workflows (#128).
- **FEEDBACK-001** — Add verified ratings and service feedback (#129).
- **REPORT-001** — Add tenant-aware business dashboards and analytics (#130).
- **OBS-001** — Add production observability and health endpoints (#131).
- **DEPLOY-001** — Harden production container and deployment configuration (#132).
- **FRONTEND-001** — Build customer and operator Marketplace applications (#133).
- **API-003** — Add pagination, filtering, and sorting to list APIs (#135).
- **SAAS-001** — Add business subscription plans and platform billing (#136).
- **REALTIME-001** — Add real-time queue and booking updates (#137).
- **GEO-002** — Add traffic-aware travel time for recommendations (#138).

## Phase 6 — Data-Driven Recommendation Evolution

- **ML-001** — Evolve recommendations with historical prediction and personalization (#134).

This phase should start only after sufficient clean historical data exists. The rule-based engine remains the explainable baseline and production fallback.

Candidate later capabilities include:

- Predicted wait and completion time.
- Branch/service demand forecasting.
- Recommended lower-demand visit times.
- Privacy-controlled preference learning.
- Queue and capacity anomaly detection.

## Recommended Delivery Sequence

```text
API/data/test hardening
    -> reliable booking and queue foundation
    -> Marketplace business and branch model
    -> branch services, capacity, discovery, and availability
    -> explainable rule-based recommendations
    -> PostgreSQL, tenant isolation, audit/security hardening
    -> payments, notifications, dashboards, real-time UX, and frontends
    -> predictive/ML optimisation after data exists
```

## Maintenance Principles

- Keep issues as focused top-level items unless sub-issues are explicitly adopted later.
- Use stable area-based identifiers such as `QUEUE-001`, `REC-001`, and `SEC-001`.
- Use `staging` as the current source of truth until the branch-promotion strategy is intentionally changed.
- Do not describe planned functionality as implemented.
- Prefer one shared decision service for availability and booking validation.
- Keep recommendation eligibility deterministic and separate from ranking.
- Preserve the modular-monolith architecture until operational evidence justifies extracting services.
- Add ML only after the data, evaluation, privacy, monitoring, and fallback requirements are satisfied.

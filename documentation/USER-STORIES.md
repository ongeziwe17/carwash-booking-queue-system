# Agile User Stories

## 1. Product Direction

The approved direction is a **Marketplace-enabled multi-tenant SaaS platform**. Customers should eventually be able to discover and compare independent car wash branches, receive explainable recommendations, book an available service, follow the queue, pay, and provide feedback. Business users should operate only their own branches and data.

Stories are grouped by delivery phase so planned capabilities are not confused with the current implementation.

## 2. Implemented Current Foundation

| Story ID | User Story | Current Acceptance Criteria | Status |
| --- | --- | --- | --- |
| US-CUR-001 | As an operator, I want to manage user records so vehicles and bookings can reference customers. | Create, retrieve, update, delete, and reject duplicate email records. | Implemented foundation; unsafe response contracts remain. |
| US-CUR-002 | As a customer or operator, I want to manage vehicle records for a user. | Create, retrieve, update, delete, associate with an owner, and reject duplicate plates during creation. | Implemented foundation. |
| US-CUR-003 | As an operator, I want to manage a wash-service catalogue. | Create, retrieve, update, delete, activate, deactivate, and filter services. | Implemented as a global catalogue only. |
| US-CUR-004 | As an operator, I want to manage bookings. | Create future bookings with canonical branch/offering scope; tenant-scoped confirm/update/filter/cancel for operators; subject-scoped customer self-service. | Implemented with tenant predicates, branch-partitioned exact-slot capacity, and lifecycle synchronization. |
| US-CUR-005 | As staff, I want to manage queue entries. | Inherit scope from bookings; tenant/branch-filter, order, rebalance, call, start, complete, and delete entries. | Implemented with tenant isolation, branch ordering, offering-duration waits, and synchronized booking lifecycle. |
| US-CUR-006 | As a customer, I want to see recent in-app notifications. | Booking/queue events create bounded records with branch/offering context that can be listed by user. | Partially implemented; external delivery/read lifecycle remain future work. |
| US-CUR-007 | As an operator, I want a daily operational summary. | Return booking/queue counts for exactly one tenant-authorized branch or business scope using branch-local dates. | Implemented under both persistence profiles; richer analytics remain future work. |
| US-CUR-008 | As a business owner, I want to manage my Marketplace business and branches. | Valid contact/location data, immutable tenant ownership, lifecycle actions, offerings, and discovery-safe views; only platform admins register businesses. | Implemented under both persistence profiles with strict tenant isolation. |

## 3. Phase 0 — API, Data, Security, Test, and Delivery Hardening

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-API-001 | #12 | As an API consumer, I want safe user request and response DTOs so credentials and nested domain graphs are not exposed. | No password fields in responses; validated registration; bounded DTOs. |
| US-SEC-001 | #24 | As the system, I want credentials hashed securely so plaintext passwords are never stored or compared directly. | Password encoder, no credential serialization/logging, tests for hashing and matching. |
| US-API-002 | #106 | As an API consumer, I want consistent validation and error contracts so failures are predictable. | Stable 400/404/500 handling, safe internal-error response, validation on all requests. |
| US-DATA-001 | #107 | As a maintainer, I want integrity rules so duplicate IDs and deletions do not corrupt relationships. | No silent overwrite, documented dependency rules, deterministic in-memory behaviour. |
| US-CI-001 | #109 | As a maintainer, I want CI aligned with the active integration branch so every change is verified. | Tests, Docker validation, artifacts, branch protection, and publishing rules match the workflow. |
| US-TEST-001 | #110 | As a maintainer, I want isolated deterministic tests and quality gates. | No shared-state leakage, coverage baseline/gate, reusable fixtures. |
| US-CONFIG-001 | #111 | As an operator, I want runtime policies externalized so environments can configure capacity, cutoffs, and timezones safely. | Typed validated properties and environment overrides. |
| US-DOCS-001 | #108 | As an API consumer, I want Swagger and written documentation to match the implemented API. | Correct methods, parameters, bodies, statuses, schemas, and examples. |

## 4. Phase 1 — Reliable Booking and Queue Foundation

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-QUEUE-001 | #16 | As a customer, I want only an eligible confirmed booking to enter one active queue. | Confirmed booking, active matching service, one active entry, validated IDs. |
| US-QUEUE-002 | #17 | As a customer, I want the server to assign and recalculate my queue position and wait estimate. | Unique consecutive positions and ETA based on active work ahead. |
| US-WORKFLOW-001 | #20 | As staff, I want booking and queue states synchronized so operational data and reports agree. | Implemented: queue start/completion updates booking state; cancellation cannot leave active queue work. |
| US-QUEUE-003 | #112 | As staff, I want a true call-next action so the first waiting entry is selected automatically. | Implemented: deterministic branch-specific first-WAITING selection, non-waiting skip, explicit override, and documented empty-queue handling. |
| US-BOOKING-001 | #113 | As a customer, I want to reschedule or cancel within allowed policy windows. | Implemented: focused schedule-only rescheduling preserves status, revalidates slot/service/ownership, rejects late or queued work, and notifies on success. |
| US-AVAIL-001 | #114 | As a customer, I want to view available service slots before booking. | Implemented: deterministic future slots share booking grid/window/capacity rules, expose remaining global capacity and duration, omit full slots, and do not reserve capacity. |

## 5. Phase 2 — Marketplace Business and Branch Foundation

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-MKT-001 | #115 | As a business owner, I want to register a business and its branches on the Marketplace. | Implemented: valid business/branch data, coordinates, timezone, immutable ownership, lifecycle state, and effective discovery filtering. |
| US-MKT-002 | #116 | As a business owner, I want to configure branch operating hours and closures. | Implemented: atomic weekly schedules, multiple/overnight intervals, absolute temporary closures with cancellation history, and timezone-aware explicit-instant open/closed decisions. |
| US-SERVICE-001 | #117 | As a business owner, I want each branch to define its own service offerings, prices, durations, and capacity. | Implemented: immutable branch/service relationships, independent validated terms, activation lifecycle, configured capacity, and effective/public discovery projection. |
| US-OPS-001 | #118 | As an operator, I want bookings, queues, notifications, and reports scoped to the correct branch. | Implemented: canonical branch/offering bookings, inherited queue scope, branch-isolated ordering and lookup, bounded notification context, and explicit branch/business reports. |
| US-GEO-001 | #119 | As a customer, I want to discover active branches near my location. | Implemented: valid coordinates, bounded radius, effective service-offering and explicit open-instant filters, deterministic raw-distance sorting/ties, and two-decimal kilometre output. |
| US-AVAIL-002 | #120 | As a customer, I want branch-aware availability so I see branches that are open, capable, and not full. | Implemented: explicit-instant timezone evaluation, continuous-window-anchored slots, complete hours/closure windows, effective offerings, overlapping configured capacity, branch-isolated queue context, optional radius, stable ordering, and shared booking validation. |

## 6. Phase 3 — Smart Marketplace Recommendations

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-REC-001 | #121 | As a customer, I want ranked car wash recommendations based on my location, service need, time, queue, availability, and price. | Implemented: one AVAIL-002 candidate set supports nearest, shortest queue, fastest total time, lowest price, and weighted best overall; raw metrics drive deterministic ranking; bounded responses include normalized score components and a customer-safe explanation. |

REC-001 is implemented as a point-in-time, non-reserving read. Future-date queue and total-time metrics remain `null`, all ties end with branch ID then offering ID, and booking creation performs authoritative revalidation. The implementation remains rule-based, deterministic, and explainable; machine learning is not part of this phase.

## 7. Phase 4 — Persistence, Security, and Tenant Isolation

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-DATA-002 | #122 | As an operator, I want data persisted in PostgreSQL so records survive restarts and workflows are transactionally safe. | Implemented: migrations, constraints, indexes, transactions, cross-instance locks, Docker profile, and restart durability. |
| US-TEST-002 | #123 | As a maintainer, I want PostgreSQL integration tests so migrations and persistence behaviour are verified. | Implemented by PR #179 with PostgreSQL 17.6 Testcontainers, production Flyway migrations, repository/constraint/rollback coverage, restart durability, precision round trips, and booking/queue concurrency tests. |
| US-SEC-002 | #13 | As a user, I want to authenticate securely so protected functionality can identify me. | Safe login, credential verification, token/session expiry, HTTP 401 paths. |
| US-SEC-003 | #22 | As a platform administrator, I want RBAC so customer, staff, owner, and admin actions are protected. | Explicit role matrix and 401/403 coverage. |
| US-TENANT-001 | #124 | As a business owner, I want strict tenant isolation so no other business can access my private operational data. | Implemented: one canonical membership, trusted/stale-invalidating `tenant_id`, scoped APIs/repositories/reports/notifications, safe foreign `404`, explicit admin paths, V4 constraints/indexes, and discovery DTO allowlists. |
| US-AUDIT-001 | #125 | As a business/platform administrator, I want an audit trail for sensitive actions. | Implemented: immutable actor/action/resource/outcome records; same-transaction success; isolated denied/failure writes; canonical identity; bounded redaction; tenant/admin read scopes; V5 indexes/constraints; no write/delete API. |

## 8. Phase 5 — Product and Platform Expansion

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-NOTIFY-001 | #126 | As a customer, I want a complete in-app notification centre. | Pagination, unread filtering/count, mark-read, bounded privacy-safe responses. |
| US-NOTIFY-002 | #127 | As a customer, I want reliable email/SMS updates. | Provider-neutral delivery, retries, idempotency, status tracking, preferences. |
| US-PAY-001 | #128 | As a customer, I want secure booking deposits/payments and refunds. | Checkout, verified webhooks, reconciliation, idempotent refunds, no card storage. |
| US-FEEDBACK-001 | #129 | As a customer, I want to rate a completed service. | Verified completed booking, one review policy, public aggregates, moderation state. |
| US-REPORT-001 | #130 | As a business owner, I want dashboards for throughput, waits, utilisation, services, and revenue. | Tenant/branch/date filtering and reconciled metrics. |
| US-OBS-001 | #131 | As an operator, I want health, logs, metrics, traces, and alerts so the platform can be supported. | Actuator, structured logs, correlation IDs, Prometheus metrics, OTel traces, redaction. |
| US-DEPLOY-001 | #132 | As a platform engineer, I want hardened deployment configuration so releases are secure and reversible. | Non-root image, probes, resources, profiles, secrets, smoke tests, rollback guidance. |
| US-FRONTEND-001 | #133 | As a customer/operator, I want web applications for discovery, booking, queue tracking, and business operations. | Secure responsive customer and operator workflows with frontend tests. |
| US-API-003 | #135 | As an API consumer, I want pagination, filtering, and sorting so large collections remain usable. | Deterministic bounded collection APIs with tenant-safe queries. |
| US-SAAS-001 | #136 | As a platform owner, I want business subscription plans and entitlements so the SaaS can be commercialized. | Trials/subscriptions, plan limits, idempotent billing events, centralized entitlement checks. |
| US-REALTIME-001 | #137 | As a customer/operator, I want real-time queue and booking updates. | Authorized WebSocket/SSE events, reconnect/reconciliation behaviour, REST source of truth. |
| US-GEO-002 | #138 | As a customer, I want traffic-aware travel estimates so fastest recommendations include journey time. | Provider-neutral routing, caching/timeouts, Haversine fallback, explanation of source. |

## 9. Phase 6 — Data-Driven Recommendation Evolution

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-ML-001 | #134 | As a customer/operator, I want recommendations and forecasts improved using historical outcomes. | Wait-time prediction, demand forecasting, privacy controls, offline evaluation, versioning, monitoring, rollback, and rule-based fallback. |

## 10. Story Principles

- Eligibility and availability rules remain deterministic even when recommendation ranking evolves.
- Stories should be implemented in dependency order rather than issue-number order.
- Backup/restore operations, SIEM/archive/retention operations, broader observability, and deployment hardening remain required before production Marketplace use; tenant isolation and application auditability are implemented foundations.
- The modular monolith remains the default architecture until scaling or team boundaries justify extraction.
- No story should describe a planned capability as already implemented.

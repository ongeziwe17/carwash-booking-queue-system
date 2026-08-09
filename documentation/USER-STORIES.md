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
| US-CUR-004 | As an operator, I want to manage bookings. | Create future bookings for valid user/vehicle/service records, confirm, guarded update, and queue-aware cancel. | Implemented foundation with exact-slot capacity and lifecycle synchronization. |
| US-CUR-005 | As staff, I want to manage queue entries. | Create, retrieve, server-order, manually rebalance, call, start, complete, and delete entries. | Implemented as one global single-location queue with synchronized booking lifecycle. |
| US-CUR-006 | As a customer, I want to see recent in-app notifications. | Booking/queue events create records that can be listed by user. | Partially implemented. |
| US-CUR-007 | As an operator, I want a daily operational summary. | Return booking and queue counts for a supplied date. | Partially implemented and in-memory only. |

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
| US-QUEUE-003 | #112 | As staff, I want a true call-next action so the first waiting entry is selected automatically. | Deterministic selection, empty-queue handling, accurate endpoint semantics. |
| US-BOOKING-001 | #113 | As a customer, I want to reschedule or cancel within allowed policy windows. | Revalidate availability/conflicts; reject late or in-service changes; notify on success. |
| US-AVAIL-001 | #114 | As a customer, I want to view available service slots before booking. | Available/full/past/inactive slots agree with booking validation. |

## 5. Phase 2 — Marketplace Business and Branch Foundation

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-MKT-001 | #115 | As a business owner, I want to register a business and its branches on the Marketplace. | Valid business/branch data, coordinates, timezone, status, and ownership. |
| US-MKT-002 | #116 | As a business owner, I want to configure branch operating hours and closures. | Weekly schedules, temporary closures, timezone-aware open/closed decisions. |
| US-SERVICE-001 | #117 | As a business owner, I want each branch to define its own service offerings, prices, durations, and capacity. | Branch-specific offering lifecycle and validated capacity. |
| US-OPS-001 | #118 | As an operator, I want bookings, queues, notifications, and reports scoped to the correct branch. | No cross-branch mismatch; branch-specific operational views and reports. |
| US-GEO-001 | #119 | As a customer, I want to discover active branches near my location. | Valid coordinates, distance in kilometres, radius/service filters, deterministic sorting. |
| US-AVAIL-002 | #120 | As a customer, I want branch-aware availability so I see branches that are open, capable, and not full. | Hours, closures, offerings, capacity, bookings, queue, and distance are considered. |

## 6. Phase 3 — Smart Marketplace Recommendations

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-REC-001 | #121 | As a customer, I want ranked car wash recommendations based on my location, service need, time, queue, availability, and price. | Support nearest, shortest queue, fastest total time, lowest price, and best overall; exclude ineligible branches; return score breakdown and reason. |

The first recommendation implementation must remain rule-based, deterministic, and explainable. Machine learning is not part of this phase.

## 7. Phase 4 — Persistence, Security, and Tenant Isolation

| Story ID | GitHub | User Story | Acceptance Summary |
| --- | ---: | --- | --- |
| US-DATA-002 | #122 | As an operator, I want data persisted in PostgreSQL so records survive restarts and workflows are transactionally safe. | Migrations, constraints, indexes, transactions, concurrency handling, Docker profile. |
| US-TEST-002 | #123 | As a maintainer, I want PostgreSQL integration tests so migrations and persistence behaviour are verified. | Testcontainers, real constraints, rollback and concurrency tests. |
| US-SEC-002 | #13 | As a user, I want to authenticate securely so protected functionality can identify me. | Safe login, credential verification, token/session expiry, HTTP 401 paths. |
| US-SEC-003 | #22 | As a platform administrator, I want RBAC so customer, staff, owner, and admin actions are protected. | Explicit role matrix and 401/403 coverage. |
| US-TENANT-001 | #124 | As a business owner, I want strict tenant isolation so no other business can access my private operational data. | Tenant-scoped APIs/repositories/reports; cross-tenant access denied and tested. |
| US-AUDIT-001 | #125 | As a business/platform administrator, I want an audit trail for sensitive actions. | Append-only actor/action/resource/outcome records with privacy and tenant controls. |

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
- Security, tenant isolation, persistence, and observability are required before production Marketplace use.
- The modular monolith remains the default architecture until scaling or team boundaries justify extraction.
- No story should describe a planned capability as already implemented.

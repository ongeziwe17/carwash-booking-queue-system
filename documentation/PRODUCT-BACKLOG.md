# Product Backlog

## 1. Product Direction

The approved direction is a **Marketplace-enabled multi-tenant SaaS platform** for car wash discovery, booking, queue management, and smart recommendations.

This backlog uses stable area-based identifiers that align with GitHub issue titles. The GitHub issue number is included for traceability.

Priority guide:

- **P0** — Immediate correctness/security blocker.
- **P1** — Required for the next major product phase.
- **P2** — Important expansion after core foundations.
- **P3** — Later product/platform capability.
- **P4** — Future optimisation or research.

## 2. Completed Current Backend Foundation

| Area | Implemented State | Notes |
| --- | --- | --- |
| Users | Safe registration/profile responses, secure credentials, JWT authentication, RBAC and duplicate-email validation | Privileged role assignment is separate and platform-admin-only. |
| Vehicles | CRUD, owner association, duplicate-plate validation, ownership authorization and deletion integrity | Marketplace tenant scoping is not implemented. |
| Services | Reusable global service catalogue plus branch-specific offerings with independent price, duration, capacity, lifecycle, and discovery | Global price/duration remain transitional for legacy AVAIL-001. |
| Bookings and availability | Canonical branch/offering booking creation, retrieval/filtering, guarded same-branch offering update, rescheduling, confirm/cancel, shared complete-window/configured-capacity validation, branch-aware search, plus legacy global AVAIL-001 | Staff/bay resources and capacity reservations are not implemented. |
| Queues | Booking-derived branch/offering scope, branch-isolated ordering/waits/call-next/rebalance, explicit call override, synchronized call/start/complete workflow, and delete | Offering concurrent capacity is not a remaining-capacity model. |
| Notifications | In-app notification creation and bounded recent lookup with branch/offering context | Read-status lifecycle and external delivery are incomplete. |
| Reports | In-memory daily summary with exactly one branch or business scope and branch-local dates | Tenant-authorized dashboards, revenue, and durable analytics are future work. |
| Marketplace | Businesses/branches, location/lifecycle/discovery, schedules/closures/open status, offerings, branch-scoped operations, nearby straight-line discovery, and branch-aware availability | Tenant isolation, routing/geocoding, reservations, and resource calendars remain future work. |
| API/Docs | Generated Swagger/OpenAPI plus human-readable API documentation and contract quality gates | DOCS-001 keeps written and generated contracts aligned. |
| Testing | Unit/integration suites plus deterministic repeatability, OpenAPI gates, and Bruno HTTP acceptance | Production persistence testing remains future work. |
| Packaging | Maven, Docker, Docker Compose, and staging-aligned GitHub Actions | Production deployment hardening remains future work. |

Completed issue cleanup:

- #15 — Stronger booking creation validation.
- #19 — Service catalogue management.
- #102 — Implemented/planned documentation alignment.
- #103 — Booking time-slot and capacity validation.
- #12 — Safe user registration/response contracts (API-001).
- #24 — Secure credential storage (SEC-001).
- #13 — JWT authentication (SEC-002).
- #22 — RBAC and ownership authorization (SEC-003).
- #106 — Standardized validation/error contracts (API-002).
- #107 — In-memory aggregate/repository integrity (DATA-001).
- #109 — Staging CI/CD and branch promotion (CI-001).
- #110 — Test isolation and quality gates (TEST-001).
- #111 — Runtime policy configuration (CONFIG-001).
- #16 — Queue-entry eligibility and active uniqueness (QUEUE-001).
- #17 — Server-managed queue ordering and wait estimates (QUEUE-002).
- #20 — Synchronized booking and queue lifecycles (WORKFLOW-001).
- #112 — True server-selected call-next queue behaviour (QUEUE-003).
- #113 — Focused booking rescheduling with status preservation and cutoff enforcement (BOOKING-001).
- #114 — Single-location service availability with shared scheduling rules (AVAIL-001).
- #164 — Explicit capability-based modular-monolith boundaries with ArchUnit enforcement (ARCH-001).
- #115 — Marketplace business and branch registration (MKT-001).
- #116 — Branch operating hours and temporary closures (MKT-002).
- #117 — Branch-specific service offerings and configured capacity (SERVICE-001).
- #118 — Branch-scoped bookings, queues, notifications, and reports (OPS-001).
- #119 — Deterministic branch distance and nearby discovery (GEO-001).

## 3. Phase 0 — Immediate Hardening

| ID | GitHub | Backlog Item | Priority | Status |
| --- | ---: | --- | --- | --- |
| API-001 | #12 | Protect user registration and response contracts | P0 | Complete |
| SEC-001 | #24 | Store credentials securely | P0 | Complete |
| API-002 | #106 | Standardize request validation and error contracts | P0 | Complete |
| DATA-001 | #107 | Enforce aggregate and repository integrity | P0 | Complete |
| CI-001 | #109 | Align CI/CD and branch promotion with staging delivery | P0 | Complete |
| TEST-001 | #110 | Improve test isolation and quality gates | P1 | Complete |
| CONFIG-001 | #111 | Externalize runtime policy configuration | P1 | Complete |
| DOCS-001 | #108 | Align OpenAPI and written API contracts | P1 | Complete with #108 |

## 4. Phase 1 — Complete Booking and Queue Management

| ID | GitHub | Backlog Item | Priority | Depends On |
| --- | ---: | --- | --- | --- |
| QUEUE-001 | #16 | Enforce queue-entry eligibility and uniqueness | P0 | API-002, DATA-001 |
| QUEUE-002 | #17 | Automate queue ordering, recalculation, and wait estimates | P0 | QUEUE-001 |
| WORKFLOW-001 | #20 | Synchronize booking and queue lifecycles — implemented | P0 | QUEUE-001 |
| QUEUE-003 | #112 | Implement true call-next queue behaviour — implemented | P1 | QUEUE-001, QUEUE-002 |
| BOOKING-001 | #113 | Add focused booking rescheduling; reuses the CONFIG-001 cancellation cutoff — implemented | P1 | CONFIG-001, WORKFLOW-001 |
| AVAIL-001 | #114 | Add single-location service availability API — implemented | P1 | CONFIG-001, DATA-001 |

Phase exit criteria:

- Only eligible confirmed bookings enter one active queue.
- Queue positions and estimates are server-managed.
- Booking and queue states remain consistent.
- Customers can query availability before booking.

Phase 1 and the ARCH-001 transition are complete. MKT-001 (#115), MKT-002 (#116), SERVICE-001 (#117), OPS-001 (#118), GEO-001 (#119), and AVAIL-002 (#120) are implemented Phase 2 foundations.

## 5. Phase 2 — Marketplace Business and Branch Foundation

| ID | GitHub | Backlog Item | Priority | Depends On |
| --- | ---: | --- | --- | --- |
| MKT-001 | #115 | Add Marketplace business and branch registration — implemented | P1 | Phase 0, ARCH-001, AVAIL-001 |
| MKT-002 | #116 | Add branch operating hours and temporary closures — implemented | P1 | MKT-001, CONFIG-001 |
| SERVICE-001 | #117 | Add branch-specific service offerings and capacity — implemented | P1 | MKT-001, MKT-002, DATA-001 |
| OPS-001 | #118 | Scope bookings, queues, notifications, and reports to branches — implemented | P1 | SERVICE-001, WORKFLOW-001, QUEUE-002 |
| GEO-001 | #119 | Add branch distance calculation and public discovery — implemented | P1 | MKT-001, SERVICE-001 |
| AVAIL-002 | #120 | Add branch-aware availability search — implemented | P1 | MKT-002, SERVICE-001, OPS-001, GEO-001 |

Phase exit criteria:

- Independent businesses and physical branches are represented.
- Services, prices, durations, capacity, bookings, queues, and reports are branch-aware.
- Public discovery can filter nearby active branches.
- Branch availability is reliable enough to feed recommendations.

## 6. Phase 3 — Smart Marketplace Recommendations

| ID | GitHub | Backlog Item | Priority | Depends On |
| --- | ---: | --- | --- | --- |
| REC-001 | #121 | Build explainable rule-based Marketplace recommendations | P1/P2 | QUEUE-002, AVAIL-002, GEO-001, SERVICE-001, CONFIG-001 |

Recommendation MVP preferences:

- `NEAREST`
- `SHORTEST_QUEUE`
- `FASTEST_TOTAL_TIME`
- `LOWEST_PRICE`
- `BEST_OVERALL`

Recommendation MVP outputs:

- Branch/business and service offering.
- Distance.
- Queue wait estimate.
- Service duration.
- Estimated total completion time.
- Price and capacity remaining.
- Score breakdown and human-readable reason.

## 7. Phase 4 — Persistence, Tenant Isolation, and Production Security Hardening

SEC-002 (#13) authentication and SEC-003 (#22) RBAC/ownership authorization were completed ahead of this original phase. The remaining security boundary here is tenant isolation plus production/audit hardening.

| ID | GitHub | Backlog Item | Priority | Depends On |
| --- | ---: | --- | --- | --- |
| DATA-002 | #122 | Add PostgreSQL persistence, migrations, and transaction boundaries | P1 | Stable Marketplace domain |
| TEST-002 | #123 | Add PostgreSQL integration tests with Testcontainers | P1 | DATA-002 |
| TENANT-001 | #124 | Enforce Marketplace tenant isolation | P0 before production | MKT-001, DATA-002, SEC-002, SEC-003 |
| AUDIT-001 | #125 | Add security and operational audit logging | P2 | DATA-002, SEC-002, SEC-003, TENANT-001 |

## 8. Phase 5 — Product and Platform Expansion

| ID | GitHub | Backlog Item | Priority |
| --- | ---: | --- | --- |
| NOTIFY-001 | #126 | Complete in-app notification lifecycle | P2 |
| NOTIFY-002 | #127 | Integrate external email and SMS delivery | P2/P3 |
| PAY-001 | #128 | Add Marketplace payment and refund workflows | P2/P3 |
| FEEDBACK-001 | #129 | Add verified ratings and service feedback | P3 |
| REPORT-001 | #130 | Add tenant-aware business dashboards and analytics | P2/P3 |
| OBS-001 | #131 | Add production observability and health endpoints | P1 before production |
| DEPLOY-001 | #132 | Harden production container and deployment configuration | P1 before production |
| FRONTEND-001 | #133 | Build customer and operator Marketplace applications | P2/P3 |
| API-003 | #135 | Add pagination, filtering, and sorting to list APIs | P2 |
| SAAS-001 | #136 | Add business subscription plans and platform billing | P3 |
| REALTIME-001 | #137 | Add real-time queue and booking status updates | P3 |
| GEO-002 | #138 | Add traffic-aware travel time for recommendations | P3 |

## 9. Phase 6 — Data-Driven Recommendation Evolution

| ID | GitHub | Backlog Item | Priority | Preconditions |
| --- | ---: | --- | --- | --- |
| ML-001 | #134 | Evolve recommendations with historical prediction and personalization | P4 | Stable REC-001, persistence, isolation, observability, and sufficient clean historical data |

Potential later outcomes:

- Predicted queue wait and completion time.
- Demand forecasting by branch/service/time.
- Lower-demand visit-time suggestions.
- Privacy-controlled preference learning.
- Queue/capacity anomaly detection.

## 10. Delivery Rules

- Finish Phase 0 and the critical Phase 1 queue/workflow issues before implementing Marketplace recommendations.
- Build a rule-based, explainable recommendation engine before considering ML.
- Treat availability eligibility and recommendation ranking as separate concerns.
- Keep all new issues as top-level issues unless the project intentionally adopts sub-issues later.
- Use `staging` as the current integration source of truth under the CI-001 branch strategy.
- Do not claim production readiness until persistence, tenant isolation, audit/security hardening, observability, and deployment hardening are complete.

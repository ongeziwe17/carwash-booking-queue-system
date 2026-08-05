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
| Users | CRUD-style user record APIs and duplicate-email validation | API still exposes unsafe domain fields; tracked by API-001. |
| Vehicles | CRUD, owner association, and duplicate plate validation during creation | Update/deletion integrity needs hardening. |
| Services | Global service catalogue CRUD and activate/deactivate workflows | Branch-specific offerings are not implemented. |
| Bookings | Create, retrieve, update, confirm, cancel, future-time validation, ownership validation, inactive-service rejection, and exact-slot capacity | Rescheduling, cancellation windows, and branch context are not implemented. |
| Queues | Create, retrieve, manual position update, call, start, complete, and delete | Ordering is client-managed and booking state is not fully synchronized. |
| Notifications | In-app notification creation and recent lookup by user | Read APIs and external delivery are incomplete. |
| Reports | Basic in-memory daily summary | Tenant/branch analytics and revenue reporting are future work. |
| API/Docs | Swagger/OpenAPI and written API documentation | Contract mismatches remain. |
| Testing | Service-layer and API workflow tests including negative booking/queue scenarios | Isolation and quality gates need improvement. |
| Packaging | Maven, Docker, and Docker Compose | Production deployment and CI branch flow need hardening. |

Completed issue cleanup:

- #15 — Stronger booking creation validation.
- #19 — Service catalogue management.
- #102 — Implemented/planned documentation alignment.
- #103 — Booking time-slot and capacity validation.

## 3. Phase 0 — Immediate Hardening

| ID | GitHub | Backlog Item | Priority | Status |
| --- | ---: | --- | --- | --- |
| API-001 | #12 | Protect user registration and response contracts | P0 | Open |
| SEC-001 | #24 | Store credentials securely | P0 | Open |
| API-002 | #106 | Standardize request validation and error contracts | P0 | Open |
| DATA-001 | #107 | Enforce aggregate and repository integrity | P0 | Open |
| CI-001 | #109 | Align CI/CD and branch promotion with staging delivery | P0 | Open |
| TEST-001 | #110 | Improve test isolation and quality gates | P1 | Open |
| CONFIG-001 | #111 | Externalize runtime policy configuration | P1 | Open |
| DOCS-001 | #108 | Align OpenAPI and written API contracts | P1 | Open |

## 4. Phase 1 — Complete Booking and Queue Management

| ID | GitHub | Backlog Item | Priority | Depends On |
| --- | ---: | --- | --- | --- |
| QUEUE-001 | #16 | Enforce queue-entry eligibility and uniqueness | P0 | API-002, DATA-001 |
| QUEUE-002 | #17 | Automate queue ordering, recalculation, and wait estimates | P0 | QUEUE-001 |
| WORKFLOW-001 | #20 | Synchronize booking and queue lifecycles | P0 | QUEUE-001 |
| QUEUE-003 | #112 | Implement true call-next queue behaviour | P1 | QUEUE-001, QUEUE-002 |
| BOOKING-001 | #113 | Add rescheduling and configurable cancellation windows | P1 | CONFIG-001, WORKFLOW-001 |
| AVAIL-001 | #114 | Add single-location service availability API | P1 | CONFIG-001, DATA-001 |

Phase exit criteria:

- Only eligible confirmed bookings enter one active queue.
- Queue positions and estimates are server-managed.
- Booking and queue states remain consistent.
- Customers can query availability before booking.

## 5. Phase 2 — Marketplace Business and Branch Foundation

| ID | GitHub | Backlog Item | Priority | Depends On |
| --- | ---: | --- | --- | --- |
| MKT-001 | #115 | Add Marketplace business and branch registration | P1 | Phase 0 |
| MKT-002 | #116 | Add branch operating hours and temporary closures | P1 | MKT-001, CONFIG-001 |
| SERVICE-001 | #117 | Add branch-specific service offerings and capacity | P1 | MKT-001, MKT-002, DATA-001 |
| OPS-001 | #118 | Scope bookings, queues, notifications, and reports to branches | P1 | SERVICE-001, WORKFLOW-001, QUEUE-002 |
| GEO-001 | #119 | Add branch distance calculation and public discovery | P1 | MKT-001, SERVICE-001 |
| AVAIL-002 | #120 | Add branch-aware availability search | P1 | MKT-002, SERVICE-001, OPS-001, GEO-001 |

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

## 7. Phase 4 — Persistence, Security, and Multi-Tenancy

| ID | GitHub | Backlog Item | Priority | Depends On |
| --- | ---: | --- | --- | --- |
| DATA-002 | #122 | Add PostgreSQL persistence, migrations, and transaction boundaries | P1 | Stable Marketplace domain |
| TEST-002 | #123 | Add PostgreSQL integration tests with Testcontainers | P1 | DATA-002 |
| SEC-002 | #13 | Authenticate users securely | P1 | SEC-001, API-001 |
| SEC-003 | #22 | Enforce role-based access control | P1 | SEC-001, SEC-002 |
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
- Use `staging` as the current source of truth until CI-001 changes the branch strategy.
- Do not claim production readiness until persistence, authentication, RBAC, tenant isolation, observability, and deployment hardening are complete.

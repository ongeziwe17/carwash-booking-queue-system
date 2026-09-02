# Changelog

## Unreleased

### Added

- Implemented AUDIT-001 (#125) as a dedicated append-only `audit` capability. Sensitive success events commit in the authoritative business transaction, denied/failed events persist through an isolated write after rollback, and both in-memory and PostgreSQL adapters preserve equivalent semantics.
- Added Flyway `V5__security_and_operational_audit.sql`, immutable historical identifiers, JSONB metadata constraints, tenant/actor/action/resource date indexes, and `AUDIT_READ` for business owners and platform administrators.
- Added `GET /api/audit-records` with bounded newest-first cursor filtering, authenticated-tenant enforcement for owners, explicit tenant/platform scopes for administrators, and no write/delete audit API.
- Added authentication/authorization failure capture, safe correlation identifiers, metadata allowlisting/redaction, structured categorical audit logging, validated retention/query/metadata configuration, Java/PostgreSQL regressions, and Bruno acceptance coverage.

### Changed

- Corrected customer and platform-administrator booking audit scope so successful create, update, reschedule, confirmation, cancellation, and deletion events derive `business_id` from the canonically locked branch/booking inside the authoritative write transaction. Actor snapshots remain membership-accurate, while missing/foreign failures remain unscoped and actor-safe.
- Enforced Marketplace tenant isolation with one Identity-owned membership per operational user, server-validated `tenant_id` JWT claims, bounded tenant access context, tenant-scoped application/repository operations across Marketplace and operational resources, customer subject scope, explicit platform-admin paths, global-catalogue restrictions, safe foreign-resource `404` responses, discovery-specific DTOs, additive Flyway V4 constraints/indexes, and in-memory/PostgreSQL/API/Bruno coverage.
- Added an explicit PostgreSQL persistence profile with Flyway-owned schema, module-local JPA adapters for every repository port, transaction/after-commit abstraction, optimistic versions, cross-instance advisory locks for booking capacity and queue ordering, lossless nanosecond mappings, persistent Docker Compose storage, Testcontainers concurrency/restart tests, and CI acceptance coverage.
- Corrected BEST_OVERALL response formatting to round the unrounded internal score once, keep scores bounded, and deterministically reconcile six-decimal component contributions without changing ranking.
- Added explainable rule-based Marketplace branch recommendations with five preferences, one AVAIL-002 candidate set, raw-value deterministic ranking, normalized BigDecimal score breakdowns, validated weights/radius, customer-safe explanations, RBAC, OpenAPI, Bruno, and architecture coverage.
- Excluded both offset-specific occurrences of an ambiguous DST fall-back branch-local start from AVAIL-002 search, keeping every advertised result representable by the current branch-local booking contract while preserving gap/overlap rejection on booking writes.
- Corrected branch-aware slot alignment to start at each continuous Marketplace operating window (including merged adjacent and overnight intervals), with split windows resetting the anchor; added multi-business capacity-isolation and real branch-queue estimate regressions.
- Added branch-aware exact-instant availability search with complete Marketplace schedule/closure windows, branch offering terms and overlapping configured capacity, same-day queue estimates, optional Haversine radius filtering, and one shared authoritative booking decision.
- Added authenticated nearby Marketplace branch discovery with a replaceable distance port, deterministic Haversine calculations, radius/service/open-at filtering, bounded responses, and Java/OpenAPI/Bruno coverage.
- Corrected queue completion so already-started work may reach `COMPLETED` after parent business, branch, offering, or reusable-service deactivation while canonical booking/queue scope integrity remains mandatory and inactive state continues to block new/call/start work.
- Scoped every new booking and queue entry to a canonical Marketplace branch and Catalog service offering, partitioned queue ordering/waits/call-next by branch, added branch filters and scoped daily reports, and added bounded branch context to notifications.
- Added Catalog-owned branch service offerings with independent price, duration, configured concurrent capacity, activation lifecycle, effective/discoverable projections, seven protected APIs, service-deletion integrity, and Java/OpenAPI/Bruno coverage.
- Added Marketplace branch weekly operating schedules, temporary closure lifecycle, timezone-aware open-status decisions, six protected APIs, and matching Java/OpenAPI/Bruno coverage.
- Aligned README and product documentation to clearly separate implemented backend workflows, partially implemented foundations, planned near-term work, and future SaaS hardening.
- Removed unused design-pattern example code that was not part of the running backend workflows.
- Refocused README and product documentation toward SaaS product readiness.
- Removed repository artifacts that existed only as project-management or review evidence.
- Preserved backend APIs, Docker setup, Maven configuration, and product workflow tests.

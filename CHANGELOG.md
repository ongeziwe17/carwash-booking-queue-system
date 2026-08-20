# Changelog

## Unreleased

### Changed

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

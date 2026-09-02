# Car Wash API Bruno Acceptance Suite

This directory contains the external HTTP acceptance suite for the Car Wash Booking and Queue Management API. It complements—rather than replaces—the Java unit, repository, service, and Spring integration tests.

The collection uses Bruno **OpenCollection YAML** (`opencollection.yml` plus `.yml` request files) so requests, tests, environments, RBAC expectations, and workflows are reviewable in Git.

> **LOCAL/TEST ONLY.** The collection contains destructive operations. `00-setup/00-safety-and-run-init.yml` stops execution unless `baseUrl` points to `localhost`/`127.0.0.1` or the environment explicitly sets `allowRemoteDestructiveTests=true`. No production environment is committed.

## Collection maps

- [Current endpoint inventory](ENDPOINT-INVENTORY.md)
- [RBAC matrix](RBAC-MATRIX.md)
- [Endpoint test matrix](TEST-MATRIX.md)

## Install Bruno Desktop

Install Bruno Desktop, then open this directory directly as a collection:

```text
tests/bruno/carwash-api
```

The numeric folder prefixes preserve deterministic sequential execution.

## Install Bruno CLI

```bash
npm install -g @usebruno/cli
bru --version
```

CI pins Bruno CLI **4.0.0**, the current release selected for this suite, so collection-format changes do not silently alter the release gate.

## Start the API

Using Docker Compose:

```bash
cp .env.example .env
# Replace the JWT placeholder and configure a bootstrap administrator locally.
docker compose up --build
```

Compose runs the unchanged collection against the durable `postgres` profile. The named database volume survives API/container restart, and CI starts a clean PostgreSQL service with run-scoped credentials before executing all requests. Use `docker compose down --volumes` only when a deliberate local data reset is intended.

Or use the Maven startup method documented in the root README after exporting the required application variables.

Confirm readiness:

```bash
curl --fail http://localhost:8080/v3/api-docs
```

## Configure secrets

The Git-versioned `local` and `ci` environments contain only safe values such as `baseUrl`. Secrets come from process environment variables and tokens remain Bruno runtime variables.

Set these before a complete run:

```text
BRUNO_BOOTSTRAP_ADMIN_EMAIL
BRUNO_BOOTSTRAP_ADMIN_PASSWORD
BRUNO_TEST_USER_PASSWORD
```

`BRUNO_BOOTSTRAP_ADMIN_*` must identify the application's configured bootstrap platform administrator. `BRUNO_TEST_USER_PASSWORD` is used only for run-scoped test identities. Do not put any of these values into the YAML environment files.

Bash example:

```bash
export BRUNO_BOOTSTRAP_ADMIN_EMAIL='local-admin@example.test'
read -s -p 'Bootstrap admin password: ' BRUNO_BOOTSTRAP_ADMIN_PASSWORD && echo
export BRUNO_BOOTSTRAP_ADMIN_PASSWORD
read -s -p 'Test user password: ' BRUNO_TEST_USER_PASSWORD && echo
export BRUNO_TEST_USER_PASSWORD
```

PowerShell example:

```powershell
$env:BRUNO_BOOTSTRAP_ADMIN_EMAIL = 'local-admin@example.test'
$env:BRUNO_BOOTSTRAP_ADMIN_PASSWORD = Read-Host 'Bootstrap admin password'
$env:BRUNO_TEST_USER_PASSWORD = Read-Host 'Test user password'
```

## Run manually

```bash
cd tests/bruno/carwash-api
bru run --env local
```

Run only requests containing tests/assertions:

```bash
bru run --env local --tests-only
```

Target useful groups:

```bash
bru run --env local --tags=smoke
bru run --env local --tags=rbac
bru run --env local --tags=security
bru run --env local --tags=availability
bru run --env local --tags=marketplace
bru run --env local --tags=recommendation
```

Bruno executes sequentially by default; do **not** use `--parallel` for the full stateful workflow suite.

## Reports

```bash
mkdir -p results

bru run --env local \
  --reporter-html results/bruno-report.html \
  --reporter-json results/bruno-report.json \
  --reporter-junit results/bruno-junit.xml \
  --reporter-skip-all-headers \
  --reporter-skip-body
```

`results/` is ignored by Git. Reports omit request/response bodies and all headers so passwords, bearer tokens, and login responses are not persisted. CI uploads these three files even when the acceptance command fails where Bruno produced them.

## Test data and chaining

The first setup request creates one run-scoped identifier such as `20260807-1929-a1b2`. User IDs, emails, vehicles, services, bookings, and queue entries include that run ID. This makes repeated runs against either persistent or in-memory application state collision-resistant without relying on global IDs such as `u1` or `b1`.

The setup workflow:

1. performs the destructive-target safety check;
2. logs in the configured bootstrap administrator;
3. registers Customer A, Customer B, a staff candidate, and an owner candidate through the real public endpoint;
4. promotes STAFF and BUSINESS_OWNER through the real admin role endpoint;
5. logs each role in through `/api/auth/login` and stores tokens only as runtime variables.

Resource creation requests store response IDs as runtime variables where chaining improves readability. No valid JWT is forged.

## Security model under test

The collection verifies:

- 401 for missing/malformed/tampered authentication;
- 403 for authenticated users without the required authority;
- cross-customer ownership isolation;
- strict unknown-property rejection and Bean Validation errors;
- safe standard error responses and sensitive-data absence;
- DATA-001 duplicate/dependency/lifecycle protections;
- QUEUE-001 confirmed-booking, canonical branch/offering inheritance, service-consistency, lifecycle, and active-entry uniqueness rules;
- QUEUE-002 server-managed branch positions, offering-duration waits, branch-only movement/deletion/completion rebalance, and obsolete position rejection;
- QUEUE-003 required-branch call-next ordering, non-waiting skip, explicit by-ID call override, and no-waiting 404 behavior;
- BOOKING-001 focused CREATED/CONFIRMED rescheduling, shared cutoff enforcement, status preservation, target-slot validation, queue/service-state rejection, notification creation, and generic-update bypass prevention;
- AVAIL-001 generated service/date slots, duration/closing fit, deterministic ordering, global remaining capacity, full-slot booking consistency, cancellation release, inactive/unknown/past rejection, and authenticated service-read access;
- MKT-001 business/branch registration, bounded responses, immutable ownership, coordinates/timezone validation, lifecycle actions, effective activity/public discovery, Marketplace RBAC, and complete unauthenticated-operation coverage;
- MKT-002 atomic weekly schedules, multiple/overnight intervals, timezone-aware open status, temporary-closure override/cancellation, overlap rejection, Marketplace scheduling RBAC, and complete unauthenticated-operation coverage;
- SERVICE-001 branch-specific price/duration/configured-capacity terms, multi-branch reuse of global service definitions, offering and parent lifecycle discovery, public-discovery suppression, duplicate/reference validation, deletion integrity, Marketplace RBAC, and complete unauthenticated-operation coverage;
- OPS-001 required booking branch/offering scope, same-branch offering changes, cross-branch rejection, inherited queue scope, branch filters, branch-partitioned call-next/positions/waits, explicit branch/business reports, and bounded notification context;
- GEO-001 deterministic Haversine distance, radius/service/open-at filters, output rounding, lifecycle/public visibility, Marketplace-read authorization, validation, and temporary-closure workflows;
- AVAIL-002 shared booking/search decisions, unambiguous branch-local starts, continuous-window slot anchors, complete branch schedule windows and closures, effective offering terms, overlapping configured capacity, same-day branch-isolated queue estimates, optional raw-distance filtering, bounded multi-business responses, and service-read authorization;
- REC-001 reuse of one AVAIL-002 candidate set across all five preferences, deterministic raw-metric ranking/ties, normalized component and weighted-contribution reconciliation, future null queue/total behavior, bounded explanations, server radius enforcement, and Marketplace-read authorization;
- TENANT-001 platform-admin membership onboarding/removal, old-token invalidation, owner/staff tenant scope, foreign-resource safe `404`, tenant enumeration denial, owner second-business denial, global catalogue restriction, and discovery-safe DTOs;
- AUDIT-001 owner/admin read scope, tenant override denial, staff/customer denial, explicit platform scope, absent write API, and unauthenticated coverage for all 72 operations;
- current queue states (`WAITING`, `CALLED`, `IN_PROGRESS`, `COMPLETED`);
- real role changes and token acquisition;
- final-platform-administrator protection without deleting or demoting the bootstrap admin.

The run-scoped queue-ordering workflow captures its branch baseline, moves a dedicated entry to the front, proves
that branch call-next selects it, starts it, proves the next call skips that in-progress entry, verifies the documented
no-waiting 404, and completes both entries so the suite remains rerunnable. Expiry/signing-clock manipulation and
internal concurrency guarantees remain Java-test responsibilities.

The run-scoped booking-rescheduling workflow exercises full-slot and same-customer/vehicle rejection, active queue
blocking, inactive associated services, and in-service/completed/cancelled states. Exact cutoff boundaries,
repository-failure rollback, notification-failure handling, and final-slot concurrency remain deterministic Java-test
responsibilities because the acceptance environment does not expose test-only clock or failure controls.

The run-scoped legacy availability workflow uses an isolated future date and services. It proves that its global exact-start slot disappears at configured capacity, authoritative creation enforces the selected offering's own capacity, cancellation restores both views, and a 60-minute service is never advertised past the closing boundary. Availability reads remain point-in-time snapshots and do not reserve capacity.

The run-scoped Marketplace workflow has the platform administrator register two businesses, assigns/reassigns explicit owner/staff memberships, re-authenticates after stale-token rejection, and exercises management only within canonical scope. Negative requests prove safe foreign-business/offering `404`, tenant-scoped enumeration, second-business denial, owner global-catalogue denial, and explicit administrator cross-tenant operations. A following operational workflow books and queues across both businesses while tenant operators remain confined to their assignment. GEO, AVAIL-002, and REC-001 retain authenticated customer-safe multi-business discovery. The audit workflow proves the only audit endpoint is bounded and read-only. Deterministic Java integration tests cover atomic audit/business rollback, authentication failure privacy, concurrency, booking, queue, notification, report, JWT, repository, and PostgreSQL constraints. Tenant isolation and auditability do not imply billing, SIEM/archive integration, compliance certification, database-per-tenant isolation, federation, driving routes, reservations, personalization, dynamic pricing, or staff/bay allocation.

The exact collection inventory is **562 HTTP requests and 1,746 explicit `test(...)` assertions**, covering **75/75 OpenAPI operations** and **73/73 protected-operation unauthenticated paths**. The notification folder covers keyset pages, unread filtering/count, mark-one/repeat, mark-all/repeat, subject and tenant isolation, explicit administrator scope, unauthenticated rejection, scalar privacy, and the absence of arbitrary create/delete operations.

## Cleanup

Cleanup is best-effort and uses only public API operations permitted by the domain. Some cancelled/completed history intentionally remains because DATA-001 preserves historical records.

For a fully clean manual run, restart the local application before executing the complete suite.

No repository-reset endpoint, authentication bypass, test-only HTTP endpoint, or hard-coded privileged user is added by this suite.

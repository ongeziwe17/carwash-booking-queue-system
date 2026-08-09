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

The first setup request creates one run-scoped identifier such as `20260807-1929-a1b2`. User IDs, emails, vehicles, services, bookings, and queue entries include that run ID. This makes repeated runs against the same in-memory application instance collision-resistant without relying on global IDs such as `u1` or `b1`.

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
- QUEUE-001 confirmed-booking, active-service, matching-service, and active-entry uniqueness rules;
- QUEUE-002 server-managed global positions, cumulative waits, full movement/deletion/completion rebalance, and obsolete position rejection;
- current queue states (`WAITING`, `CALLED`, `IN_PROGRESS`, `COMPLETED`);
- real role changes and token acquisition;
- final-platform-administrator protection without deleting or demoting the bootstrap admin.

Expiry/signing-clock manipulation and internal concurrency guarantees remain Java-test responsibilities.

## Cleanup

Cleanup is best-effort and uses only public API operations permitted by the domain. Some cancelled/completed history intentionally remains because DATA-001 preserves historical records.

For a fully clean manual run, restart the local application before executing the complete suite.

No repository-reset endpoint, authentication bypass, test-only HTTP endpoint, or hard-coded privileged user is added by this suite.

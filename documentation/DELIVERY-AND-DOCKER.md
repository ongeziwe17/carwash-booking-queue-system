# Delivery and Docker Workflow

## Branch promotion model

`staging` is the active integration branch.

```text
feature/fix/security/ci branch
        ↓ pull request
staging
        ↓ controlled promotion pull request
develop
        ↓ release promotion pull request
master
        ↓
v* release tag
```

Feature and maintenance branches are validation inputs, not releases. Container publishing occurs only after a successful push to `staging`, `develop`, `master`, or a `v*` tag.

### Promotion rules

- Work branches target `staging`.
- Only `staging` is promoted into `develop`.
- Only `develop` is promoted into `master`.
- Version tags are created from an approved release commit after promotion.
- Direct pushes should be disabled on `staging`, `develop`, and `master` through repository branch protection.

## Recommended branch protection

Protect `staging`, `develop`, and `master` with:

- Pull requests required before merging.
- At least one approval where appropriate.
- Maven verification required.
- Workflow syntax validation required.
- Docker validation required, including Compose validation and image build.
- Review conversations resolved before merging.
- Force pushes disabled.
- Direct pushes disabled where appropriate.

The repository workflow validates promotion sources, but repository settings remain the enforcement point for approvals and direct-push restrictions.

## Required GitHub secrets

| Secret | Purpose |
|---|---|
| `CI_JWT_SECRET` | Test-only JWT signing secret for Maven, OpenAPI, Compose, and container validation. |
| `DOCKER_USERNAME` | Docker Hub namespace and login username used only by the publishing job. |
| `DOCKER_PASSWORD` | Docker Hub password or access token used only by the publishing job. |

`CI_JWT_SECRET` must be Base64 representing at least 32 random bytes. Generate a value without committing or printing it:

```bash
openssl rand -base64 32
```

GitHub Container Registry authentication uses the workflow `GITHUB_TOKEN`. The publishing job alone receives `packages: write`; all other jobs use read-only repository access.

## CI validation

Pull requests into `staging`, `develop`, and `master` run:

1. GitHub Actions workflow syntax validation with actionlint.
2. Java 21 Maven verification using `./mvnw --batch-mode clean verify`.
3. Test-report and JaCoCo artifact upload, including failed-test output where available.
4. Verified JAR upload after a successful Maven build.
5. OpenAPI export from `/v3/api-docs` with bounded startup retries and process cleanup.
6. Dockerfile linting with Hadolint.
7. Temporary `.env` generation and `docker compose config --quiet` validation.
8. A BuildKit image build with no registry login and no push.
9. Non-root runtime-user inspection.
10. Docker image-history inspection for secret or `.env` references.
11. Container startup and public OpenAPI smoke testing with bounded retries and cleanup.
12. A Trivy `HIGH` and `CRITICAL` vulnerability baseline report.
13. A blocking gate for fixed `CRITICAL` findings.

PR validation does not require Docker Hub credentials and never publishes JAR releases or images.

## Published image tags

Both Docker Hub and GitHub Container Registry receive the same approved tags.

| Event | Tags |
|---|---|
| Push to `staging` | `staging`, `staging-YYYYMMDD-HHmmss`, `sha-abcdef0` |
| Push to `develop` | `develop`, `develop-YYYYMMDD-HHmmss`, `sha-abcdef0` |
| Push to `master` | `master`, `latest`, `sha-abcdef0` |
| Push of `v0.1.0` | `v0.1.0`, `v0.1.0-YYYYMMDD-HHmmss`, `sha-abcdef0` |

Only `master` receives `latest`. Staging and version tags never assign it.

## Coverage gate

JaCoCo produces `target/site/jacoco/jacoco.xml` and the HTML report during `verify`.

The first CI-001 workflow run measured **83.52% line coverage**: 892 covered lines and 176 missed lines out of 1,068 total lines.

The initial JaCoCo minimum is **80% line coverage**. This floor is conservative enough to accommodate small instrumentation and generated-code changes while remaining close to the measured baseline and preventing a material coverage regression. Future changes must not reduce line coverage below 80%.

## Local environment

Create a local runtime file from the safe template:

```bash
cp .env.example .env
```

Replace `REPLACE_WITH_BASE64_ENCODED_32_BYTE_SECRET` with a locally generated value. Never commit `.env`.

Validate and run:

```bash
docker compose config
docker compose build
docker compose up
```

Detached run:

```bash
docker compose up --build -d
```

Logs:

```bash
docker compose logs -f carwash-api
```

Shutdown:

```bash
docker compose down
```

Remove stopped services and orphan containers without deleting persistent data:

```bash
docker compose down --remove-orphans
```

The current application has no PostgreSQL service and no dedicated liveness/readiness endpoint. CI smoke testing uses the existing public OpenAPI endpoint. Database persistence belongs to DATA-002, while production health endpoints and deployment hardening belong to OBS-001 and DEPLOY-001.

## Docker image design

The Dockerfile uses:

- A Maven and Eclipse Temurin Java 21 build stage.
- Dependency-definition layers before source code for improved caching.
- Maven BuildKit cache mounts.
- Packaging with tests skipped because Maven verification runs first in CI.
- A predictable `target/carwash-api.jar` artifact.
- An Eclipse Temurin Java 21 JRE runtime stage.
- A dedicated non-root `appuser`.
- Only the application JAR copied into the runtime image.
- An exec-form Java entrypoint.

Runtime secrets and environment-specific values are never included in image layers.

## Vulnerability baseline

The first complete image scan, GitHub Actions run #122, reported 18 fixed findings: 3 `CRITICAL` and 15 `HIGH`. The three critical findings affected embedded Tomcat 11.0.21 and were remediated by pinning the Spring Boot-managed Tomcat line to 11.0.24. The fixed-critical gate remains blocking.

The initial high-severity baseline remains visible in the uploaded Trivy JSON report and is documented in [Container Vulnerability Baseline](CONTAINER-VULNERABILITY-BASELINE.md). Final vulnerability governance remains part of DEPLOY-001 and later supply-chain work; findings are not hidden or globally suppressed.

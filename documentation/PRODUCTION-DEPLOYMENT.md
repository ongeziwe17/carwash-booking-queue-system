# Production runtime guide

This guide describes the application image and the configuration it requires. Hosting is planned on a cloud virtual machine running a container, with infrastructure defined later in a separate repository. This repository does not provision a VM, database, network, reverse proxy, monitoring stack or deployment service. Local Docker Compose is a development and CI fixture, not a production topology.

## Profiles and configuration

| Profile | Persistence | Bootstrap admin | API documentation | Use |
| --- | --- | --- | --- | --- |
| default | In-memory | Disabled unless explicitly configured | Available | Local development only; data disappears on restart. |
| test | In-memory | Disabled | Available to tests | Automated tests. |
| postgres | PostgreSQL and Flyway | Disabled by default | Available | Local Compose and controlled development. |
| staging | PostgreSQL and Flyway | Forced disabled | Disabled | Durable preproduction runtime. |
| production | PostgreSQL and Flyway | Forced disabled | Disabled | Durable production runtime. |

`staging` and `production` activate `postgres` through Spring profile groups. An early startup guard rejects missing PostgreSQL configuration, disabled Flyway or clean protection, excluded database autoconfiguration, enabled bootstrap administration or API documentation, and simultaneous staging/production profiles. It reports a generic error without printing configuration values. The JWT key must decode from Base64 to at least 32 bytes under the existing security validation. These profiles do not turn on optional external delivery or payment providers.

Supply the following from the environment's secret facility, never in the image, Git or build arguments: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SECURE_JWT_SECRET`, and separate `CARWASH_SCRAPE_USERNAME` and `CARWASH_SCRAPE_PASSWORD_HASH` for Prometheus scraping. The scrape hash uses the existing `{bcrypt}` format. The existing non-secret settings include `SPRING_APP_NAME`, `SERVER_PORT`, `SECURE_JWT_ISSUER`, `SECURE_JWT_ACCESS_TOKEN_TTL`, the password policy and pool settings. Use separate credentials and databases per environment. Configure database TLS and certificate verification for the selected database endpoint.

The optional `CARWASH_TRACE_OTLP_ENDPOINT` stays empty until a trusted collector exists. Collector failure does not affect readiness. A stable JWT key is required across restarts; rotating it invalidates previously issued tokens. Do not log credentials when validating configuration.

## Image and container contract

The Java 21 Dockerfile pins builder and JRE images to verified immutable digests. The runtime contains the packaged JAR without source or build tools. Java runs directly as PID 1 under UID/GID 10001; no shell entrypoint is needed for SIGTERM. The image uses `/tmp` for temporary files and user-home data, and starts with a 20% heap target and a 55% maximum of detected container memory to leave native-memory headroom.

Run the container with a read-only root filesystem, a bounded writable `/tmp` (the local Compose fixture uses 64 MiB), all Linux capabilities dropped, no privilege escalation and the runtime's default seccomp profile. Local Compose also limits memory to 768 MiB, processes to 256 and stop grace to 45 seconds. These are initial test values, not measured production sizing. Measure peak memory, temporary storage, thread count and latency before choosing VM/container limits. Keep the Java process's 20-second graceful shutdown interval shorter than the container termination grace interval.

The existing CI builds and scans the image, checks image history for credential references and starts the read-only non-root container. Refresh pinned base digests deliberately through a rebuilt image and the same security gates. Use unique version/SHA tags and promote one verified image digest between environments; do not rebuild a release differently for staging and production.

## Health, traffic and migration behavior

`/actuator/health/liveness` has no database dependency. `/actuator/health/readiness` includes PostgreSQL, so an outage should remove traffic without restarting a live process. The minimal probes do not require database-backed JWT authentication. `/actuator/prometheus` requires its separate Basic credentials; other diagnostic management endpoints remain inaccessible. Protect scraping with TLS and network restrictions.

Terminate TLS at a trusted reverse proxy, restrict direct backend access, strip client-supplied `Forwarded` and `X-Forwarded-*` headers, and set any trusted headers at that boundary. The app currently ignores forwarded headers, so a public client cannot change its effective scheme or host. Configure and verify HTTPS redirects, certificates and HSTS at the eventual proxy. Application `nosniff` and frame-denial headers are exercised in CI. Do not enable wildcard CORS. Staging and production disable Swagger UI and `/v3/api-docs`; the CI/test pipeline still checks the OpenAPI contract.

Startup runs Flyway additive migrations and Hibernate schema validation. Flyway validation and clean protection remain enabled. V1–V8 are unchanged. The eventual release process must decide migration ownership and assess compatibility before rollout or rollback. Reverting an application image never reverses a database migration; do not automate destructive downgrades or volume deletion. Observe readiness, error rates, pool saturation, Flyway logs and latency during a release, and drain in-flight requests before stopping the old container. CI tests real HTTP graceful shutdown, while VM proxy draining and availability require validation in the later infrastructure work.

## Local verification commands

These examples use the repository's disposable local Compose stack, not a production deployment:

| Command | What it does and state effect |
| --- | --- |
| `docker compose config --quiet` | Validates local Compose configuration; reads files and does not start containers. Requires the local `.env` placeholders to be replaced. |
| `docker compose up --build -d` | Builds the image and **starts/changes** local API and PostgreSQL containers and their persistent local volume. |
| `CARWASH_RUNTIME_PROFILE=staging docker compose up -d --force-recreate carwash-api` | **Recreates** only the local API in staging mode against the local database; this is a profile smoke test, not a staging deployment. The default profile is `postgres`. |
| `docker compose down` | **Stops/removes** local containers and network; retains the local PostgreSQL volume. |

The CI Docker job tests both staging and production profiles against disposable PostgreSQL, checks liveness/readiness and management access, and restores the default local profile for other checks. Its VM-independent runtime checks do not prove cloud networking, database high availability, backups, TLS or production rollout safety. Those belong to the future infrastructure repository.

# Production observability

Actuator, Prometheus metrics, Logstash JSON logs and Micrometer/OpenTelemetry traces cover the capabilities currently implemented. No production vendor, purchased domain or external notification/payment account is needed. The application remains Java 21 / Spring Boot 4.0.6 with the existing Tomcat alignment. V1–V6 are unchanged.

## Endpoint access policy

Management uses the application port with a dedicated first-priority security chain. Restrict this port and its `/actuator` routes through network/ingress policy in production. TLS is required for Basic authentication outside an isolated local network.

| Endpoint | Method | Access | Response / meaning |
|---|---|---|---|
| `/actuator/health/liveness` | GET | Public; bearer headers ignored | Only `status`; ability to continue running. No database or collector dependency. |
| `/actuator/health/readiness` | GET | Public; bearer headers ignored | Only `status`; PostgreSQL profile includes database availability. Unhealthy dependencies produce HTTP 503. Default/in-memory/test needs no database. |
| `/actuator/prometheus` | GET | Dedicated Basic scrape identity with `ROLE_SCRAPE` | Prometheus text. Empty credentials disable access, not application startup. |
| All other management paths, including root health/discovery | Any | Denied | No diagnostic access, even for scraper. Anonymous requests normally receive 401; authenticated identities receive 403. |
| Probe and scrape paths | Other methods, including OPTIONS | Denied | No management mutations. |
| Customer API and OpenAPI | Existing methods | Existing policy | Business authentication, error bodies and operation contract are unchanged. |

Customer, staff, business-owner and platform-admin JWTs cannot authenticate to management. The scraper is a separate identity held in memory, not a marketplace account; its authentication never consults PostgreSQL. Only `health` and `prometheus` are exposed, with read-only access; default endpoint access is `none`. Env, configprops, heapdump, threaddump, loggers, shutdown, info and metrics diagnostics remain inaccessible. Health details/components and discovery links are disabled.

Database readiness uses Boot's datasource health contributor and the configured Hikari connection timeout. During an outage the response may wait for that timeout (default 30 seconds); set a suitable `SPRING_DATASOURCE_CONNECTION_TIMEOUT_MS` and align orchestrator probe deadlines/periods. Liveness does not acquire a connection. The local external readiness probe times out after five seconds and treats a slow/outage response as failure. Failed collector exports affect neither probe.

## Configuration and credential rotation

| Variable | Default | Meaning |
|---|---|---|
| `CARWASH_SCRAPE_USERNAME` | Empty | Dedicated scrape username. |
| `CARWASH_SCRAPE_PASSWORD_HASH` | Empty | `{bcrypt}` followed by a bcrypt hash. Both identity fields must be present together. Invalid configuration fails startup. |
| `CARWASH_TRACE_SAMPLE_PROBABILITY` | `0.1` | Boot's parent-based sampling probability for new traces; valid upstream sampled context is respected. Use `1.0` only for controlled development/testing. |
| `CARWASH_TRACE_OTLP_ENDPOINT` | Empty | Empty means no network exporter. Otherwise use the complete HTTP/protobuf `/v1/traces` endpoint. HTTPS for remote collectors; no embedded credentials/query/fragment. |
| `CARWASH_QUEUE_TELEMETRY_REFRESH_MS` | `15000` | Delay between queue aggregate refreshes. Keep positive and avoid aggressive database polling. |

Provision a random dedicated password in a secret manager, supply its bcrypt hash to the application, and supply the plaintext password to Prometheus through a mounted secret file. Never use a user's JWT or commit either credential. In dotenv files, single-quote the entire hash so literal `$` characters survive parsing. The local setup script creates these files for you. Production rotation requires rolling the application's hash and updating/reloading the scraper's password together; a transient scrape outage is expected because one credential is accepted at a time. Keep probe access available throughout rotation. Avoid placing plaintext secrets in shell arguments.

Boot's direct `management.opentelemetry.tracing.export.otlp.enabled` setting is deliberately **false**. The application configures its own privacy-filtering exporter; do not enable a second direct exporter or add an OpenTelemetry Java agent. Production collector authentication/TLS termination belongs to the adjacent trusted collector/gateway; this implementation does not accept provider keys or arbitrary exporter headers.

## Metric inventory

Names below are Prometheus names. Technical target labels (`job`, `instance`) are added by Prometheus. Custom meters do not contain user, booking, tenant, branch, notification or vehicle identifiers.

| Name | Type / unit | Labels | Meaning |
|---|---|---|---|
| `http_server_requests_seconds_*` | Timer: seconds; count, sum, max and histogram buckets | method, uri, status, outcome, exception; le for buckets | HTTP throughput, duration and errors. URI is the route template or `unmatched`, never a raw URL/query. Methods have an `OTHER` fallback and exception is `none`; status identifies failures. Boot caps URI tags at 200. |
| `jvm_*`, `process_*`, `system_*` | Boot standard gauges/counters; units in names | Standard bounded JVM/pool labels | JVM memory/GC/threads, process CPU/uptime and host signals. |
| `hikaricp_connections_*`, `jdbc_connections_*` | Standard connection gauges/timers | pool/name | PostgreSQL pool active/idle/max/pending and acquisition signals. Absent in memory runtime. |
| `carwash_workflow_attempts_total` | Counter, attempts | operation | One attempt at a top-level named operation, including rejected attempts. Reentrant calls of the same operation on one thread are suppressed. |
| `carwash_workflow_outcomes_total` | Counter, outcomes | operation, outcome | `committed` after the outer transaction commits; `rollback` when a provisional return is rolled back; `failed` for a thrown failure. For non-mutating work outside a transaction, committed denotes successful completion. A thrown failure is counted once and is not also counted as rollback. |
| `carwash_workflow_duration_seconds_*` | Timer, seconds; histogram enabled | operation, outcome (`returned`/`failed`), le | Execution duration through the service return/throw boundary. `returned` does not assert commit; use the outcome counter for commit semantics. |
| `carwash_recommendation_candidates_count`, `_sum`, `_max` | Distribution summary, candidates | None | Eligible candidate counts for recommendation requests that reached candidate evaluation, including zero results. Mean is rate(sum)/rate(count). |
| `carwash_queue_depth` | Gauge, entries | None | Current active (`WAITING`, `CALLED`, `IN_PROGRESS`) queue entries in the shared database or this in-memory JVM. Not an accumulating counter. |
| `carwash_queue_estimated_wait_seconds` | Gauge, seconds | None | Mean estimated wait across current active entries; zero when empty. Not observed customer waiting duration. |
| `carwash_queue_snapshot_age_seconds` | Gauge, seconds | None | Time since the last successful aggregate refresh; NaN until first success. |
| `carwash_queue_snapshot_available` | Gauge, boolean 0/1 | None | Whether any aggregate has been obtained. An old successful snapshot remains available; use age to detect staleness. |
| `carwash_trace_export_batches_total` | Counter, batches | outcome (`succeeded`, `failed`, `disabled`) | Asynchronous export completion. Disabled batches are discarded locally. This does not count spans lost because the bounded SDK queue filled. |

`operation` is a fixed enum: `booking_create`, `booking_update`, `booking_reschedule`, `booking_cancel`, `booking_confirm`, `booking_delete`; `queue_create`, `queue_move`, `queue_call`, `queue_start`, `queue_complete`, `queue_delete`; `availability_search`, `availability_decision`, `recommendation`, `notification_create`, `audit_append`. Persistence read/write produces spans, not extra workflow counters/timers. Different nested operations describe distinct work (for example a booking confirmation and its in-app notification creation). Queue counters describe service operations, not each internal reordered row. Audit append success means a record persisted, regardless of whether its secured audit outcome is SUCCESS, DENIED or FAILURE. Duplicate-suppressed appends are not counted.

PostgreSQL aggregation uses one timeout-limited scalar count/average query every refresh interval, with no entity hydration or per-scrape database access. Multiple API instances see the same shared queue state: **use max, not sum, across those instances**. In memory, repository mutation hooks maintain active-entry totals without full scans. Reads run through the existing transaction coordinator. Gauges cache the last successful state; outages preserve it and age reveals staleness. The SQL aggregate cost scales with active data and the database's query plan; review it as volume grows. No schema migration is required. Histograms increase time-series volume; budget for route count, buckets and replicas.

## Logging privacy and correlation

Boot's supported Logstash JSON formatter is configured with `SafeLogJsonCustomizer`. It exports a field allowlist: timestamp, level, logger/thread, server-owned correlation ID, trace/span IDs and safe events with exception type/stack location where available. Raw framework message text, generic arbitrary application messages, throwable messages, nested causes, stack trace strings, arbitrary MDC and key-value fields are excluded. Framework events retain logger/level/type/location; diagnose startup configuration locally through secured configuration and the failure type rather than exporting raw exceptions.

The single servlet registration of `AuditRequestFilter` wraps API security; it no longer also runs inside the bearer chain. Each request receives a new server-generated `X-Correlation-ID`, which appears in MDC logs and secured audit records. Client correlation values are ignored. Contexts are removed in `finally`, including security failures. Probe/scrape requests do not emit normal request-completion logs. Audit event logs omit audit, actor, tenant and target IDs; required identity/resource information remains in the existing authoritative audit store. Audit success logs wait for transaction completion.

Never add passwords/hashes, JWTs, Authorization/Cookie headers, provider keys, credentials, names, contact details, plates, coordinates, special requests, notification content, SQL bind values, bodies or query strings. Custom events use safe enum/code values and known route templates. New asynchronous request work must explicitly carry the server correlation and trace context and clean it up on the worker; do not copy arbitrary MDC or authentication. This change introduces no asynchronous business work. The scheduled queue sampler is independent background work; the SDK exporter receives already completed spans through its bounded processor.

## Tracing

The framework-neutral `Telemetry` application port has a no-op implementation for direct service construction and a Micrometer adapter composed by bootstrap. Explicit wrappers instrument final/manual services, so execution does not depend on tracing annotations or Spring proxies. Booking/queue, availability, recommendation, in-app notification and audit appends create bounded `carwash.<operation>` spans. Coordinator read/write boundaries create persistence spans around the transaction/lock boundary. PostgreSQL commit and rollback semantics are unchanged.

Incoming HTTP spans use a custom convention with no high-cardinality URL attribute. A final `PrivacySpanExporter` also sanitizes automatic security/framework instrumentation: bounded names (`http.server` or `framework.internal`), allowlisted attributes only, no exception events, status descriptions, links, SQL/URL/header/body data or remote tracestate. Trace and span IDs and parent relationships are retained. Application failures use a synthetic safe error instead of the original Throwable. Structured request/audit correlation works even when tracing is disabled.

There are no external provider HTTP clients at this baseline. Future clients must use Boot's configured RestClient/WebClient builders to propagate context, with bounded spans and privacy review; do not manually forward arbitrary customer headers. No duplicate agent instrumentation is installed.

OTLP export is optional. Batches run off the request thread with queue capacity 1024, batches of 128, five-second scheduling and two-second export deadlines. The exporter makes one attempt per batch, with a one-second connection deadline, and emits no repeated provider-error logs. Collector failure drops telemetry after the deadline without retry growth, booking changes or readiness impact. The local collector also limits memory and retries for at most 30 seconds. Sampling and bounded queues mean traces are diagnostic samples, not an authoritative audit trail.

## Local tooling

Docker Compose >=2.24.4, Docker, Python 3 and the normal `.env` are required. The supplied local scrape/probe targets assume `SERVER_PORT=8080`; change both target URLs if you use a different application port. Normal application/PostgreSQL startup stays unchanged; observability services are an optional override.

1. Create the normal `.env` using the existing configuration guide, then run:

   ```bash
   python3 tools/setup-observability.py
   ```

   This changes local state: it pulls/runs a pinned temporary `htpasswd` container, generates random credentials and writes ignored `.observability` files. It prints no secrets and refuses to overwrite existing credentials. The directory is private (0700); password files are 0444 so unprivileged containers can read their read-only mounts. The Compose hash file is private. Protect this directory like any secret store.

2. Start the optional stack:

   ```bash
   docker compose --env-file .env --env-file .observability/compose.env -f docker-compose.yml -f docker-compose.observability.yml up --detach --build
   ```

   This changes local state: builds/starts the API and database plus pinned Prometheus, Grafana, blackbox probe, collector and Tempo containers and creates local volumes. A one-shot root init container sets Tempo volume ownership; Tempo runs unprivileged. All published local interfaces, including the overridden API/database bindings, use loopback. The collector accepts OTLP HTTP and sends gRPC to Tempo on the private Compose network. Grafana's provisioned dashboard is **HlambaQ Operations**. Prometheus retains 48 hours of local metrics; Tempo retains 24 hours of traces.

3. Verify the complete stack:

   ```bash
   python3 tools/smoke-observability.py
   ```

   This reads health, metrics, Prometheus, Grafana and Tempo. It also sends one unauthenticated API request with a valid sampled trace context, which creates a security audit record, and verifies the app → collector → Tempo path and privacy. It prints check results, not secret values or telemetry bodies.

4. Open Grafana at `http://127.0.0.1:3000` as `observability-admin`; retrieve its password privately from `.observability/grafana-password`. Prometheus is at `http://127.0.0.1:9090`, Tempo at `http://127.0.0.1:3200`. Use Grafana Explore → Tempo for traces. Local UIs depend on the loopback-only binding and must not be exposed as production services.

5. Stop while preserving data:

   ```bash
   docker compose --env-file .env --env-file .observability/compose.env -f docker-compose.yml -f docker-compose.observability.yml down
   ```

   This removes the stack's containers/network and stops the API/database; volumes and credentials remain.

6. Delete local stack data only when intended:

   ```bash
   docker compose --env-file .env --env-file .observability/compose.env -f docker-compose.yml -f docker-compose.observability.yml down --volumes
   ```

   This is destructive: it also deletes PostgreSQL data and all metrics, dashboards/user state and traces for this Compose project. To rotate local credentials, stop the stack, remove only `.observability`, rerun setup and restart. Grafana's initial admin password is set on first database creation; for existing Grafana data rotate through its admin UI rather than assuming a changed file resets the password.

## Alerts

Thresholds are examples that require tuning for traffic, branch count and capacity. `observability/alerts.yml` contains nine sustained rules. Prometheus evaluates rules and displays alerts locally; no Alertmanager or notification routing is provisioned. Connect production routing and ownership explicitly before relying on paging.

| Alert | Trigger | Action |
|---|---|---|
| CarwashHttpLatency | p95 >1s for 10m, >6 requests/minute | Compare workflow spans, database locks/pool waits and workload changes. |
| CarwashHttpErrors | 5xx >5% for 5m, >6 requests/minute | Inspect route templates, safe failure codes, correlation-linked audit and readiness. |
| CarwashScrapeUnavailable | Scrape down for 2m | Check process/network and scrape credentials/rotation; scrape failure alone does not prove business failure. |
| CarwashNotReady | Readiness or its external probe is down for 2m | Check PostgreSQL/pool reachability; compare liveness and avoid restart loops for database outages. |
| CarwashPoolPressure | Active >90% of max with pending callers for 5m | Inspect slow transactions/locks before resizing pools; respect PostgreSQL limits. |
| CarwashQueueBacklog | Fresh global active depth >100 for 15m | Inspect tenant-authorized branch queues and capacity; tune for marketplace size. |
| CarwashQueueWait | Fresh mean estimated wait >30m with >5 active entries for 10m | Inspect staffing, duration estimates and stalled transitions. |
| CarwashQueueSnapshotStale | Snapshot absent or >60s old for 3m | Check scheduler, query timeout and DB readiness; don't interpret stale depth as current. |
| CarwashInAppNotificationFailures | >=3 creation failures and >=5 attempts in 10m, sustained 5m | Inspect notification persistence and safe booking/queue failure events; delivery providers are not involved. |

The dashboard uses max for the shared queue gauges and aggregates counters with rates. Low-volume systems may not trigger latency/error rules; inspect raw counts manually when investigating isolated failures. For separate databases/environments use separate jobs or environment labels and adapt aggregation; never mix independent queues into the shared-database max model.

## Validation and CI

The existing Maven/coverage, ArchUnit, OpenAPI, PostgreSQL durability/concurrency, repeatability seeds 11001/11002, isolation, Docker, workflow and vulnerability gates remain. New tests exercise management permissions, minimal probes, a paused PostgreSQL outage, provisional rollbacks, current queue state, explicit business instrumentation, correlation/audit/log consistency, actual rendered JSON privacy, real parent/child spans and disabled/failed export. The PostgreSQL outage test unpauses its container in `finally`.

Management Bruno requests live in **`tests/bruno/carwash-management`**, separate from the business collection/OpenAPI checker. Set `BRUNO_SCRAPE_BASIC` to base64 of the dedicated `username:password` through your environment, then run its collection:

```bash
bru run --env local --reporter-skip-all-headers --reporter-skip-body
```

Run from that management directory. This performs read-only probes/scrapes/access-denial checks; it writes only any explicitly requested reports. CI provisions ephemeral credentials, masks them and runs both collections. Business OpenAPI operations are unchanged.

```bash
./mvnw --batch-mode clean verify
```

This changes local build output in `target`, compiles Java 21 code, runs all tests/coverage and starts ephemeral PostgreSQL test containers; Docker is required. Re-run with `-Dtest.order.seed=11002` for the second order seed. No test/profile exclusions are committed to the gates.

```bash
python3 tools/validate-observability.py
```

This is read-only validation of YAML, dashboard JSON structure, pins, loopback bindings and alert annotations; Python PyYAML is required. It does not prove runtime connectivity.

```bash
promtool check rules observability/alerts.yml
promtool test rules observability/alerts.test.yml
```

Both are read-only Prometheus semantic checks; the first validates all expressions, the second validates alert behavior and low-traffic suppression. CI uses the pinned Prometheus container's promtool, validates Collector configuration and starts the real stack to verify authenticated scrape, Grafana dashboard provisioning and trace ingestion. Successful structural validation alone is not an executed runtime smoke test.

## Production integration and deferred work

Supply secrets through your deployment secret manager, terminate TLS, restrict management/collector networks, configure targets per replica, add stable environment/service labels and provision alert routing/runbook ownership. Keep the secured audit store authoritative and use correlation IDs for controlled incident investigation. Plan metrics retention/cardinality, queue aggregate load, trace sampling/export drops, local volume growth and telemetry access controls. The global queue averages do not expose tenant/branch diagnostics; inspect those through authorized application tooling.

External email/SMS delivery (#127), booking payments (#128) and subscription billing (#136) remain deferred. The dashboard explicitly labels these as future work, with no fabricated counters, panels or alerts. Add provider operation/latency/safe outcome instrumentation at their future application ports and outbound-client boundaries once those features exist.

Version-appropriate references: [Boot 4.0 tracing](https://docs.spring.io/spring-boot/4.0/reference/actuator/tracing.html), [management endpoints](https://docs.spring.io/spring-boot/4.0/reference/actuator/endpoints.html), [structured logging](https://docs.spring.io/spring-boot/4.0/reference/features/logging.html). Observability dependencies use Boot 4.0.6's managed versions; no Java agent is needed.

## Dependency security alignment

The current image scan flagged CVE-2026-47884 and CVE-2026-47890 in the baseline's Spring Framework 7.0.7 as fixed critical findings under the existing scanner policy. Spring's [XsltView advisory](https://spring.io/security/cve-2026-47884/) and [SSE fragment advisory](https://spring.io/security/cve-2026-47890/) identify 7.0.9 as the fixed open-source version; the vendor rates these conditional issues medium and low respectively. This REST application does not use those view-rendering features, but the unchanged image gate requires the patched dependency.

The Maven `spring-framework.version=7.0.9` property aligns every Framework artifact through Boot's Framework BOM, using its [supported version override](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/properties.html). Java 21, Boot 4.0.6 and the existing Tomcat 11.0.25 alignment are preserved. This is a narrowly scoped security correction for an existing dependency, not an observability dependency requirement. No vulnerability suppression or gate change is introduced. Revisit the override when a separately reviewed Boot upgrade manages the same or newer Framework patch.
# Feedback operation telemetry

Submission, reporting and moderation emit `feedback_submit`, `feedback_report` and `feedback_moderate` through the existing bounded workflow timer, counter and trace port. Operation and outcome are the only feedback metric dimensions; IDs, comments and report reasons never become labels or span errors. Mandatory audit records participate in the protected transaction. See [FEEDBACK.md](FEEDBACK.md).

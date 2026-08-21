# Runtime Policy Configuration

Runtime policy values are bound to validated Spring `@ConfigurationProperties` records at startup. Invalid values fail application startup instead of being accepted silently. These non-secret settings have explicit application defaults and can be overridden through environment variables; a local `.env` file is convenient for Docker Compose but is not required by the application configuration model.

## Persistence profiles

The default and `test` profiles use module-owned in-memory repositories. Activate `postgres` for durable storage; this enables the PostgreSQL driver, Flyway, Spring Data JPA adapters, real REQUIRED transactions, and cross-instance advisory locks. Open Session in View is disabled. Hibernate runs only `ddl-auto=validate`; it never creates or changes schema objects.

| Environment variable | Required with `postgres` | Meaning |
|---|---:|---|
| `SPRING_DATASOURCE_URL` | yes | JDBC URL, for example `jdbc:postgresql://localhost:5432/carwash`. |
| `SPRING_DATASOURCE_USERNAME` | yes | PostgreSQL login name. |
| `SPRING_DATASOURCE_PASSWORD` | yes | PostgreSQL password; never commit it. |
| `SPRING_DATASOURCE_MAX_POOL_SIZE` | no (`20`) | Maximum Hikari connections. |
| `SPRING_DATASOURCE_MIN_IDLE` | no (`2`) | Minimum idle Hikari connections. |
| `SPRING_DATASOURCE_CONNECTION_TIMEOUT_MS` | no (`30000`) | Pool acquisition timeout in milliseconds. |

Example direct startup:

```bash
export SPRING_PROFILES_ACTIVE=postgres
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/carwash
export SPRING_DATASOURCE_USERNAME=carwash
read -s -p 'Database password: ' SPRING_DATASOURCE_PASSWORD && echo
export SPRING_DATASOURCE_PASSWORD
./mvnw spring-boot:run
```

Compose activates `postgres` automatically and requires `POSTGRES_PASSWORD` in the ignored `.env`. Flyway applies `V{number}__description.sql` files in order and validates their checksums at every startup. Never edit an applied migration; add the next version. Neither profile creates production seed users or credentials. The four built-in authorization roles and permission catalogue are immutable reference data, not login accounts.

## Supported settings

| Property | Environment variable | Type | Default | Validation | Meaning |
|---|---|---|---|---|---|
| `carwash.policy.booking.max-active-bookings-per-slot` | `CARWASH_BOOKING_MAX_ACTIVE_PER_SLOT` | integer | `1` | `>= 1` | Maximum number of non-cancelled bookings allowed at the same scheduled date/time. |
| `carwash.policy.booking.cancellation-window` | `CARWASH_BOOKING_CANCELLATION_WINDOW` | `Duration` | `PT0S` | non-negative | Minimum lead time before the current scheduled date/time for cancellation or rescheduling. |
| `carwash.policy.booking.operating-start` | `CARWASH_BOOKING_OPERATING_START` | `LocalTime` | `08:00` | required; before operating end | Global single-location opening time. |
| `carwash.policy.booking.operating-end` | `CARWASH_BOOKING_OPERATING_END` | `LocalTime` | `17:00` | required; after operating start | Global single-location closing time; overnight windows are not supported. |
| `carwash.policy.booking.slot-interval` | `CARWASH_BOOKING_SLOT_INTERVAL` | `Duration` | `PT30M` | positive whole minutes; at least one minute | Interval between appointment starts, measured from the applicable operating-window start. |
| `carwash.policy.notification.recent-limit` | `CARWASH_NOTIFICATION_RECENT_LIMIT` | integer | `10` | `>= 1` | Default maximum returned by the recent-notification lookup. Explicit internal caller limits still take precedence. |
| `carwash.policy.queue.default-service-duration` | `CARWASH_QUEUE_DEFAULT_SERVICE_DURATION` | `Duration` | `PT10M` | greater than zero | Retained validated CONFIG-001 compatibility setting. OPS-001 queue entries always resolve their offering duration and never fall back to this value. |
| `carwash.recommendation.weights.distance` | `CARWASH_RECOMMENDATION_WEIGHT_DISTANCE` | decimal | `0.25` | finite; `0..1`; all four weights sum exactly to `1` | BEST_OVERALL distance weight. |
| `carwash.recommendation.weights.queue-wait` | `CARWASH_RECOMMENDATION_WEIGHT_QUEUE_WAIT` | decimal | `0.25` | finite; `0..1`; all four weights sum exactly to `1` | BEST_OVERALL queue-wait weight. |
| `carwash.recommendation.weights.total-time` | `CARWASH_RECOMMENDATION_WEIGHT_TOTAL_TIME` | decimal | `0.25` | finite; `0..1`; all four weights sum exactly to `1` | BEST_OVERALL queue-plus-service total-time weight. |
| `carwash.recommendation.weights.price` | `CARWASH_RECOMMENDATION_WEIGHT_PRICE` | decimal | `0.25` | finite; `0..1`; all four weights sum exactly to `1` | BEST_OVERALL branch-offering price weight. |
| `carwash.recommendation.max-radius-km` | `CARWASH_RECOMMENDATION_MAX_RADIUS_KM` | decimal kilometres | `50.00` | `(0, 20000]` | Default and server maximum radius for recommendation requests. |
| `carwash.runtime.time-zone` | `CARWASH_TIME_ZONE` | `ZoneId` | `UTC` | valid Java/IANA zone ID | Zone used by the application `Clock` for local date/time policy decisions plus queue and notification lifecycle timestamps. |

`UTC` is the explicit runtime default because the current product documentation does not establish one business operating geography. It avoids inheriting a developer machine or container timezone. An environment with a defined local business zone can override it, for example `Africa/Johannesburg`. Booking request DTOs validate required branch-local date/time syntax, while the shared availability decision resolves future-time validity against the selected branch timezone; a timezone-less Bean Validation `@Future` check is deliberately avoided. A booking start must map to exactly one valid branch offset: DST gaps and ambiguous fall-back local times are rejected. AVAIL-002 also excludes an offset-aware instant when its resolved branch-local start is ambiguous, because the current booking request cannot carry the selected overlap occurrence.

## Duration values

Spring accepts ISO-8601 duration syntax. Common examples are:

- `PT0S` — zero seconds
- `PT30M` — 30 minutes
- `PT2H` — two hours

Negative booking cancellation windows, zero/negative/sub-minute booking intervals, invalid operating-window ordering, and zero or negative retained queue default durations are rejected at startup.

## Single-location scheduling window

For legacy AVAIL-001, candidate starts begin at `operating-start` and advance by `slot-interval` while remaining before `operating-end`. Branch-aware writes and AVAIL-002 start the grid at the beginning of the applicable continuous Marketplace operating window. Exactly adjacent intervals form one continuous window and keep the earliest anchor; a real gap starts a new grid; an overnight window remains anchored to its actual opening on the previous local date. Any positive whole-minute duration is valid, including values such as `PT45M` that do not divide 24 hours. AVAIL-001 still reports the global exact-start model. Branch-aware capacity is scoped to overlapping active bookings for one branch/offering; cancelled and completed bookings do not consume it.

The legacy start/end settings configure AVAIL-001 only; they do not configure Marketplace branches. MKT-002 branch hours are managed through the Marketplace API and support overnight recurrence. AVAIL-002 booking writes and search enforce those hours, closures, and the same unambiguous-local-start rule, ensuring every advertised result is compatible with the current branch-local booking contract. External holiday calendars, staff/bay calendars, reservations, and predictive scheduling remain out of scope.

## Booking-change boundary

For a scheduled booking time and configured cancellation window:

```text
cutoff = scheduledDateTime - cancellationWindow
```

Cancellation and rescheduling are allowed only when `now < cutoff`. At `now == cutoff`, or any time after the cutoff, the change is rejected. For rescheduling, this cutoff applies to the existing appointment and is not a minimum lead time for the requested new slot. The default `PT0S` therefore preserves the existing cancellation behaviour for valid future bookings while providing the same boundary for schedule changes. CONFIG-001 introduced this setting; BOOKING-001 reuses it rather than adding a duplicate property.

## Overrides

Shell example:

```bash
export CARWASH_BOOKING_MAX_ACTIVE_PER_SLOT=2
export CARWASH_BOOKING_CANCELLATION_WINDOW=PT2H
export CARWASH_BOOKING_OPERATING_START=08:00
export CARWASH_BOOKING_OPERATING_END=17:00
export CARWASH_BOOKING_SLOT_INTERVAL=PT30M
export CARWASH_NOTIFICATION_RECENT_LIMIT=20
export CARWASH_QUEUE_DEFAULT_SERVICE_DURATION=PT15M
export CARWASH_TIME_ZONE=Africa/Johannesburg
./mvnw spring-boot:run
```

Docker Compose already passes the variables from the uncommitted `.env` file to the application container, so the same image can use different runtime policy values without Dockerfile changes.

Tests set all supported policy values and the timezone explicitly in `application-test.properties`. Unit tests that exercise exact cutoff or timestamp behaviour use a fixed `Clock` rather than sleeps or the host clock.

## Runtime policy versus domain invariant

Runtime policies are operational values that may legitimately vary between environments without changing the workflow model, such as slot capacity, cancellation lead time, global operating hours, slot interval, recent-notification count, queue fallback duration, and timezone.

Domain invariants remain code. CONFIG-001 does not externalize booking or queue status transitions, the requirement for positive queue positions, ownership rules, terminal-state restrictions, or the initial `WAITING` queue state. The `IN_APP` notification channel is also retained as an implementation constant because external delivery/channel selection is not configurable functionality in the current system.

## Recommendation scoring configuration

REC-001 activates `carwash.recommendation.*`. The four decimal weights must each be between zero and one and must sum exactly to `1`; binary floating-point tolerance is not used. Missing, out-of-range, non-decimal, or invalid-sum values fail startup. The request's optional `maxRadiusKm` cannot exceed the configured maximum; omission applies the configured maximum.

Example override:

```dotenv
CARWASH_RECOMMENDATION_WEIGHT_DISTANCE=0.40
CARWASH_RECOMMENDATION_WEIGHT_QUEUE_WAIT=0.20
CARWASH_RECOMMENDATION_WEIGHT_TOTAL_TIME=0.25
CARWASH_RECOMMENDATION_WEIGHT_PRICE=0.15
CARWASH_RECOMMENDATION_MAX_RADIUS_KM=75.00
```

The values are non-secret and are mapped in `.env.example`, `application.properties`, deterministic test configuration, and Docker Compose. They affect ranking only; they never change AVAIL-002 eligibility or reserve capacity.

Runtime policies are internal configuration only. No public configuration endpoint is exposed.

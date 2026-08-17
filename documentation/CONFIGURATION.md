# Runtime Policy Configuration

Runtime policy values are bound to validated Spring `@ConfigurationProperties` records at startup. Invalid values fail application startup instead of being accepted silently. These non-secret settings have explicit application defaults and can be overridden through environment variables; a local `.env` file is convenient for Docker Compose but is not required by the application configuration model.

## Supported settings

| Property | Environment variable | Type | Default | Validation | Meaning |
|---|---|---|---|---|---|
| `carwash.policy.booking.max-active-bookings-per-slot` | `CARWASH_BOOKING_MAX_ACTIVE_PER_SLOT` | integer | `1` | `>= 1` | Maximum number of non-cancelled bookings allowed at the same scheduled date/time. |
| `carwash.policy.booking.cancellation-window` | `CARWASH_BOOKING_CANCELLATION_WINDOW` | `Duration` | `PT0S` | non-negative | Minimum lead time before the current scheduled date/time for cancellation or rescheduling. |
| `carwash.policy.booking.operating-start` | `CARWASH_BOOKING_OPERATING_START` | `LocalTime` | `08:00` | required; before operating end | Global single-location opening time. |
| `carwash.policy.booking.operating-end` | `CARWASH_BOOKING_OPERATING_END` | `LocalTime` | `17:00` | required; after operating start | Global single-location closing time; overnight windows are not supported. |
| `carwash.policy.booking.slot-interval` | `CARWASH_BOOKING_SLOT_INTERVAL` | `Duration` | `PT30M` | positive whole minutes; at least one minute | Interval between server-generated appointment starts, measured from operating start. |
| `carwash.policy.notification.recent-limit` | `CARWASH_NOTIFICATION_RECENT_LIMIT` | integer | `10` | `>= 1` | Default maximum returned by the recent-notification lookup. Explicit internal caller limits still take precedence. |
| `carwash.policy.queue.default-service-duration` | `CARWASH_QUEUE_DEFAULT_SERVICE_DURATION` | `Duration` | `PT10M` | greater than zero | Retained validated CONFIG-001 compatibility setting. OPS-001 queue entries always resolve their offering duration and never fall back to this value. |
| `carwash.runtime.time-zone` | `CARWASH_TIME_ZONE` | `ZoneId` | `UTC` | valid Java/IANA zone ID | Zone used by the application `Clock` for local date/time policy decisions plus queue and notification lifecycle timestamps. |

`UTC` is the explicit runtime default because the current product documentation does not establish one business operating geography. It avoids inheriting a developer machine or container timezone. An environment with a defined local business zone can override it, for example `Africa/Johannesburg`. Bean Validation time constraints such as booking `@Future` validation use the same application `Clock`, so request validation and service policy decisions interpret local date/time values consistently.

## Duration values

Spring accepts ISO-8601 duration syntax. Common examples are:

- `PT0S` — zero seconds
- `PT30M` — 30 minutes
- `PT2H` — two hours

Negative booking cancellation windows, zero/negative/sub-minute booking intervals, invalid operating-window ordering, and zero or negative retained queue default durations are rejected at startup.

## Single-location scheduling window

For a requested date, candidate starts begin at `operating-start` and advance by `slot-interval` while remaining before `operating-end`. A candidate is advertised or accepted only when its service-specific estimated duration finishes at or before closing. Booking creation, focused rescheduling, generic offering changes, and availability all reuse this same time-grid policy. Operational booking capacity is partitioned by branch; legacy AVAIL-001 still reports the global exact-start model. Cancelled bookings do not consume capacity.

These settings remain the transitional same-day time window for branch-scoped booking writes and legacy AVAIL-001; they do not configure Marketplace branches. MKT-002 branch hours are managed through the Marketplace API and support overnight recurrence, but OPS-001 deliberately does not enforce them. Branch-aware availability, external holiday calendars, staff/bay calendars, and overlapping-resource scheduling remain out of scope.

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

## Reserved recommendation namespace

`carwash.recommendation.*` is reserved for REC-001 and later recommendation work, including future weights and distance limits. No recommendation property is currently registered and no value in that namespace affects runtime behaviour under CONFIG-001.

Runtime policies are internal configuration only. No public configuration endpoint is exposed.

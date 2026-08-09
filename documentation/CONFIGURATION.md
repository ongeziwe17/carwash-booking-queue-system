# Runtime Policy Configuration

Runtime policy values are bound to validated Spring `@ConfigurationProperties` records at startup. Invalid values fail application startup instead of being accepted silently. These non-secret settings have explicit application defaults and can be overridden through environment variables; a local `.env` file is convenient for Docker Compose but is not required by the application configuration model.

## Supported settings

| Property | Environment variable | Type | Default | Validation | Meaning |
|---|---|---|---|---|---|
| `carwash.policy.booking.max-active-bookings-per-slot` | `CARWASH_BOOKING_MAX_ACTIVE_PER_SLOT` | integer | `1` | `>= 1` | Maximum number of non-cancelled bookings allowed at the same scheduled date/time. |
| `carwash.policy.booking.cancellation-window` | `CARWASH_BOOKING_CANCELLATION_WINDOW` | `Duration` | `PT0S` | non-negative | Minimum lead time before the current scheduled date/time for cancellation or rescheduling. |
| `carwash.policy.notification.recent-limit` | `CARWASH_NOTIFICATION_RECENT_LIMIT` | integer | `10` | `>= 1` | Default maximum returned by the recent-notification lookup. Explicit internal caller limits still take precedence. |
| `carwash.policy.queue.default-service-duration` | `CARWASH_QUEUE_DEFAULT_SERVICE_DURATION` | `Duration` | `PT10M` | greater than zero | Defensive ETA duration used when a queue calculation has no positive service duration. Queue estimates are exposed in whole minutes, so a positive sub-minute fallback rounds up to one minute. |
| `carwash.runtime.time-zone` | `CARWASH_TIME_ZONE` | `ZoneId` | `UTC` | valid Java/IANA zone ID | Zone used by the application `Clock` for local date/time policy decisions plus queue and notification lifecycle timestamps. |

`UTC` is the explicit runtime default because the current product documentation does not establish one business operating geography. It avoids inheriting a developer machine or container timezone. An environment with a defined local business zone can override it, for example `Africa/Johannesburg`. Bean Validation time constraints such as booking `@Future` validation use the same application `Clock`, so request validation and service policy decisions interpret local date/time values consistently.

## Duration values

Spring accepts ISO-8601 duration syntax. Common examples are:

- `PT0S` — zero seconds
- `PT30M` — 30 minutes
- `PT2H` — two hours

Negative booking cancellation windows and zero or negative queue fallback durations are rejected at startup.

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
export CARWASH_NOTIFICATION_RECENT_LIMIT=20
export CARWASH_QUEUE_DEFAULT_SERVICE_DURATION=PT15M
export CARWASH_TIME_ZONE=Africa/Johannesburg
./mvnw spring-boot:run
```

Docker Compose already passes the variables from the uncommitted `.env` file to the application container, so the same image can use different runtime policy values without Dockerfile changes.

Tests set all supported policy values and the timezone explicitly in `application-test.properties`. Unit tests that exercise exact cutoff or timestamp behaviour use a fixed `Clock` rather than sleeps or the host clock.

## Runtime policy versus domain invariant

Runtime policies are operational values that may legitimately vary between environments without changing the workflow model, such as slot capacity, cancellation lead time, recent-notification count, queue fallback duration, and timezone.

Domain invariants remain code. CONFIG-001 does not externalize booking or queue status transitions, the requirement for positive queue positions, ownership rules, terminal-state restrictions, or the initial `WAITING` queue state. The `IN_APP` notification channel is also retained as an implementation constant because external delivery/channel selection is not configurable functionality in the current system.

## Reserved recommendation namespace

`carwash.recommendation.*` is reserved for REC-001 and later recommendation work, including future weights and distance limits. No recommendation property is currently registered and no value in that namespace affects runtime behaviour under CONFIG-001.

Runtime policies are internal configuration only. No public configuration endpoint is exposed.

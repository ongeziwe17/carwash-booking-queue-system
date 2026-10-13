# Operational reporting (REPORT-001)

This API is a private, authenticated backend dashboard contract. Owners can request their own business or one of its branches. Platform administrators must supply exactly one business or branch ID. Customers and staff have no reporting access. Missing and foreign scopes both return safe 404 responses. IDs are normalized and the resolved canonical ID is used for all reads. Inactive businesses, branches and offerings remain in authorized historical reports. Authorization, aggregation, ranking and pagination run in one `readSnapshot` (PostgreSQL read-only repeatable-read; in-memory read lock). No customer identity, contact, vehicle, special request, feedback or notification text is returned.

## API

All five new `GET /api/reports/*` operations require `startDate` and `endDate` as ISO calendar dates, inclusive, and exactly one `branchId` or `businessId`. The range is 1–90 days. The server turns it into `[start.atStartOfDay, (end+1).atStartOfDay)` for scheduled cohorts and branch-local instants for events. Invalid, reversed, excessive, duplicate scalar, unknown or conflicting parameters return the standard 400 error. The previous `/api/reports/daily-summary` path, response and semantics remain unchanged.

| Path | Response | Default order | Filters / page |
| --- | --- | --- | --- |
| `/period-summary` | Scope, timezone semantics, dates and combined metrics | N/A | No page fields |
| `/daily-breakdown` | Daily metrics including zero dates | Date ascending | `date`, `bookings`, `id`; page 0, size 20 |
| `/branches` | Branch ID, timezone and metrics | Booking volume descending, branch ID ascending | `bookings`, `id`; page 0, size 20 |
| `/offerings` | Offering and service IDs, noncancelled booking volume | Volume descending, offering ID ascending | Exact `serviceId`; `bookings`, `serviceId`, `id`; page 0, size 20 |
| `/peak-hours` | Hours 0–23 with booking volume and queue completions | Booking volume descending, hour ascending | `bookings`, `hour`, `id`; page 0, size 20 |

For the four page responses, `results` is the existing `PageResponse` envelope with a maximum size of 100 and a deterministic ID tie-breaker. A filtered or out-of-range page keeps the authorized matching total. A business day combines each branch's local calendar day, without assigning a fictitious business timezone; `timezoneSemantics` is `BRANCH_LOCAL_DAYS`. Peak hours similarly combine local clock-hour labels across branches. Branch responses return the IANA timezone.

## Measurements and coverage

| Field | Source and population | Time basis / unit | Limitation |
| --- | --- | --- | --- |
| `bookingVolume`, `bookingStatus` | Existing booking rows and **current** status; five status buckets sum to volume | Booking's branch-local scheduled start date/hour, count | This is a scheduled cohort, not transition history. Deletion removes records. |
| `cancellations`, `cancellationRate` | Current `CANCELLED` bookings in the scheduled cohort; cancelled / all cohort bookings | Count; fraction rounded to 4 decimals (`HALF_UP`), null for zero bookings | A cancellation timestamp is not stored; no cancellation event-date claim is made. |
| `queueJoined`, `serviceStarts`, `queueCompletions` | One queue entry for each corresponding nonnull joined, started or completed timestamp | Event instant converted from configured runtime clock zone to branch local date/hour, counts | Completion counts queue events once and does not add booking `COMPLETED` status. It is not a historical state-transition ledger if rows are deleted. |
| `actualWait` | Started entries with valid joined and started instants, elapsed joined-to-started | Sample count, average and maximum seconds | Called timestamp and estimated wait are intentionally excluded. Missing or negative elapsed values do not form samples. |
| `actualServiceDuration` | Completed entries with valid started and completed instants, elapsed started-to-completed | Sample count, average and maximum seconds | Configured offering duration is not used for actual service time. |
| `/offerings` popularity | Noncancelled scheduled booking cohorts, grouped by canonical offering | Count for scheduled dates | An inactive historical offering remains visible to authorized owners/admins. |
| `/peak-hours` | Scheduled booking start hour and queue completion event hour | Branch-local clock hour 0–23; count | Hours on DST overlap days combine both occurrences of that hour. |
| `scheduledCapacity` | Prospective noncancelled booking intervals and current active offerings; current weekly hours minus active closures | Booked minutes, available concurrency-minutes and percent rounded to 2 decimals | Measures *scheduled occupancy*, never physical bay, staff or actual service utilization. See below. |

Averages are `sum(whole elapsed seconds) / sampleCount` rounded to 2 decimals (`HALF_UP`). Maximum is whole seconds. No samples means `sampleCount=0`, `averageSeconds=null`, `maximumSeconds=null`. An empty cohort yields zero counts and null cancellation rate. Reporting alone does not mutate operational records. Private report reads follow the existing daily-report convention (no new audit record); a fixed `report_read` latency/outcome operation has no resource-ID labels.

Queue runtime `LocalDateTime` values are written using `carwash.runtime.time-zone` (UTC by default), while booking schedule `LocalDateTime` is already branch local. PostgreSQL stores both as `timestamp without time zone`; it converts the former through the configured clock zone before applying the branch zone. Ambiguous runtime local times use the later/standard offset in both adapters. Impossible gap timestamps are excluded, as are missing event timestamps; this is reported through the event and sample counts, not estimated or backfilled. Branch-local DST boundaries use actual instants for event attribution and elapsed time. Overnight weekly windows include the portion after midnight on the next local day. Event rows are attributed to the date/hour of **their own** event (start for wait, completion for duration), even if joining or starting occurred before the requested range. Bookings spanning midnight contribute scheduled occupancy minutes to each local calendar day they overlap.

### Scheduled capacity

Only **strictly future** branch-local dates can use the current configuration. Past and current dates return `available=false`, null numeric fields and `HISTORICAL_CONFIGURATION_UNAVAILABLE`, because branch timezone, schedule, closure and offering-capacity versions were not captured for those dates. This also applies to a range containing any such day (`PARTIAL_CAPACITY_COVERAGE`). Inactive branches, missing opening windows or no active offerings return unavailable. A day with zero open concurrency-minutes returns `ZERO_AVAILABLE_CAPACITY` rather than a made-up zero percent. No historical backfill is performed.

For a future day, available minutes = `sum(weekly-open-window seconds clipped to that local day, less overlapping active closures) / 60 × sum(current active offering concurrent capacities)`. Day boundaries and windows are resolved in the branch IANA timezone, including daylight-saving gaps/overlaps. Booked minutes = `sum(duration of each noncancelled booking's scheduled interval with an active offering, clipped to the local calendar day) / 60`. The offering's current configured duration is a planning input. A booking retained after a closure can make scheduled occupancy exceed 100%; the number exposes that planning conflict rather than hiding it. Concurrent capacities describe configured offering slots, not historical staffing or physical bays. Forecasts can change as bookings or configuration change and are not an immutable ledger.

## Persistence and performance

The reporting capability composes published booking, queue, Marketplace, schedule and catalog application queries. Booking and queue modules own their database aggregates: scheduled cohorts are grouped by date/hour/offering/status in PostgreSQL; queue lifecycle events are filtered by canonical branch and bounded event windows, then grouped by branch-local date/hour/event. No query loads every historical booking or queue row into the reporting service. In memory, the same projections are grouped from module-owned repositories under the coordinator read lock.

V8 adds `(branch_id, joined_at)`, partial `(branch_id, started_at)` and partial `(branch_id, completed_at)` indexes so each event stream uses its branch and time predicate before timezone grouping. The existing `(branch_id, scheduled_at, booking_id)` booking index supports cohort and booked-minute scans. The booking capacity query joins a parameterized, snapshot-supplied set of active offering durations, without querying catalog tables directly. The PostgreSQL regression checks both adapter aggregates, all three indexes and representative `EXPLAIN` plans with sequential scans disabled for the tiny fixture; the actual planner may choose a table scan for very small tables. V1–V7 are unchanged. A business with many branches executes one bounded aggregation per branch; page size caps output, not this internal aggregation cost. A future export should use an authorized, asynchronous, capped snapshot job with retention and download permissions; no raw export or warehouse is introduced here.

Revenue, payments and refunds are deferred until #128 supplies authoritative payment and refund events. No financial figure is inferred from offering prices or bookings.

## Example commands

`./mvnw verify` compiles and runs the unit/integration, architecture, OpenAPI and unchanged coverage gates; it changes local build output under `target/` and may start an ephemeral PostgreSQL Testcontainers instance when Docker is available.

`python3 tools/validate-observability.py` checks dashboard, alert and configuration definitions without changing application state.

`git diff --check` checks the working tree for whitespace errors without changing files.

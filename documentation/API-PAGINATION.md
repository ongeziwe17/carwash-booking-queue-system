# List API pagination, filtering and sorting

Implementation base: `08d3a80086268f10e238eb6d24c133762e6fbec8` (staging, fetched 12 October 2026).

## Endpoint inventory (recorded before contract changes)

All rows below are GET requests. Existing paths and permissions are preserved.

| Endpoint | Authorization/scope | Previous bound | New approach/default ordering | Filters and sort allowlist | Consumer changes |
|---|---|---|---|---|---|
| `/api/users` | Platform administrator only | Unbounded | Offset; ID ASC | accountStatus; ID, name, createdAt | Array → page; API/Bruno user assertions |
| `/api/vehicles` | Operational business; admin must specify businessId; customers denied | Unbounded | Offset; ID ASC | userId, vehicleType; ID, brand, model | Array → page; tenant/vehicle assertions |
| `/api/services` | Existing service-read permission, global catalog | Unbounded | Offset; ID ASC | active, name; ID, name, price, duration | Array → page; catalog assertions |
| `/api/marketplace/businesses` | Admin global; owners/staff own business only | Admin unbounded, tenant structurally ≤1 | Offset; ID ASC | status; ID, name, registeredAt | Array → page; marketplace assertions |
| `/api/marketplace/businesses/{businessId}/branches` | Existing accessible business required; missing and foreign businesses 404 | Unbounded | Offset; ID ASC | status, city; ID, name, createdAt | Array → page; branch assertions |
| `/api/marketplace/branches/discoverable` | Existing marketplace-read permission; active business and branch, public discovery enabled | Unbounded | Offset; ID ASC | city; ID, name, createdAt | Array → page; discovery assertions |
| `/api/marketplace/branches/{branchId}/offerings` | Marketplace-manage; accessible branch required | Unbounded | Offset; ID ASC | status, serviceId; ID, price, duration | Array → page; offering assertions |
| `/api/marketplace/branches/{branchId}/offerings/discoverable` | Marketplace-read; active offering/service/business/branch and public discovery | Unbounded | Offset; ID ASC | serviceId; ID, price, duration | Array → page; discovery offering assertions |
| `/api/marketplace/branches/{branchId}/closures` | Marketplace-manage; accessible branch required | Unbounded history | Offset; startsAt then ID | status, from, to; ID, startsAt, endsAt | Array → page; schedule assertions |
| `/api/bookings` | Operational business; admin exact businessId; customers denied | Unbounded | Offset; ID ASC | branchId, status, userId, serviceId, offeringId, from, to; ID, scheduledAt, createdAt | Array → scalar page; booking/tenant assertions |
| `/api/queue-entries` | Queue-operate; operational business; admin exact businessId; customers denied | Unbounded | Offset; branch, active first, position, joinedAt (all nanos), ID | branchId, status, serviceId, offeringId; ID, position, joinedAt | Array → scalar page; queue/tenant assertions |
| `/api/marketplace/branches/discoverable/nearby` | Marketplace-read; existing eligibility/radius/openAt rules | Unbounded result, computation depends on eligible branches | Offset after raw-distance ranking; existing distance sort | Existing latitude/longitude/radiusKm/serviceId/openAt; sort=distance retained | Array → page; nearby assertions |
| `/api/availability/branches` | Service-read; existing schedule/capacity/radius eligibility | Unbounded derived candidates | Offset after eligibility and existing ordering | Existing serviceId/at/latitude/longitude/radiusKm; fixed ordering | Array → page; availability assertions |
| `/api/recommendations/branches` | Marketplace-read; existing preference/radius/eligibility | Unbounded ranked output | Offset after full normalization and ranking; global ranks preserved | Existing serviceId/at/preference/location/radius; fixed ranking | Array → page; recommendation assertions |
| `/api/audit-records` | Existing platform/business cursor scope | Bounded cursor | Unchanged cursor (no offset totals) | Existing filters unchanged | No contract change |
| `/api/notifications/user/{userId}/inbox` | Existing authorized user/business scope | Bounded cursor + consistent inbox snapshot | Unchanged exact cursor/nanosecond/snapshot contract | Existing cursor/limit/unreadOnly | No contract change |
| `/api/notifications/user/{userId}` | Existing authorized recent notifications | Explicitly bounded recent limit | Unchanged purpose and bounded output | Existing business scope | No contract change |
| `/api/availability` | Service-read | Finite policy-defined daily slots | No pagination: daily aggregate | Existing service/date | No contract change |
| Operating hours, daily report, open-status and individual resources | Existing permissions/scopes | Fixed weekly schedule, aggregates or single resources | Unchanged | Unchanged | No contract change |

Payments and feedback do not exist and remain out of scope. Their future collection APIs must adopt the same bounded, authorized contract.

## Offset contract and compatibility

All fourteen offset endpoints above now return an object instead of a bare array. Requests without parameters use `page=0&size=20`; sizes 1–100 are accepted, with no option to retrieve every record. Client code must read `response.content` and follow `hasNext`, rather than treating the response itself as an array.

For example, `GET /api/services?active=true&sort=price,asc&sort=name,desc&page=1&size=2` returns:

```json
{
  "content": [],
  "page": 1,
  "size": 2,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": true
}
```

This is an out-of-range page: matching totals remain present and the requested page is retained. An empty dataset has `content=[]`, `totalElements=0`, `totalPages=0` and `hasNext=false`; `hasPrevious` means the requested page is greater than zero, even when that page is empty. Metadata counts only authorized records matching all filters. Pages are snapshots within one request, not a promise of stable membership across subsequent requests while data changes.

Booking list items now contain `bookingId`, `userId`, `vehicleId`, `branchId`, `serviceOfferingId`, `serviceId`, `scheduledDateTime`, `status` and `createdAt`. Queue list items contain existing lifecycle/position/wait fields plus scalar booking, user, branch, offering and service IDs. Nested user/vehicle/service aggregates and booking special requests are omitted from these lists. Individual resource responses and secured audit records keep their existing contracts. Other list item fields are preserved through explicit DTOs.

## Filters, sorting and validation

Ordinary offset lists also accept an exact `id` filter. All filters combine with AND, and ID strings are exact, case-sensitive matches with a maximum of 64 characters. `name` on services and `city` on branches are exact case-sensitive text (not substring or full-text search). Null fields do not match filters; sorting puts nulls last in either direction. Text sorting uses Unicode code point order (PostgreSQL `C` collation) in both adapters, not locale-dependent alphabetical order.

Public sort field names are lowercase `id`, `name`, `price`, `duration`, `position` and the camel-case names in the inventory; “ID” in the inventory means `id`. Up to three repeated `sort=field,asc|desc` terms are applied in request order. Fields are case-sensitive; directions are case-insensitive. A stable `id,asc` tie-breaker is appended if ID is absent. The default queue order additionally groups branches and places active entries before terminal entries, then compares position, exact joined timestamp and ID. A custom list sort affects display only; queue positions and call-next semantics are unchanged.

State filters are exact enum names: user `PENDING|ACTIVE|SUSPENDED|DEACTIVATED`, business/branch/offering `ACTIVE|INACTIVE`, closure `ACTIVE|CANCELLED`, booking `CREATED|CONFIRMED|CANCELLED|IN_SERVICE|COMPLETED`, queue `WAITING|CALLED|IN_PROGRESS|COMPLETED|EXITED`. Vehicle type is existing exact text, not a new enumeration. Service `active` accepts only `true` or `false`. Nearby retains its existing `sort=distance` syntax; availability and recommendation order is fixed and rejects a supplied sort.

Booking `from`/`to` compare scheduled date/time inclusively, using branch-local ISO local timestamps without offsets, e.g. `2089-01-17T10:00:00.123456789`. Closure `from`/`to` compare closure start instants inclusively, using an RFC3339 timestamp with an offset, e.g. `2089-01-17T08:00:00.123456789Z`. Both accept calendar years 0001–9999, so values outside the portable database range fail with 400 in either profile. These are start-time ranges, not overlap queries. Both endpoints preserve nanoseconds: PostgreSQL compares the stored time/seconds and nano remainder together. `from` must not exceed `to`.

Negative/malformed/overflowing pages, sizes outside 1–100, offsets greater than `Integer.MAX_VALUE`, unknown query keys, repeated scalar parameters, invalid sort fields/directions, duplicate sort fields, more than three terms, blank/oversized filters, invalid enums/timestamps and reversed ranges return HTTP 400 using the existing `ApiErrorResponse`. Sizes are rejected rather than clamped. Existing typed parameter conversion may report `INVALID_PARAMETER`; application validation uses the existing business-rule error code. Error text does not echo unsafe filter values.

## Authorization and consistency

Tenant/business scope is derived on the server before filters, counts, sorting and limits. Platform administrators still need an exact `businessId` for bookings, queues and vehicles. Owner/staff requests cannot override their authenticated business. Accessible-branch/business checks preserve safe 404 responses for foreign resources. Customers gain no operational list access. Optional user/service/offering filters can only narrow the mandatory scope; a foreign filter yields no foreign rows or totals.

Persistent ordinary list queries use the same parameterized predicates for count and content, within one read-only PostgreSQL REPEATABLE READ transaction covering authorization checks and DTO projection. They reject joining a weaker transaction. Content has an SQL offset and limit; EXISTS scope predicates avoid duplicate rows. User list hydration excludes credentials. Booking scalar projections do not hydrate associated personal profiles; queue pages bulk-load only their page's minimal booking associations. Branch/offering projections bulk-load only page-related parent/catalog data. Internal complete-set queries remain available for capacity, reports and queue lifecycle calculations.

In-memory requests hold the existing transaction coordinator's read lock for authorization, selection, totals and projection. Every application page captures immutable scalar snapshots before releasing the lock, so concurrent mutations cannot alter later DTO serialization. They sort/filter their authorized in-memory set; this is equivalent snapshot behavior, not an SQL-like bounded computation claim. Tests interleave foreign records, compare adapters, preserve nanoseconds and pause PostgreSQL between count and content with barriers while another transaction commits.

Existing migrations V1–V6 are unchanged. No new index is asserted necessary without production cardinality evidence. Existing scope/association indexes are retained. PostgreSQL CI captures representative EXPLAIN ANALYZE structural plans for a branch booking page and the tenant vehicle EXISTS query in `target/pagination-query-plans.txt`; small fixtures can correctly select sequential scans. The hosted run [346](https://github.com/ongeziwe17/carwash-booking-queue-system/actions/runs/38236613818) produced `Limit → Incremental Sort → Index Scan` for the branch booking page and a limited sort/nested-loop plan with index-only/index scans for scoped vehicles. These are small deterministic fixture plans, not production load measurements. Sorting by non-indexed names/prices and exact counts can cost O(matching rows), and deep offsets traverse skipped rows. Review real plans and traffic before adding an additive covering index. Bounds on HTTP output do not bound count/sort work.

## Derived lists and cursor exceptions

Nearby discovery, branch availability and recommendation APIs compute the existing eligible candidate set before page selection. Distance comparisons retain raw precision and tie-breakers. Recommendation normalization runs over all eligible candidates and retains global ranks on later pages. Point-in-time opening/capacity and all complete-set workflow calculations remain unchanged. Candidate discovery/calculation still scales with eligible branches and their offerings/schedules/queues; there is no new database shortlist or computational cap. Large deployments should profile these calculations before replacing them with a bounded, equivalently ranked strategy.

Audit and notification inbox keep their existing bounded cursor schemas, authorization, exact timestamp precision and inbox snapshot rules. Cursor metadata has continuation/cursor semantics, not offset totals or fabricated total pages. The legacy recent-notification endpoint remains bounded and is not a historical paging endpoint. Daily summaries, fixed weekly schedules, daily availability slot aggregates and single-resource responses remain unchanged. Future payment and feedback list APIs should adopt this bounded contract, with ownership predicates in content and count queries; neither feature is implemented here.

Branch pages resolve the business inside the same read snapshot as the authorized count, content and projection. The resolved business ID supplies the page scope, so a whitespace-padded valid path ID gives the same result as its canonical ID. An existing business with no branches returns a 200 page with zero totals; a missing business returns 404 for administrators, while tenant callers receive the same safe 404 for missing and foreign businesses.

## Verification commands

With Java 21 and Docker available, `./mvnw --batch-mode clean verify` compiles and runs unit, architecture, OpenAPI, in-memory and PostgreSQL tests plus the existing coverage gate. It changes build outputs and starts disposable Testcontainers databases; it does not deploy. `./mvnw --batch-mode -Dtest.order.seed=11001 clean verify` and the same command with seed `11002` repeat the full gate with deterministic test ordering, replacing build outputs and disposable test data.

`./mvnw --batch-mode -Djacoco.skip=true -Dit.test=PaginationIntegrationTest verify` checks the API test independently and changes build/test outputs; it is an isolation check, not a substitute for full coverage. The business Bruno collection includes page defaults, boundary/filter/sort/invalid-parameter examples; follow its README to start disposable acceptance fixtures and run it. Management Bruno remains separate. Existing CI retains dependency alignment, vulnerability scans, workflow/container, migration/durability, telemetry privacy/security, observability syntax/runtime and complete acceptance gates.

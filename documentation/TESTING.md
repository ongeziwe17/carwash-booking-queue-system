# Test Isolation and Quality Gates

TEST-001 separates fast unit/repository/service tests from Spring API integration tests and makes isolation, test data, execution order, and release-gate behaviour explicit.

## Current OPS-001 inventory

The OPS-001 suite executes **310 Surefire unit/repository/service tests** and **148 Failsafe Spring integration tests**. The generated OpenAPI contract remains exactly **66 operations** because OPS-001 evolves existing routes, and the complete Bruno collection executes **483 requests / 1,616 tests**. CI repeats the complete Maven verification under deterministic seeds `11001` and `11002`; exact coverage and container evidence is recorded in the draft pull request for the change.

## Baseline inventory before TEST-001

The prerequisite CI/CD run #163 executed **187 tests across 20 test classes**. The inventory below records the relevant isolation characteristics from the `staging` source used for TEST-001.

| Test class | Type / responsibility | Spring context | Repository / fixture state | IDs / timing / ordering | Authentication | Reset before each test | Approx. size |
|---|---|---|---|---|---|---|---:|
| `CarwashBookingQueueSystemApplicationTests` | Spring context smoke | `@SpringBootTest` | Shared application context; no business fixture | No material fixed IDs or timing assumptions | None | No | <30 lines |
| `ApiIntegrationTest` | Broad API workflows across users, vehicles, services, bookings, queues, notifications and reports | Yes; MockMvc built from Spring context | Shared Spring repositories; repeated API setup and raw JSON | Reused short/global fixture IDs and `LocalDateTime.now()` offsets; no explicit `@Order`, but shared data made order relevant | Primarily Security MockMvc JWT helpers | No | ~900 lines / 36 tests |
| `RbacAuthorizationIntegrationTest` | Authentication, RBAC, ownership and role-change contracts | Yes | Shared Spring repositories/services; role-specific fixtures | UUID/fixed fixture values plus mutable slot sequencing and wall-clock offsets | Real registration/login plus signed-token tests | `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` | ~600 lines / 9 tests |
| `ApiErrorContractIntegrationTest` | Validation, malformed requests, protocol failures, auth failures, internal errors and OpenAPI error checks | Yes | Shared context; synthetic failure controller; repeated assertions | Fixed missing-resource identifiers; no explicit ordering or sleeps | Anonymous and JWT helpers | No | ~350 lines / 7 tests |
| `AuthenticationIntegrationTest` | Login, bearer-token validation and public endpoints | Yes | Shared user repository; each scenario created users but context data remained afterward | Fixed/test-local identifiers; no sleeps | Real registration/login and bearer token use | No | ~180 lines / 4 tests |
| `DataIntegrityIntegrationTest` | Duplicate IDs, dependency deletion and lifecycle integrity through API | Yes | Shared Spring application repositories | Repeated fixed resource IDs; no sleeps | JWT helper | No | ~250 lines / 3 tests |
| `RequestValidationIntegrationTest` | Request DTO validation across API surfaces | Yes | Shared application repositories with prerequisite API data | Repeated raw maps/JSON and fixed identifiers | Anonymous registration/login requests plus JWT helper for protected APIs | No | ~250 lines / 1 large test |
| `Sec003RemediationIntegrationTest` | Ownership-transfer and safe role-name regressions | Yes | Shared service/repository beans | Generated/fixed IDs and wall-clock future time | JWT helper | No | ~180 lines / 2 tests |
| `UnsupportedMediaTypeHeaderIntegrationTest` | HTTP 415 contract and `Accept` guidance | Yes | No meaningful business fixture | Fixed endpoint only; no time/order assumption | JWT helper | No | ~60 lines / 1 test |
| `UserApiContractIntegrationTest` | Safe user DTO, validation, duplicate-email and update contracts | Yes | Shared Spring application repositories | Generated/fixed IDs and repeated raw maps | JWT helper for protected reads/updates | No | ~250 lines / 6 tests |
| `UserCredentialSecurityIntegrationTest` | BCrypt storage, UTF-8 password limits and credential serialization | Yes | Shared user repository/service | Test-local users; no execution-order requirement | Real credential service; no mocked security bypass | No | ~150 lines / 3 tests |
| `ServiceLayerTest` | Seven service-layer responsibilities in one class | No | Fresh repositories, coordinator and services in `@BeforeEach` | Heavy use of short IDs such as `u1`, `v1`, `s1`, `b1`, `q1`; `LocalDateTime.now()` offsets | None | Fresh object graph per method | ~900 lines / 68 tests |
| `AggregateIntegrityServiceTest` | Cross-repository aggregate consistency and concurrency | No | Fresh in-memory repositories/coordinator in `@BeforeEach` | Descriptive test-local IDs; latches/futures with bounded timeouts; no sleeps | None | Fresh object graph per method | ~200 lines / 5 tests |
| `UserManagementSecurityTest` | Last-platform-admin concurrency and lifecycle protection | No | Fresh in-memory user repository/service per method | Descriptive local IDs; latches/futures with bounded timeouts | None | Fresh object graph per method | ~170 lines / 4 tests |
| `InMemoryRepositoryIntegrityTest` | Duplicate insertion, missing update, snapshots and concurrency | No | Method-local repository instances | Local fixed IDs; one wall-clock booking helper; bounded latch/future timeouts | None | New repositories in each test | ~150 lines / 6 tests |
| `InMemoryRepositoryCrudTest` | Explicit in-memory CRUD/query semantics | No | Method-local repository instances | Local fixed IDs; one wall-clock booking helper | None | New repositories in each test | ~120 lines / 4 tests |
| `DatabaseUserRepositoryTest` | Unsupported database-repository placeholder contract | No | One immutable/stateless repository instance | Local `U-001` placeholder IDs; no timing | None | Not required; implementation has no mutable data | ~60 lines / 7 tests |
| `JwtAuthorityConverterTest` | Role catalogue to JWT authority conversion | No | No repositories | Fixed token subject; used `Instant.now()` for token timestamps | Direct JWT conversion | Not applicable | ~100 lines / 7 executions |
| `RoleCatalogTest` | Built-in role/permission catalogue | No | No repositories | No time/order assumptions | None | Not applicable | ~30 lines / 3 tests |
| `UserAuthenticationServiceTest` | Authentication timing-equivalence, mutation revalidation and lock scope | No; Mockito extension | Mock repository/credential/token services; fresh coordinator/service per method | Descriptive IDs; latches/futures with 1–2 second bounded waits; no sleeps | Direct service authentication with mocks | Fresh mocks/object graph per method | ~220 lines / 10 tests |

### Baseline shared-state findings

The principal isolation risk was not the method-local fixed IDs in repository/unit tests; those repositories were already recreated per test. The risks were concentrated in Spring integration tests, where one cached application context held mutable in-memory repositories across methods and classes. The large API suite and several focused classes therefore had to avoid collisions manually, while RBAC compensated by rebuilding the entire context before each method.

The baseline also mixed unit and Spring integration tests under Surefire, used wall-clock future offsets in several fixtures, duplicated MockMvc/authentication/request setup, and kept notification ID sequencing in a static production `AtomicLong`. Those patterns made order-related failures harder to reproduce and made the suite less suitable as a release gate.

## TEST-001 isolation model

Every Spring/API integration test now extends `ApiIntegrationTestSupport`, which provides:

- `@SpringBootTest`
- `@AutoConfigureMockMvc`
- `@ActiveProfiles("test")`
- shared `MockMvc` and `ObjectMapper`
- `InMemoryTestDataCleaner`
- a fresh `TestIdFactory` for every test method
- focused API/authentication clients and fixture builders
- reusable standard-error and privacy assertions

Before every integration-test method, `InMemoryTestDataCleaner` performs one `InMemoryDataCoordinator.write(...)` operation and deletes current records through repository APIs in dependency order so no dependent outlives its owner.

1. notifications
2. queue entries
3. bookings
4. branch service offerings
5. branch temporary closures and operating schedules
6. Marketplace branches and businesses
7. vehicles
8. reusable global services
9. users

The cleaner also resets only the test implementation of `NotificationIdGenerator`. Production exposes no reset API, no reflection-based cleanup, and no public test endpoint. The cached Spring context is reused; `@DirtiesContext` is no longer an isolation strategy.

## Post-refactor test ownership

The original post-TEST-001 verification measured **216 tests across 34 classes**: **134 Surefire tests** and **82 Failsafe integration tests**. The class inventory below records that refactor baseline; the current aggregate is listed above.

### Surefire: unit, repository and service tests

| Class | Tests | Responsibility / isolation |
|---|---:|---|
| `InMemoryRepositoryIntegrityTest` | 6 | Method-local repositories; deterministic dates; bounded concurrency |
| `DatabaseUserRepositoryTest` | 7 | Stateless unsupported database-repository contract |
| `InMemoryRepositoryCrudTest` | 4 | Method-local repositories; deterministic dates |
| `JwtAuthorityConverterTest` | 7 | Fixed test instants; no repository state |
| `RoleCatalogTest` | 3 | Pure permission catalogue |
| `UserAuthenticationServiceTest` | 10 | Fresh mocks/coordinator; bounded latch/future concurrency |
| `AggregateIntegrityServiceTest` | 5 | Fresh repositories/coordinator; aggregate/concurrency rules |
| `UserManagementServiceTest` | 10 | Fresh service graph per method |
| `VehicleManagementServiceTest` | 7 | Fresh service graph per method |
| `ServiceCatalogServiceTest` | 8 | Fresh service graph per method |
| `BookingManagementServiceTest` | 24 | Fresh service graph and deterministic booking dates |
| `QueueManagementServiceTest` | 14 | Fresh service graph and queue lifecycle |
| `NotificationManagementServiceTest` | 9 | Fresh service graph; deterministic notification IDs |
| `DailySummaryReportServiceTest` | 7 | Fresh service graph; fixed report dates |
| `UserManagementSecurityTest` | 4 | Fresh repository/service; bounded administrator concurrency |
| `RuntimePolicyConfigurationTest` | 9 | Configuration binding, validation and runtime-policy boundary coverage |

### Failsafe: Spring integration tests

All classes below inherit the same per-method repository reset unless a class only adds a narrowly scoped test configuration/controller import.

| Class | Tests | Responsibility |
|---|---:|---|
| `ApplicationContextIntegrationTest` | 1 | Spring context smoke |
| `UserWorkflowIntegrationTest` | 1 | User API workflow |
| `ServiceWorkflowIntegrationTest` | 4 | Service catalogue workflow |
| `BookingWorkflowIntegrationTest` | 14 | Booking creation, ownership, lifecycle and capacity |
| `QueueWorkflowIntegrationTest` | 8 | Queue lifecycle and relationship rules |
| `NotificationWorkflowIntegrationTest` | 3 | Notification lookup and deterministic IDs |
| `ReportWorkflowIntegrationTest` | 4 | Daily summary report contract |
| `OpenApiQualityGateIntegrationTest` | 8 | DTO/error schemas, privacy, auth metadata, controller coverage, operation metadata, path parameters and local `$ref` integrity |
| `AuthenticationIntegrationTest` | 4 | Real registration/login, JWT and public endpoint behaviour |
| `RbacAuthorizationIntegrationTest` | 9 | Real role fixtures/login plus signed-token RBAC/ownership regressions |
| `ApiErrorContractIntegrationTest` | 6 | Standard validation/protocol/security/internal error contract |
| `RequestValidationIntegrationTest` | 1 | Bean-validation coverage across request DTOs |
| `DataIntegrityIntegrationTest` | 3 | Duplicate/dependency/lifecycle integrity through API |
| `UnsupportedMediaTypeHeaderIntegrationTest` | 1 | HTTP 415 header/error contract |
| `UserApiContractIntegrationTest` | 6 | Safe user response and registration/update validation |
| `Sec003RemediationIntegrationTest` | 2 | Security ownership/role regressions |
| `UserCredentialSecurityIntegrationTest` | 3 | BCrypt limits and credential privacy with Spring beans |
| `TestIsolationIntegrationTest` | 4 | Empty-start, cross-test leakage, random-order and notification reset proof |

## Identifier and time strategy

Spring integration tests do not use global IDs such as `u1`, `s1`, `v1` or `b1`. `TestIdFactory` creates readable test-local identifiers with independent sequences, for example:

```text
bookingworkflowintegrationtest-create-user-001
bookingworkflowintegrationtest-create-vehicle-001
```

Repository-only unit tests may retain small local identifiers when each test owns a fresh repository and the ID is part of the repository contract rather than shared application state.

Booking/report fixtures use deterministic bounded dates through `TestDates` instead of short `now()+offset` windows. Concurrency tests use latches, futures and bounded timeouts; the suite does not sleep to wait for state changes.

## Maven quality gates

`./mvnw test` runs Surefire only. `./mvnw verify` and `./mvnw clean verify` run Surefire followed by Failsafe, then generate/check the combined JaCoCo report. Failsafe is bound to `integration-test` and `verify`; `*IntegrationTest` classes are excluded from Surefire so they cannot execute twice. Both plugins fail if their expected category unexpectedly contains no tests.

JUnit class and method order are randomized with a deterministic seed supplied as `test.order.seed`. CI runs complete verification with seeds `11001` and `11002`, with Spring integration-test parallel execution disabled. Reproduce a recorded ordering failure with:

```bash
./mvnw --batch-mode -Dtest.order.seed=11001 clean verify
```

No retry-on-failure configuration or `@Order` dependency is used.

## Coverage and CI artifacts

The TEST-001 measured baseline is **1,262 covered lines, 215 missed lines, 1,477 total lines, 85.44% line coverage**, while the enforced minimum remains **80%**. See `documentation/COVERAGE.md` for the baseline comparison.

The main CI verification uploads Surefire/Failsafe reports, JaCoCo output, the verified JAR and OpenAPI JSON. The test-repeatability matrix uploads seed-labelled reports on failure, and selected service/booking/RBAC tests are also executed independently. Docker validation remains a separate single execution rather than being repeated for every order seed.

# JaCoCo Coverage Baseline

TEST-001 refreshed the JaCoCo line-coverage baseline from GitHub Actions run #170 after separating Surefire and Failsafe execution and adding isolated integration-test support.

| Metric | Result |
|---|---:|
| Covered lines | 1,262 |
| Missed lines | 215 |
| Total lines | 1,477 |
| Measured line coverage | 85.44% |
| Enforced minimum | 80% |

For comparison, the successful prerequisite run #163 measured 1,259 covered lines, 216 missed lines, 1,475 total lines, or 85.36%. The TEST-001 refactor therefore preserves the existing coverage margin while making unit and integration-test execution explicit.

The minimum remains 80%. It is intentionally not raised from a single high run; any future increase should be based on a stable measured margin across repeated CI executions.

`./mvnw verify` and `./mvnw clean verify` execute unit tests through Maven Surefire, integration tests through Maven Failsafe, generate `target/site/jacoco/`, and run the JaCoCo `check` goal. The same JaCoCo agent data is appended across both test phases so the report covers the complete suite.

GitHub Actions uploads the main JaCoCo report and Surefire/Failsafe reports. TEST-001 also runs complete verification with deterministic random-order seeds `11001` and `11002`; failure artifacts are labelled with the seed so an order-related failure can be reproduced locally with `-Dtest.order.seed=<seed>`.

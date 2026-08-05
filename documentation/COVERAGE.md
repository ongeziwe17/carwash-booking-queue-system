# JaCoCo Coverage Baseline

CI-001 measured the current JaCoCo line-coverage baseline from GitHub Actions run #120.

| Metric | Result |
|---|---:|
| Covered lines | 892 |
| Missed lines | 176 |
| Total lines | 1,068 |
| Measured line coverage | 83.52% |
| Enforced minimum | 80% |

The initial minimum is set at 80% because it is supported by the measured 83.52% baseline, leaves a small margin for instrumentation or generated-code changes, and still blocks a material regression. Future changes must not reduce bundle line coverage below 80%.

The Maven `verify` lifecycle generates `target/site/jacoco/` and executes the JaCoCo `check` goal. CI uploads the complete report even when verification fails where output is available.

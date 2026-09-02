# Master Branch Protection Justification

## Purpose

This document explains the branch protection rules applied to the `master` branch and the reason behind enforcing a controlled pull request flow from `develop` into `master`.

The `master` branch represents the most stable version of the repository. Any changes merged into this branch should be reviewed, tested, and intentionally promoted from the development branch.

## Protected Branch

```text
master
```

The `master` branch is protected to reduce the risk of accidental, unreviewed, or unstable changes being introduced directly into the main release branch.

## Branch Protection Rules Applied

The following protection rules have been enabled for the `master` branch:

### 1. Require a Pull Request Before Merging

All changes targeting `master` must be submitted through a pull request.

This prevents direct commits to `master` and ensures that changes are reviewed before being merged.

### 2. Require Approvals

At least one approval is required before a pull request can be merged into `master`.

This ensures that another team member has reviewed the changes and confirmed that they are acceptable.

### 3. Dismiss Stale Pull Request Approvals

Approvals are dismissed when new commits are pushed to the pull request branch.

This is important because a pull request may change after approval. New commits could introduce issues, so the updated changes must be reviewed again.

### 4. Require Status Checks to Pass Before Merging

A required GitHub Actions status check has been configured:

```text
check-branch
```

The purpose of this check is to validate that pull requests into `master` only come from the `develop` branch.

### 5. Require Conversation Resolution Before Merging

All pull request conversations must be resolved before merging.

This ensures that review comments, concerns, questions, or requested changes are properly addressed before the code is merged into `master`.

## GitHub Actions Status Check

The repository includes a GitHub Actions workflow that enforces the allowed source branch for pull requests into `master`.

```yaml
name: Enforce develop-only PRs into master

on:
  pull_request:
    branches:
      - master

jobs:
  check-branch:
    runs-on: ubuntu-latest
    steps:
      - name: Check source branch
        run: |
          echo "Base branch: ${{ github.base_ref }}"
          echo "Head branch: ${{ github.head_ref }}"

          if [ "${{ github.head_ref }}" != "develop" ]; then
            echo "Only PRs from 'develop' are allowed into 'master'"
            exit 1
          fi
```

## Justification for Enforcing `develop` to `master`

The repository follows a controlled branch promotion strategy:

```text
feature branches → develop → master
```

This means that individual feature, bugfix, or experiment branches should first be merged into `develop`. Once the changes have been integrated, reviewed, and validated in `develop`, they can then be promoted to `master`.

This approach helps maintain a clean and predictable release flow.

## Why Direct Feature Branches Should Not Merge Into `master`

Allowing any branch to merge directly into `master` can introduce several risks:

- Unstable or incomplete work may reach the stable branch.
- Changes may bypass integration testing in `develop`.
- Multiple unrelated features may be promoted without proper coordination.
- The release history can become harder to trace.
- Review and approval standards may become inconsistent.

By only allowing pull requests from `develop` into `master`, the repository ensures that `master` receives changes only after they have passed through the agreed development workflow.

## Benefits

This branch protection setup provides the following benefits:

- Protects the stability of the `master` branch.
- Prevents accidental direct commits to `master`.
- Enforces peer review before merging.
- Ensures all discussions are resolved before merge.
- Ensures required automated checks pass before merge.
- Creates a clear promotion path from development to stable code.
- Improves traceability and accountability in the Git history.

## Expected Workflow

Developers should follow this process:

1. Create a feature or fix branch from `develop`.

   ```bash
   git checkout develop
   git checkout -b feature/example-change
   ```

2. Commit changes to the feature branch.

3. Open a pull request from the feature branch into `develop`.

4. Once reviewed and merged into `develop`, open a pull request from `develop` into `master`.

5. The pull request into `master` must pass the required `check-branch` status check.

6. After approval and conversation resolution, the pull request can be merged into `master`.

## Summary

The branch protection rules on `master` are in place to ensure that only reviewed, approved, and properly promoted changes are merged into the stable branch.

The required `check-branch` GitHub Actions workflow adds an extra safeguard by ensuring that only the `develop` branch can be used as the source branch for pull requests into `master`.

This supports a safer, cleaner, and more controlled development and release process.

## Application tenant protection

Marketplace authorization is enforced independently of branch protection. `CarWashBusiness.businessId` is the tenant boundary; Identity owns the one-to-one operational membership, Access verifies the canonical `tenant_id` claim on every token use, and application/repository layers require tenant-scoped operations. No client-supplied tenant override or wildcard administrator tenant is supported.

For mutations, the authorization decision is authoritative only inside the service's single write transaction after the resource lock is acquired. Tenant and customer repository reads/writes retain the authenticated business/user predicate through persistence; administrator mutations use an explicit administrator scope. A foreign, missing, replaced, or zero-row guarded resource fails with the same safe `404` and is never retried through an unscoped lookup. PostgreSQL advisory locks remain transaction-scoped, while the in-memory profile provides equivalent atomicity under the shared write lock.

The safe response policy is `401` for missing/invalid/forged/stale authentication, `403` for a canonical role without the required permission, and indistinguishable `404` responses for absent and foreign-tenant resources. Business owners cannot register a second business or mutate the platform-wide reusable service catalogue. Discovery remains authenticated and uses dedicated allowlisted DTOs.

The complete membership lifecycle, backfill procedure, database constraints/indexes, discovery allowlist, and #125 audit boundary are documented in [Marketplace Tenant Isolation](TENANT-ISOLATION.md).

## Notification inbox protection

Notification creation remains internal to canonical booking/queue workflows through `BookingNotificationPublisher`;
there is no client-selected recipient, tenant, type, message, POST, DELETE, or purge operation. Public representations
allow only `notificationId`, `userId`, `bookingId`, `branchId`, `serviceOfferingId`, `type`, bounded `message`,
`channel`, `sentAt`, `readAt`, and `deliveryStatus`. They never include aggregate graphs, credentials, contact/address
data, roles/membership, booking special requests, versions, persistence details, headers, tokens, cookies, exception
messages, or stack traces.

Customers list only their subject. Staff and owners may read a recipient only through the authenticated business;
platform administrators must provide one exact business scope and have no wildcard. The repository SQL includes the
recipient and business predicates, so a foreign tenant produces an empty inbox without a user/resource discovery
read. Customer/operator tenant overrides are rejected. A cursor is bounded, opaque, and bound to the complete
authenticated query scope, but repository predicates remain authoritative even if a cursor is malformed or forged.

Read-state mutation is stricter than list access: every role, including operators and platform administrators, may
modify only records whose `user_id` is its own authenticated subject. Mark-one resolves and updates through
`notification_id + user_id`, returning the same safe `404` for missing and foreign records. Mark-all requires the path
subject to equal authentication. Both execute in the shared authoritative write boundary. Repeats never advance an
existing `readAt`; PostgreSQL guarded updates and the in-memory write lock converge concurrent attempts on one state.

## Application audit protection

AUDIT-001 makes the database record authoritative and append-only. A sensitive SUCCESS record is inserted inside the same transaction as the authorized mutation; failure of that insert prevents or rolls back the protected work. A denied/failed operation first rolls back business state, then appends its safe event through an isolated write. Audit persistence failure never replaces the original safe API error and emits only a categorical operational log.

Identity and tenant snapshots come only from canonical server authentication/domain state. Invalid bearer contents are never decoded for enrichment. Metadata is allowlisted, ordered, and bounded; password material, hashes, JWTs, authorization/cookie headers, credentials, bodies, personal contact/address fields, vehicle notes, booking special requests, notification bodies, stack traces, and unrestricted exception messages are prohibited. `AUDIT_READ` is granted only to business owners and platform administrators. Owners are repository-scoped to their authenticated tenant; administrators must select an explicit tenant or platform scope, never a wildcard.

For successful tenant-owned booking mutations, actor identity and event tenant are deliberately separate snapshots. A customer or platform administrator keeps a null membership tenant in `AuditActor`; the audit command's `businessId` is derived from the canonically locked branch/booking inside the authoritative write transaction. Missing and foreign subject-scoped reads fail before that enrichment and append exactly one actor-safe DENIED record without the victim tenant.

Flyway V1–V4 remain immutable. V5 adds the audit table, constraints/indexes, and permission reference data. There is no audit write/delete/purge endpoint, SIEM integration, archive store, payment/refund implementation, or compliance certification.

# Product Specification

## Product Vision

A car wash booking and queue management backend that helps teams validate core customer, booking, queue, service catalog, notification-record, and reporting workflows with an implemented authentication/authorization baseline before adding durable persistence and SaaS platform capabilities.

## Stakeholders

- **Customers**: Need booking, vehicle, queue, and status-update workflows.
- **Car wash staff**: Need operational booking and queue visibility.
- **Business owners**: Need configurable services and eventually reporting, billing, and tenant administration.
- **Product maintainers**: Need a secure, maintainable backend that can be hardened incrementally.

## Current MVP Scope

Implemented in the current backend:

- User record management.
- Vehicle registration, lookup, update, and deletion.
- Service catalog creation, lookup, update, deletion, activation, and deactivation.
- Branch/offering-scoped booking creation, retrieval/filtering, same-branch offering update, rescheduling, confirmation, and cancellation.
- Booking-derived queue scope with branch-filtered retrieval, ordering, rebalance/call-next, offering-duration waits, call/start/complete transitions, and deletion.
- Bounded in-app notification record lookup by user with branch/offering context.
- Branch- or business-scoped daily summaries from current in-memory data using branch-local dates.
- Swagger/OpenAPI documentation.
- Secure BCrypt credential storage and stateless JWT login/current-user APIs.
- RBAC and ownership authorization for customer, staff, business-owner, and platform-admin workflows.
- Validated runtime policy configuration and standardized safe API errors.
- Local Docker and Maven workflows.
- In-memory Marketplace business/branch registration plus timezone-aware weekly hours, temporary closures, explicit-instant open-status decisions, and Catalog-owned branch service offerings.
- Authenticated nearby discovery over existing branch coordinates with deterministic straight-line distance and optional radius, effective-offering, and explicit-instant open filters.

## Partially Implemented Foundation

- JWT login and RBAC are implemented, but refresh tokens, logout/revocation, Marketplace tenant isolation, and security audit logging are not.
- Notification domain objects can track statuses, but no external SMS/email provider sends messages.
- A daily summary report endpoint exists, but richer dashboards, revenue reporting, filtering, and production analytics are not implemented.
- Repository abstractions exist, but the running application uses in-memory storage rather than durable PostgreSQL persistence.

## Functional Requirements: Current Backend

| ID    | Requirement                                                                                  | Status                |
|-------|----------------------------------------------------------------------------------------------|-----------------------|
| FR-01 | Expose APIs for managing user records.                                                       | Implemented           |
| FR-02 | Allow vehicle records to be created, retrieved, updated, deleted, and associated with users. | Implemented           |
| FR-03 | Expose service catalog APIs for available wash services.                                     | Implemented           |
| FR-04 | Require canonical branch and offering scope for booking creation, preserve it through update/reschedule/confirm/cancel, and support branch filtering. | Implemented |
| FR-05 | Derive queue scope from bookings and partition positioning, waits, rebalance, and call-next by branch. | Implemented |
| FR-06 | Maintain in-app notification records and list recent records for a user.                     | Partially implemented |
| FR-07 | Return consistent error responses for invalid requests and missing resources.                | Implemented           |
| FR-08 | Provide a basic daily summary report from current data.                                      | Partially implemented |
| FR-09 | Authenticate users with JWT and enforce current RBAC/ownership rules.                          | Implemented           |
| FR-10 | Manage Marketplace businesses/branches and evaluate branch operating status from weekly hours and temporary closures. | Implemented |
| FR-11 | Configure and discover branch-specific service price, duration, configured concurrent capacity, and activation state. | Implemented |
| FR-12 | Include bounded branch/offering notification context and require explicit branch/business daily report scope. | Implemented |
| FR-13 | Discover effective public branches by validated coordinates using deterministic distance, radius/service/open filters, and bounded responses. | Implemented |

## Functional Requirements: Planned/Future

| Capability                                                         | Target Status                   |
|--------------------------------------------------------------------|---------------------------------|
| Refresh-token/logout/revocation lifecycle, if specified.           | Future security work            |
| Security and operational audit logging.                            | Future security hardening       |
| Tenant-scoped authorization for Marketplace businesses/branches.  | Future SaaS hardening           |
| PostgreSQL persistence and migrations.                             | Planned persistence work        |
| Branch-aware availability, configured remaining capacity, and tenant isolation. | Future Marketplace/SaaS work |
| External email/SMS notification delivery.                          | Future product/platform work    |
| Payments.                                                          | Future product/platform work    |
| Ratings and feedback.                                              | Future product capability       |
| Rich reporting dashboards and analytics.                           | Future product capability       |
| Monitoring, observability, and production deployment hardening.    | Future SaaS hardening           |

## Non-Functional Requirements

| Category        | Current Expectation                                                                                                                |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------|
| Maintainability | Code should remain layered by controller, service, repository, domain, and DTO responsibilities.                                   |
| Testability     | Core service, repository, and API workflows should remain covered by automated tests.                                              |
| API usability   | Swagger/OpenAPI documentation should remain available for local development.                                                       |
| Extensibility   | Storage implementations should remain replaceable behind repository interfaces.                                                    |
| Deployment      | Docker and Docker Compose should support repeatable local execution.                                                               |
| Security        | BCrypt credentials, JWT authentication, RBAC, ownership authorization, and safe errors are implemented; tenant isolation, audit logging, durable persistence, and broader production hardening are still required. |
| Persistence     | Running application storage is currently in-memory only.                                                                           |

## Business Rules

- A booking must reference a valid customer-owned vehicle, Marketplace branch, Catalog offering belonging to that branch, and the offering-derived reusable service.
- A vehicle plate number cannot be duplicated for the same owner.
- The domain workflow constrains booking status transitions.
- Queue entries inherit immutable branch/offering scope from an eligible confirmed booking; client service input is consistency-only and mismatches are rejected.
- Queue positions, rebalancing, call-next, and offering-duration wait calculations are isolated by branch.
- Notification records preserve bounded branch/offering context in addition to channel, message, user, booking, and delivery-status metadata; external delivery is future work.
- Daily reports require exactly one branch or business scope and apply branch-local date boundaries; scopes do not enforce tenant authorization.
- Branch open status requires active business/branch state, a matching half-open weekly interval in the branch timezone, and no active covering temporary closure; public discovery is a separate decision.
- An offering is effectively active only when its stored state, reusable global service, branch, and owning business are active; discovery additionally requires branch public discovery. Configured concurrent capacity is not remaining capacity.
- Nearby discovery uses raw Haversine kilometres for filtering/sorting, rounds only output to two decimals with `HALF_UP`, defaults to branch-ID order, and never treats public visibility as anonymous authorization.

## Out of Current Scope

The current backend implements in-memory Marketplace registration/scheduling/offerings, nearby straight-line branch discovery, and branch-scoped operational workflows, but does not implement PostgreSQL persistence, payments, tenant isolation, driving routes/traffic/geocoding, remaining-capacity calculation, branch-aware availability, external notification delivery, observability, frontend applications, or production SaaS readiness. Authentication, RBAC, ownership authorization, and secure credential storage are implemented foundations.

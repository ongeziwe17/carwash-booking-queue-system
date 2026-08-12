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
- Booking creation, retrieval, update, confirmation, and cancellation.
- Queue entry creation, retrieval, position update, call/start/complete transitions, and deletion.
- In-app notification record lookup by user.
- Basic daily summary reporting from current in-memory data.
- Swagger/OpenAPI documentation.
- Secure BCrypt credential storage and stateless JWT login/current-user APIs.
- RBAC and ownership authorization for customer, staff, business-owner, and platform-admin workflows.
- Validated runtime policy configuration and standardized safe API errors.
- Local Docker and Maven workflows.

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
| FR-04 | Allow booking creation for a customer, vehicle, and service, with confirm/cancel workflows.  | Implemented           |
| FR-05 | Allow queue entries to be created, positioned, called, started, completed, and deleted.      | Implemented           |
| FR-06 | Maintain in-app notification records and list recent records for a user.                     | Partially implemented |
| FR-07 | Return consistent error responses for invalid requests and missing resources.                | Implemented           |
| FR-08 | Provide a basic daily summary report from current data.                                      | Partially implemented |
| FR-09 | Authenticate users with JWT and enforce current RBAC/ownership rules.                          | Implemented           |

## Functional Requirements: Planned/Future

| Capability                                                         | Target Status                   |
|--------------------------------------------------------------------|---------------------------------|
| Refresh-token/logout/revocation lifecycle, if specified.           | Future security work            |
| Security and operational audit logging.                            | Future security hardening       |
| Tenant-scoped authorization for Marketplace businesses/branches.  | Future SaaS hardening           |
| PostgreSQL persistence and migrations.                             | Planned persistence work        |
| Business registration and tenant isolation.                        | Future SaaS hardening           |
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

- A booking must reference valid customer, vehicle, and service records.
- A vehicle plate number cannot be duplicated for the same owner.
- The domain workflow constrains booking status transitions.
- Queue entries must reference valid booking/service context as supported by the API workflow.
- Queue positions should remain positive and drive estimated wait calculations.
- Notification records preserve channel, message, user, booking, and delivery-status metadata, but external delivery is future work.

## Out of Current Scope

The current backend implements in-memory Marketplace business/branch registration, but does not implement PostgreSQL persistence, payments, tenant isolation, branch-scoped operations, external notification delivery, observability, frontend applications, or production SaaS readiness. Authentication, RBAC, ownership authorization, and secure credential storage are implemented foundations.

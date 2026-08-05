# Product Specification

## Product Vision

A car wash booking and queue management backend that helps teams validate core customer, booking, queue, service catalog, notification-record, and reporting workflows before adding production security, persistence, and SaaS platform capabilities.

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
- Local Docker and Maven workflows.

## Partially Implemented Foundation

- `User` contains password-hash and authentication helper fields/methods, but there is no login endpoint, session/token issuance, Spring Security integration, or verified secure credential storage.
- `Role` exists as domain data, but role-based authorization is not enforced at the API layer.
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

## Functional Requirements: Planned/Future

| Capability                                                         | Target Status                   |
|--------------------------------------------------------------------|---------------------------------|
| Authentication, login/logout, and token/session management.        | Planned near-term security work |
| Secure credential hashing and storage.                             | Planned near-term security work |
| RBAC enforcement for customer, staff, owner, and admin operations. | Planned near-term security work |
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
| Security        | Production authentication, authorization, and credential handling are not yet implemented and must be added before production use. |
| Persistence     | Running application storage is currently in-memory only.                                                                           |

## Business Rules

- A booking must reference valid customer, vehicle, and service records.
- A vehicle plate number cannot be duplicated for the same owner.
- The domain workflow constrains booking status transitions.
- Queue entries must reference valid booking/service context as supported by the API workflow.
- Queue positions should remain positive and drive estimated wait calculations.
- Notification records preserve channel, message, user, booking, and delivery-status metadata, but external delivery is future work.

## Out of Current Scope

The current backend does not implement authentication, RBAC, secure credential storage, PostgreSQL persistence, payments, business registration, multi-tenancy, external notification delivery, observability, frontend applications, or production SaaS readiness.
# Product Specification

## Product Vision

A web-based car wash booking and queue management platform that helps customers book services, join and manage queues, receive updates, and helps car wash businesses manage daily operations efficiently.

## Stakeholders

- **Customers**: Book wash services, manage vehicles, view queue status, and receive updates.
- **Car wash staff**: Monitor bookings, manage queue progress, and coordinate customer service.
- **Business owners**: Configure service offerings and track operational performance.
- **Product maintainers**: Build and operate a secure, maintainable backend platform.

## MVP Scope

The current backend supports the core API foundation for:

- User record management.
- Vehicle registration and lookup.
- Service catalog operations.
- Booking creation and retrieval.
- Queue entry creation and position management.
- Notification record creation and retrieval.

## Functional Requirements

| ID | Requirement |
| --- | --- |
| FR-01 | The system shall expose APIs for managing user records. |
| FR-02 | The system shall allow customers to register and retrieve vehicles. |
| FR-03 | The system shall expose service catalog APIs for available wash services. |
| FR-04 | The system shall allow booking creation for a customer, vehicle, and service. |
| FR-05 | The system shall allow customers to join and track queue entries. |
| FR-06 | The system shall maintain notification records for customer updates. |
| FR-07 | The system shall return consistent error responses for invalid requests and missing resources. |

## Non-Functional Requirements

| Category | Requirement |
| --- | --- |
| Maintainability | Code should remain layered by controller, service, repository, domain, and DTO responsibilities. |
| Testability | Core service and API workflows should be covered by automated tests. |
| API usability | Swagger/OpenAPI documentation should remain available for local development. |
| Extensibility | Storage implementations should remain replaceable behind repository interfaces. |
| Deployment | Docker and Docker Compose should continue to support repeatable local execution. |

## Business Rules

- A booking must reference valid customer, vehicle, and service records.
- Queue entries must reference valid bookings or service context as supported by the API workflow.
- Queue positions should be managed consistently when entries are created or updated.
- Notification records should preserve delivery channel, recipient, message, and status information.

## Out of Scope for This Cleanup

This cleanup does not add authentication, role-based access control, payment processing, durable persistence, or multi-tenancy. Those remain roadmap items.

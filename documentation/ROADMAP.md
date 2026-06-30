# Product Roadmap

This roadmap separates what is implemented in the current backend foundation from planned hardening and future SaaS/platform capabilities. It should not be read as a production-readiness claim.

## Current Foundation

Implemented in the current backend:

- Spring Boot REST API controllers for users, vehicles, services, bookings, queue entries, notifications, and daily summary reports.
- Service-layer business rules for resource lookup, duplicate user email checks, duplicate vehicle plates per owner, booking validation, and queue status transitions.
- Repository interfaces with in-memory implementations used by the running application and tests.
- Basic `DatabaseUserRepository` abstraction test coverage, but no configured production database runtime.
- Swagger/OpenAPI documentation.
- Docker and Docker Compose support for local execution.
- Automated service-layer, repository, and API integration tests.

## Near-Term Backend Hardening

1. Strengthen booking and queue validation around time slots, capacity, cancellation windows, and status transitions.
2. Expand API integration tests for customer and operator workflows.
3. Standardize request/response DTOs and API error contracts.
4. Improve notification workflow boundaries so provider integrations can be added safely later.
5. Keep product documentation aligned with the implemented API behavior.

## Security and Access-Control Roadmap

Not yet implemented:

1. Secure authentication and session/token handling.
2. Secure credential hashing and storage.
3. Spring Security integration.
4. Role-based access control for customers, staff, owners, and administrators.
5. Audit logging for sensitive administrative actions.

## Persistence Roadmap

Currently, in-memory only for the running backend. Planned work:

1. Add PostgreSQL persistence for all domain aggregates.
2. Add schema migrations and repeatable local database setup.
3. Define transaction boundaries for booking and queue operations.
4. Add persistence-focused integration tests.
5. Add backup/restore and data retention guidance for production environments.

## SaaS/Platform Roadmap

Future SaaS hardening:

1. Business registration and tenant-aware data modeling.
2. Tenant isolation across APIs, repositories, reports, and notifications.
3. Subscription or billing-plan support.
4. Production deployment configuration for container/cloud platforms.
5. Monitoring, metrics, tracing, structured logging, and operational alerts.
6. Security hardening, secrets management, and environment-specific configuration.

## Future Product Capabilities

Future product enhancements, not current backend capabilities:

1. Payment provider workflows for deposits or full payments.
2. External email/SMS notification delivery with retries and delivery status reconciliation.
3. Rich reporting dashboards for revenue, throughput, utilization, and queue performance.
4. Ratings and customer feedback.
5. Customer-facing and operator-facing frontend applications.
6. Advanced scheduling, capacity planning, and multi-location operations.

## Maintenance Principles

- Prefer product workflow tests over isolated examples.
- Keep documentation practical for contributors and operators.
- Avoid adding demo-only code that is not part of the backend product path.
- Keep infrastructure changes incremental and verifiable.
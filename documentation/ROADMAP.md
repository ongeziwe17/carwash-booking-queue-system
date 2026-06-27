# Product Roadmap

This roadmap tracks the evolution of the Car Wash Booking Queue System from a clean backend foundation toward a production-ready SaaS platform.

## Current Foundation

- Spring Boot REST API for users, vehicles, services, bookings, queue entries, and notifications.
- Service-layer business rules and validation.
- Repository interfaces with in-memory implementations for local development and automated tests.
- Swagger/OpenAPI documentation.
- Docker and Docker Compose support.

## Near-Term Priorities

1. Strengthen booking and queue validation around time slots, capacity, and status transitions.
2. Expand API integration tests for common customer and operator workflows.
3. Improve notification workflow boundaries so provider integrations can be added safely.
4. Keep product documentation aligned with implemented API behavior.

## SaaS Readiness

1. Add secure authentication and credential storage.
2. Add role-based access control for customers, staff, and business owners.
3. Introduce durable database persistence and migrations.
4. Add tenant-aware data modeling for multiple car wash businesses.
5. Add business owner reporting for bookings, revenue, throughput, and queue performance.
6. Integrate payment provider workflows for deposits or full payments.
7. Add email/SMS notification providers and delivery retry handling.
8. Add observability with structured logging, metrics, tracing, and health checks.
9. Harden container and cloud deployment configuration.

## Maintenance Principles

- Prefer product workflow tests over isolated examples.
- Keep documentation practical for contributors and operators.
- Avoid adding demo-only code that is not part of the backend product path.
- Keep infrastructure changes incremental and verifiable.

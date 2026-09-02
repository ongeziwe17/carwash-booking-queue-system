# System Requirements Document (SRD)

## Web-Based Car Wash Booking and Queue Management System

**Version:** 1.0

**Date:** March 2026

**Status:** Draft aligned to current backend foundation

## 1. Introduction

### 1.1 Project Overview

This document describes the current backend foundation and planned requirements for a car wash booking and queue management system. The Spring Boot backend supports lightweight in-memory storage and an explicit durable PostgreSQL profile.

### 1.2 Purpose

The purpose of this document is to clarify what the system currently supports and what remains planned or future work.

### 1.3 Current Scope

The current backend includes:

- REST APIs for user record management.
- REST APIs for vehicle management.
- REST APIs for service catalog management.
- REST APIs for booking and queue workflows.
- In-app notification record lookup.
- Basic daily summary reporting from selected-profile data.
- Marketplace businesses/branches with validated coordinates, scheduling, offerings, branch-scoped operations, and authenticated nearby discovery.
- Authenticated, explainable Marketplace branch recommendations using authoritative availability eligibility and deterministic rule-based scoring.
- Swagger/OpenAPI documentation.
- Secure BCrypt credential storage, JWT authentication, RBAC, customer ownership authorization, and canonical Marketplace tenant isolation.
- Standardized API errors and validated runtime/business policy configuration.
- Docker/local development support.
- Flyway migrations, module-owned PostgreSQL adapters, transactional workflows, cross-instance capacity/queue locking, and restart durability.

The current backend does not include a frontend application, managed database provisioning/backups, payments, external notifications, SIEM/archive/automated audit retention, or production SaaS hardening. Marketplace tenant identity, data isolation, and append-only application audit history are implemented in both persistence profiles; database-per-tenant and federation are not.

## 2. Stakeholder Analysis Summary

| Stakeholder          | Role                            | Current Concerns                                                    | Future Concerns                                          |
|----------------------|---------------------------------|---------------------------------------------------------------------|----------------------------------------------------------|
| Customer             | Books services and joins queues | Secure login plus accurate booking, vehicle, queue, and notification-record workflows | External notifications, payments, feedback |
| Business Owner       | Manages services and operations | Assigned-business offerings, bookings, queues, schedules, and reports | Payments and richer dashboards                         |
| Service Staff        | Performs car wash services      | Tenant-scoped queue and booking status flow                           | Richer operational tooling                             |
| System Administrator | Maintains system                | Local reliability and maintainable APIs                             | Security, observability, production operations           |
| Platform Owner       | Oversees system direction       | Clear implementation status                                         | SaaS readiness and tenant isolation                      |

## 3. Functional Requirements: Current Backend

### FR1: User Record Management

**Description:** The system exposes APIs to create, retrieve, update, and delete user records.

**Status:** Implemented.

### FR2: Service Catalog

**Description:** The system exposes APIs to create, retrieve, update, delete, activate, and deactivate car wash services.

**Status:** Implemented.

### FR3: Booking System

**Description:** The system requires each booking to reference a valid user-owned vehicle, Marketplace branch, and branch service offering, and preserves that scope through its lifecycle.

**Status:** Implemented for backend workflows. New bookings, offering changes, and rescheduling share continuous-operating-window slot alignment, branch-hours, closure, offering-capacity, and lifecycle validation with branch-aware availability search.

### FR4: Queue Management

**Description:** The system derives queue branch/offering scope from an eligible booking and isolates positioning, waits, rebalance, call-next, and lifecycle operations by branch.

**Status:** Implemented for backend workflows. Advanced capacity and operational rules need hardening.

### FR5: Notification Records

**Description:** The system stores and retrieves bounded in-app notification records with booking, branch, and service-offering context.

**Status:** Implemented as records only. External SMS/email delivery is not implemented.

### FR6: Basic Reporting

**Description:** The system provides a daily summary endpoint for exactly one branch or owning-business scope, computed with branch-local date semantics from the selected persistence profile.

**Status:** Partially implemented. Rich dashboards, revenue reports, and production analytics are future work.

### FR7: Nearby Branch Discovery

**Description:** The system returns only effective active, public-discovery-enabled Marketplace branches for required coordinates and supports optional bounded radius, effective service-offering, and explicit-instant open filters with deterministic straight-line distance ordering.

**Status:** Implemented under both persistence profiles with Haversine distance and no external maps, routing, traffic, or geocoding provider.

### FR8: Explainable Branch Recommendations

**Description:** The system returns a deterministically ranked point-in-time view of booking-eligible branches for an exact requested service and offset-aware desired start. It supports nearest, shortest known queue, fastest known total time, lowest price, and weighted best-overall preferences over the same AVAIL-002 candidate set. Scores use raw internal metrics and decimal-safe min-max normalization; responses expose rounded metrics, component scores, configured weights, contributions, and customer-safe explanations.

**Status:** Implemented under both persistence profiles. Unknown future branch-local queue and total-time values remain `null` and rank last for dependent preferences. Recommendations do not reserve capacity, and booking creation performs authoritative revalidation.

### FR9: Durable Persistence

**Description:** The explicit `postgres` profile stores every current aggregate in a Flyway-owned schema through module-local adapters. Multi-record workflows use database transactions, booking capacity and branch queue decisions use transaction-scoped cross-instance locks, and ordinary mutable rows use optimistic versions.

**Status:** Implemented. The default/test profile remains in memory; managed provisioning, backup/restore automation, and replicas are not included.

### FR10: Marketplace Tenant Isolation

**Description:** The system binds each staff member/business owner to exactly one canonical business, validates that assignment on every token use, and requires subject-, tenant-, or explicit administrator-scoped application/repository operations.

**Status:** Implemented under in-memory and PostgreSQL profiles. Existing unassigned operational users fail closed until a platform administrator explicitly assigns a verified business. Foreign and missing tenant resources produce indistinguishable `404` responses.

## 4. Planned/Future Functional Requirements

| Requirement                                        | Status                                       |
|----------------------------------------------------|----------------------------------------------|
| Refresh-token/logout/revocation lifecycle, if specified | Future security work                     |
| SIEM, audit archive/automated retention, certification | Future security hardening                |
| Marketplace tenant-scoped authorization                | Implemented                              |
| Database-per-tenant isolation or federation             | Out of current scope                     |
| External email/SMS notification delivery           | Future product/platform work                 |
| Payments                                           | Future product/platform work                 |
| Ratings and feedback                               | Future product capability                    |
| Monitoring/observability                           | Future SaaS hardening                        |
| Production deployment hardening                    | Future SaaS hardening                        |

## 5. Non-Functional Requirements

### 5.1 Maintainability

- The system should keep controller, service, repository, domain, and DTO responsibilities separated.
- Documentation should reflect the actual implementation status.

### 5.2 Testability

- Service-layer and API integration tests should cover implemented workflows.
- Persistence, security, and SaaS capabilities include dedicated tests as they are implemented.

### 5.3 API Usability

- Swagger/OpenAPI should remain available for local development.
- Endpoint documentation should list only implemented endpoints unless planned endpoints are explicitly marked as planned.

### 5.4 Security

- BCrypt credential storage, JWT authentication, RBAC, ownership authorization, tenant isolation, and safe API errors are implemented.
- Audit logging, backup/restore operations, observability, and deployment hardening remain required before production SaaS use.

### 5.5 Persistence and Reliability

- The default/test runtime is in-memory; `postgres` supplies durable storage and Flyway migrations.
- Managed provisioning, backup/restore automation, and data-retention operations remain future work.

### 5.6 Deployability

- Docker support is available for local execution.
- Cloud deployment, secrets management, monitoring, and production hardening are future work.

## 6. Constraints and Assumptions

### Constraints

- Payment integration is not included in the current backend.
- No frontend application is included in the current backend repository.
- In-memory runtime data is not durable; PostgreSQL-profile data survives API/container restart when the database volume remains.

### Assumptions

- Current APIs are used for backend validation, local development, and automated tests.
- Production use still requires auditability, backup/restore operations, observability, and deployment hardening.
- Operational filters and reports carry a canonical tenant predicate; platform administrators use explicit concrete scopes.

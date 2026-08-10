# System Requirements Document (SRD)

## Web-Based Car Wash Booking and Queue Management System

**Version:** 1.0

**Date:** March 2026

**Status:** Draft aligned to current backend foundation

## 1. Introduction

### 1.1 Project Overview

This document describes the current backend foundation and planned requirements for a car wash booking and queue management system. The current implementation is a Spring Boot backend with in-memory storage for local development and tests.

### 1.2 Purpose

The purpose of this document is to clarify what the system currently supports and what remains planned or future work.

### 1.3 Current Scope

The current backend includes:

- REST APIs for user record management.
- REST APIs for vehicle management.
- REST APIs for service catalog management.
- REST APIs for booking and queue workflows.
- In-app notification record lookup.
- Basic daily summary reporting from in-memory data.
- Swagger/OpenAPI documentation.
- Secure BCrypt credential storage, JWT authentication, RBAC, and ownership authorization.
- Standardized API errors and validated runtime/business policy configuration.
- Docker/local development support.

The current backend does not include a frontend application, durable PostgreSQL persistence, payments, external notifications, multi-tenancy, or production SaaS hardening. Authentication and RBAC are implemented but are not a substitute for Marketplace tenant isolation.

## 2. Stakeholder Analysis Summary

| Stakeholder          | Role                            | Current Concerns                                                    | Future Concerns                                          |
|----------------------|---------------------------------|---------------------------------------------------------------------|----------------------------------------------------------|
| Customer             | Books services and joins queues | Secure login plus accurate booking, vehicle, queue, and notification-record workflows | External notifications, payments, feedback |
| Business Owner       | Manages services and operations | Service, booking, queue, and basic report visibility                | Tenant management, reports, payments, dashboards         |
| Service Staff        | Performs car wash services      | RBAC-protected queue and booking status flow                         | Tenant-scoped operational workflows                     |
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

**Description:** The system allows bookings to be created and managed for valid user, vehicle, and service records.

**Status:** Implemented for backend workflows. Advanced slot/capacity rules need hardening.

### FR4: Queue Management

**Description:** The system allows queue entries to be created, positioned, called, started, completed, and deleted.

**Status:** Implemented for backend workflows. Advanced capacity and operational rules need hardening.

### FR5: Notification Records

**Description:** The system stores and retrieves in-app notification records.

**Status:** Implemented as records only. External SMS/email delivery is not implemented.

### FR6: Basic Reporting

**Description:** The system provides a daily summary endpoint computed from current in-memory data.

**Status:** Partially implemented. Rich dashboards, revenue reports, and production analytics are future work.

## 4. Planned/Future Functional Requirements

| Requirement                                        | Status                                       |
|----------------------------------------------------|----------------------------------------------|
| Refresh-token/logout/revocation lifecycle, if specified | Future security work                     |
| Security and operational audit logging                 | Future security hardening                |
| Marketplace tenant-scoped authorization                | Future SaaS hardening                    |
| PostgreSQL persistence and migrations              | Planned; not implemented for the running app |
| Business registration and multi-tenancy            | Future SaaS hardening                        |
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
- Future persistence, security, and SaaS capabilities should include dedicated tests when implemented.

### 5.3 API Usability

- Swagger/OpenAPI should remain available for local development.
- Endpoint documentation should list only implemented endpoints unless planned endpoints are explicitly marked as planned.

### 5.4 Security

- BCrypt credential storage, JWT authentication, RBAC, ownership authorization, and safe API errors are implemented.
- Tenant isolation, audit logging, durable persistence, observability, and deployment hardening remain required before production SaaS use.

### 5.5 Persistence and Reliability

- Current runtime storage is in-memory only.
- Durable database storage, migrations, backup/restore, and data-retention practices are planned/future work.

### 5.6 Deployability

- Docker support is available for local execution.
- Cloud deployment, secrets management, monitoring, and production hardening are future work.

## 6. Constraints and Assumptions

### Constraints

- Payment integration is not included in the current backend.
- No frontend application is included in the current backend repository.
- Current runtime data is not durable across application restarts.

### Assumptions

- Current APIs are used for backend validation, local development, and automated tests.
- Production use requires security, persistence, observability, and deployment hardening first.
- The initial implementation assumes a single-location/non-tenant workflow until SaaS tenancy is introduced.

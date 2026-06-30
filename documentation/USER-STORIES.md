# Agile User Stories

## 1. Introduction

These user stories are organized by implementation status so current backend capabilities are not mixed with planned security, persistence, SaaS, or product enhancements.

## 2. Implemented in the Current Backend

| Story ID | User Story | Acceptance Criteria | Status |
| --- | --- | --- | --- |
| US-001 | As an operator, I want to manage user records so that customer data can be referenced by bookings and vehicles. | 1) Create user records.<br>2) Retrieve users by ID or list users.<br>3) Update and delete user records.<br>4) Reject duplicate email addresses. | Implemented |
| US-002 | As a customer or operator, I want to manage vehicle records so bookings can reference a customer's vehicle. | 1) Create vehicles for a user.<br>2) Retrieve, update, and delete vehicles.<br>3) Prevent duplicate plates for the same owner. | Implemented |
| US-003 | As an operator, I want to manage the service catalog so available wash services are represented in the backend. | 1) Create, retrieve, update, and delete services.<br>2) Activate and deactivate services.<br>3) Store price and estimated duration. | Implemented |
| US-004 | As an operator, I want to create and manage bookings so customer wash requests can be tracked. | 1) Create bookings for valid user, vehicle, and service records.<br>2) Retrieve and update bookings.<br>3) Confirm and cancel bookings. | Implemented |
| US-005 | As staff, I want to manage queue entries so service progress can be tracked. | 1) Create queue entries.<br>2) Update queue positions.<br>3) Call, start, and complete queue entries.<br>4) Delete queue entries. | Implemented |
| US-006 | As a customer or operator, I want to view notification records so booking/queue messages can be tracked in-app. | 1) Notification records can be associated with users and bookings.<br>2) Recent notifications can be listed by user. | Implemented for in-app records only |
| US-007 | As an operator, I want a basic daily summary so I can inspect current booking and queue counts. | 1) Request a summary by date.<br>2) See basic booking and queue totals from current in-memory data. | Partially implemented |

## 3. Planned Near-Term Stories

| Story ID | User Story | Acceptance Criteria | Status |
| --- | --- | --- | --- |
| US-008 | As a maintainer, I want stronger booking validation so invalid time slots and capacity conflicts are rejected consistently. | 1) Past/invalid time slots are rejected.<br>2) Capacity rules are enforced.<br>3) API tests cover invalid scenarios. | Planned |
| US-009 | As staff, I want stricter queue transition rules so operational state remains consistent. | 1) Invalid transitions are rejected.<br>2) Queue position changes are consistent.<br>3) Tests cover transition failures. | Planned |
| US-010 | As a maintainer, I want consistent DTO and error response documentation so API consumers can integrate reliably. | 1) Request/response shapes are documented.<br>2) Error responses are consistent.<br>3) Swagger matches implemented behavior. | Planned |

## 4. Planned Security and Access-Control Stories

| Story ID | User Story | Acceptance Criteria | Status |
| --- | --- | --- | --- |
| US-011 | As a customer, I want to authenticate securely so my booking data can be protected. | 1) Login endpoint exists.<br>2) Tokens or sessions are issued securely.<br>3) Invalid credentials are rejected safely. | Planned; not implemented |
| US-012 | As the system, I want secure credential storage so passwords are not stored or compared unsafely. | 1) Passwords are hashed with a suitable algorithm.<br>2) Plain-text passwords are not persisted.<br>3) Tests verify credential behavior. | Planned; not implemented |
| US-013 | As an administrator, I want role-based access control so only authorized users can perform staff/owner/admin actions. | 1) Customer, staff, owner, and admin permissions are defined.<br>2) Controllers enforce permissions.<br>3) Unauthorized requests are denied. | Planned; not implemented |

## 5. Planned Persistence Stories

| Story ID | User Story | Acceptance Criteria | Status |
| --- | --- | --- | --- |
| US-014 | As an operator, I want data to persist across restarts so production records are not lost. | 1) PostgreSQL stores core entities.<br>2) Migrations manage schema changes.<br>3) Integration tests validate persistence behavior. | Planned; not implemented |

## 6. Future SaaS and Product Stories

| Story ID | User Story | Acceptance Criteria | Status |
| --- | --- | --- | --- |
| US-015 | As a business owner, I want to register my business so my car wash can be managed independently. | Business and tenant records are created and isolated. | Future |
| US-016 | As a platform owner, I want tenant isolation so multiple businesses can safely use the platform. | APIs, data, reports, and notifications are tenant-scoped. | Future |
| US-017 | As a customer, I want payment options so I can pay deposits or full amounts online. | Payment provider checkout, webhook, and receipt flows exist. | Future |
| US-018 | As a customer, I want SMS/email updates so I receive notifications outside the app. | Provider integrations deliver messages and record delivery outcomes. | Future |
| US-019 | As a business owner, I want richer reports so I can track revenue, throughput, and queue performance. | Dashboards and analytics filters are available. | Future |
| US-020 | As a customer, I want to rate completed services so businesses can collect feedback. | Ratings and feedback can be submitted and reviewed. | Future |

## 7. INVEST Alignment Note

Stories remain independent enough to schedule incrementally, negotiable in implementation detail, valuable to named stakeholders, estimable at backlog level, small enough for focused delivery, and testable through clear acceptance criteria.

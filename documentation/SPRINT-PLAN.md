# Sprint Planning

## 1. Sprint Goal

This 2-week delivery cycle aims to deliver a usable MVP flow that allows a customer to create an account, securely sign in, browse available car wash services, book a service slot, and join the virtual queue. Completing this flow provides the project’s first end-to-end customer journey and establishes the technical foundation for future operational and reporting features.

By the end of the delivery cycle, the team should have reliable core APIs, validated data handling, and basic user-facing endpoints/pages that support booking and queue entry with proper authentication. This creates immediate business value by enabling digital self-service, reducing manual booking overhead, and preparing the platform for staff-side management features in later delivery cycles.

---

## 2. Selected User Stories

- US-001 – Register account
- US-002 – Authenticate user
- US-003 – Browse service catalog
- US-004 – Create booking
- US-005 – Join virtual queue

---

## 3. Sprint Backlog (Tasks)

| Task ID | Task Description | Assigned To | Estimated Hours | Status |
|--------|-----------------|-------------|-----------------|--------|
| T-001 | Design and implement user database schema (users, roles, auth fields) for US-001/US-002 | Backend Dev | 5 | To Do |
| T-002 | Build register account API endpoint with input validation and unique email checks (US-001) | Backend Dev | 6 | To Do |
| T-003 | Implement password hashing and secure credential persistence for registration/authentication (US-001, US-002) | Backend Dev | 4 | To Do |
| T-004 | Build login/authentication API with token/session generation and invalid-credential handling (US-002) | Backend Dev | 6 | To Do |
| T-005 | Create service catalog schema/seed data and active-service query endpoint (US-003) | Backend Dev | 5 | To Do |
| T-006 | Build booking schema and create-booking API with slot availability/past-time validation (US-004) | Backend Dev | 8 | To Do |
| T-007 | Implement virtual queue schema and join-queue API with queue number and ETA calculation (US-005) | Backend Dev | 8 | To Do |
| T-008 | Develop basic registration and login UI forms integrated with auth APIs (US-001, US-002) | Frontend Dev | 7 | To Do |
| T-009 | Develop service catalog UI view to display service name, description, and pricing (US-003) | Frontend Dev | 5 | To Do |
| T-010 | Develop booking creation UI flow (service + date/time selection + confirmation) (US-004) | Frontend Dev | 7 | To Do |
| T-011 | Develop join virtual queue UI action and confirmation display (queue number + ETA) (US-005) | Frontend Dev | 6 | To Do |
| T-012 | Add integration tests for auth, catalog retrieval, booking creation, and queue join happy paths (US-001 to US-005) | Dev Team | 7 | To Do |

---

## 4. Notes

- All delivery cycle tasks are derived directly from selected Must-have user stories (US-001 to US-005).
- Hour estimates are sized for a 2-week delivery cycle and include implementation plus basic verification.
- This delivery cycle focuses on the core system foundation and a usable customer-facing vertical slice for MVP delivery.

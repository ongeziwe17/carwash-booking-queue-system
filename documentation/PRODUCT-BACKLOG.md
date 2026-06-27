# Product Backlog

## 1. Overview

This product backlog compiles user stories derived from functional requirements and use cases. The backlog is prioritized using the MoSCoW method to ensure that core system functionality is delivered first in alignment with stakeholder needs and MVP scope.

---

## 2. Backlog Table

| Story ID | User Story | Priority (MoSCoW) | Effort (Story Points) | Dependencies |
|----------|------------|------------------|----------------------|--------------|
| US-001 | Register account | Must-have | 3 | None |
| US-002 | Authenticate user | Must-have | 3 | US-001 |
| US-003 | Browse service catalog | Must-have | 2 | None |
| US-004 | Create booking | Must-have | 5 | US-002, US-003 |
| US-005 | Join virtual queue | Must-have | 5 | US-004 |
| US-006 | View queue position | Should-have | 2 | US-005 |
| US-007 | Receive notifications | Could-have | 3 | US-004, US-005 |
| US-008 | Manage services | Must-have | 3 | US-002 |
| US-009 | Manage bookings and queue | Must-have | 5 | US-004, US-005 |
| US-010 | View reports | Should-have | 3 | US-009 |
| US-011 | Role-based access control | Must-have | 5 | US-002 |
| US-012 | Cancel booking | Should-have | 2 | US-004 |
| US-013 | Secure credential storage | Must-have | 3 | US-002 |

---

## 3. Prioritization Justification

The backlog is prioritized using the MoSCoW technique:

- **Must-have** features represent the core functionality required for the system to operate, including authentication, booking creation, queue management, and administrative controls. These directly align with stakeholder success metrics such as usability, reliability, and operational efficiency.

- **Should-have** features enhance user experience and system usability, such as viewing queue positions, reporting, and booking cancellation. These are important but not critical for initial system operation.

- **Could-have** features provide additional convenience and user engagement, such as notifications. These can be deferred without affecting core system functionality.

- **Won’t-have (for now)** features are intentionally excluded from the MVP scope to maintain focus on essential system delivery.

---

## 4. Notes

- Effort estimates use a simplified Fibonacci-like scale (1–5)
- Dependencies ensure logical implementation order
- Backlog aligns with Agile principles and supports delivery planning

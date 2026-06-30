# Product Backlog

## 1. Overview

This backlog separates completed backend foundation work from planned and future work. Items marked completed describe the current backend implementation only; they do not imply production readiness.

## 2. Completed / Current Backend Foundation

| Item ID     | Backlog Item                    | Status              | Notes                                                                                            |
|-------------|---------------------------------|---------------------|--------------------------------------------------------------------------------------------------|
| Feature-001 | User record management          | Completed           | CRUD-style user record APIs and validation are present.                                          |
| Feature-002 | Vehicle management              | Completed           | Vehicle CRUD, owner association, and duplicate plate checks per owner are present.               |
| Feature-003 | Service catalog management      | Completed           | Service CRUD and activate/deactivate workflows are present.                                      |
| Feature-004 | Booking workflow                | Completed           | Create, retrieve, update, confirm, and cancel workflows are present.                             |
| Feature-005 | Queue workflow                  | Completed           | Queue create, position update, call, start, complete, and delete workflows are present.          |
| Feature-006 | In-app notification records     | Completed           | Recent notification records can be retrieved by user. External delivery is not present.          |
| Feature-007 | Swagger/OpenAPI documentation   | Completed           | Springdoc Swagger UI/OpenAPI is available locally.                                               |
| Feature-008 | Docker/local development setup  | Completed           | Dockerfile and Docker Compose support local runs.                                                |
| Feature-009 | Daily summary report foundation | Partially completed | Basic in-memory daily summary endpoint exists; dashboards and revenue reporting are future work. |

## 3. Planned Near-Term Backend Hardening

| Item ID   | Backlog Item                                        | Priority | Notes                                                                                     |
|-----------|-----------------------------------------------------|----------|-------------------------------------------------------------------------------------------|
| Fix-001   | Strengthen booking validation                       | High     | Time slot, past-date, capacity, and cancellation-window rules need hardening.             |
| Fix-002   | Strengthen queue validation                         | High     | Queue ordering, transitions, and service capacity rules need deeper coverage.             |
| Test-001  | Expand API integration tests                        | High     | Add more end-to-end API scenarios for booking, queue, notification, and report workflows. |
| Docs-006  | Align implemented and planned feature documentation | High     | Keep docs from overclaiming planned SaaS capabilities.                                    |
| Chore-005 | Audit project status, documentation, and backlog    | High     | Related umbrella documentation/status audit.                                              |

## 4. Planned Security and Access-Control Work

| Item ID      | Backlog Item                               | Priority | Status                                                       |
|--------------|--------------------------------------------|----------|--------------------------------------------------------------|
| Security-001 | Add authentication                         | High     | Planned; not implemented.                                    |
| Security-002 | Add secure credential hashing/storage      | High     | Planned; not implemented.                                    |
| Security-003 | Add RBAC enforcement                       | High     | Planned; role data exists but authorization is not enforced. |
| Security-004 | Add audit logging for sensitive operations | Medium   | Planned; not implemented.                                    |

## 5. Planned Persistence Work

| Item ID     | Backlog Item                      | Priority | Status                                                      |
|-------------|-----------------------------------|----------|-------------------------------------------------------------|
| Feature-010 | Add PostgreSQL persistence        | High     | Planned; running app currently uses in-memory repositories. |
| Feature-011 | Add database migrations           | High     | Planned; not implemented.                                   |
| Test-002    | Add persistence integration tests | Medium   | Planned after database persistence is introduced.           |

## 6. Future SaaS Hardening

| Item ID     | Backlog Item                       | Priority | Status  |
|-------------|------------------------------------|----------|---------|
| Feature-012 | Business registration              | Medium   | Future. |
| Feature-013 | Multi-tenancy and tenant isolation | High     | Future. |
| Feature-014 | Payments                           | Medium   | Future. |
| Feature-015 | External SMS/email notifications   | Medium   | Future. |
| Feature-016 | Monitoring and observability       | Medium   | Future. |
| Feature-017 | Production deployment hardening    | High     | Future. |

## 7. Future Product Capabilities

| Item ID     | Backlog Item                              | Priority | Status  |
|-------------|-------------------------------------------|----------|---------|
| Feature-018 | Ratings and feedback                      | Low      | Future. |
| Feature-019 | Rich reports and dashboards               | Medium   | Future. |
| Feature-020 | Customer/operator frontend                | Medium   | Future. |
| Feature-021 | Advanced scheduling and capacity planning | Medium   | Future. |
| Feature-022 | Advanced analytics and reporting          | Medium   | Future. |

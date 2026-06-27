# carwash-booking-queue-system

A Web-Based Car Wash Booking and Queue Management System

## Overview

The Web-Based Car Wash Booking and Queue Management System is a software platform designed to improve how car wash services manage customer queues and service availability. Many small car wash businesses rely on manual walk-in systems where customers must physically wait in line without knowing queue length or estimated waiting times. This often results in inefficient service management and poor customer experience.

This system introduces a digital platform that allows customers to view available car wash services, check queue length, and join a service queue remotely. At the same time, car wash businesses can manage service queues, update job statuses, and monitor daily workload through a centralized interface.

## Purpose of the Project

The purpose of this project is to design and demonstrate a digital system that improves queue visibility and operational efficiency for car wash businesses. The system will provide a structured way for customers and service providers to interact through a web-based platform.

The project is being developed as part of the **Software Engineering System Specification and Architectural Modeling assignment**, where the system will be fully documented and modelled using the **C4 architectural model**.

## System Goals

The system aims to:

- Provide customers with visibility into available car wash services
- Allow customers to join or schedule service queues digitally
- Enable car wash businesses to manage bookings and service flow
- Improve operational efficiency and customer experience
- Demonstrate a scalable system architecture that can support future expansion

## Core System Features

The initial system prototype will support:

- Customer registration and login
- Vehicle registration
- Car wash business registration
- Service listing and pricing management
- Digital queue and booking management
- Service status updates
- Customer ratings and feedback

## Getting Started

Follow these steps to set up the project locally for development and testing.

### Clone

```bash
git clone https://github.com/ongeziwe17/carwash-booking-queue-system.git
cd carwash-booking-queue-system
```

### Build

```bash
./mvnw clean package
```

### Run

```bash
./mvnw spring-boot:run
```

### Run with Docker Compose

Build and start the Spring Boot API locally with Docker Compose:

```bash
docker compose up --build
```

The API is available at:

```text
http://localhost:8080
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

Stop the Compose stack with:

```bash
docker compose down
```


### Swagger UI

After starting the Spring Boot application, open Swagger UI at:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON is available at:

```text
http://localhost:8080/v3/api-docs
```

## Project Status

This repository is maintained as an academic and portfolio project.
External contributions are not currently being accepted.


## Project Documentation

Detailed system documentation and architecture diagrams are available in the following files:

- [System Specification](documentation/SPECIFICATION.md)
- [System Architecture](documentation/ARCHITECTURE.md)
- [Stakeholder Analysis](documentation/STAKEHOLDER-ANALYSIS.md)
- [System Requirements](documentation/SYSTEM-REQUIREMENTS.md)
- [Reflection](documentation/REFLECTION.md)
- [Use Case Diagram](documentation/USE-CASE-DIAGRAM.md)
- [Use Case Specifications](documentation/USE-CASE-SPECIFICATION.md)
- [Test Case Development](documentation/TEST-CASES.md)
- [Agile User Stories](documentation/USER-STORIES.md)
- [Product Backlog](documentation/PRODUCT-BACKLOG.md)
- [Sprint Planning](documentation/SPRINT-PLAN.md)
- [Template Analysis](documentation/TEMPLATE-ANALYSIS.md)
- [Kanban Explanation](documentation/KANBAN-EXPLANATION.md)
- [Object State Modeling](documentation/STATE-DIAGRAMS.md)
- [Activity Workflow Modeling](documentation/ACTIVITY-DIAGRAMS.md)
- [Traceability Mapping](documentation/TRACEABILITY.md)
- [Domain Model](documentation/DOMAIN-MODEL.md)
- [Class Diagram](documentation/CLASS-DIAGRAM.md)
- [Repository Class Diagram](documentation/REPOSITORY-CLASS-DIAGRAM.md)
- [Branch Protection Rules](documentation/PROTECTION.md)
- [Roadmap](documentation/ROADMAP.md)

## Technology Stack (Planned)

The system will be developed using the following technologies:

**Core Application**

- Backend / API: Java Spring Boot
- Database: PostgreSQL
- Frontend: Vue.js or React (Web-based interface)

**DevOps and Deployment (Optional Extension)**

- Containerization: Docker
- Infrastructure Provisioning: Terraform
- Cloud Hosting: AWS
- CI/CD: GitHub Actions

**Architecture Modeling**

- C4 Model using Mermaid diagrams

## Project Status

## From Class Diagrams to Code

For Class Diagrams to Code with All Creational Patterns, Java 21 and Spring Boot with Maven were selected because they align with the planned backend and API implementation approach for this system. The implementation work translates the Domain Modeling and Class Diagram Development Mermaid class diagram into concrete Java classes in the project domain model. In addition, all required creational design patterns were implemented to demonstrate multiple object-creation strategies in a domain-relevant way. Unit tests were added to validate these pattern implementations and support maintainable evolution of the codebase.

### Source Code Structure

```text
src/main/java/com/carwash/
├── domain/
├── enums/
└── creational_patterns/
    ├── simple_factory/
    ├── factory_method/
    ├── abstract_factory/
    ├── builder/
    ├── prototype/
    └── singleton/
```

### Creational Pattern Rationale

| Pattern | Implementation | Purpose |
|--------|----------------|---------|
| Simple Factory | VehicleFactory | Centralizes creation of vehicle-type objects |
| Factory Method | Notification sender factories | Delegates notification sender creation to concrete factories |
| Abstract Factory | Dashboard component factories | Creates related role-based dashboard component families |
| Builder | BookingBuilder | Builds booking objects step-by-step with validation |
| Prototype | ServicePrototypeRegistry | Clones reusable service templates |
| Singleton | ApplicationConfig | Provides one shared application configuration instance |

Although not every creational pattern would be required in a small MVP, all six patterns were implemented because Class Diagrams to Code with All Creational Patterns requires them. Each pattern was adapted to the car wash booking and queue management domain where possible.

### Testing and Coverage

Run unit tests:

```text
mvn clean test
```

Generate unit test coverage report:

```text
mvn clean test jacoco:report
```

Coverage report output:

```text
target/site/jacoco/index.html
```

![alt text](assets/JaCoCo-Object-Creation-Test-Coverage.png)

### GitHub Project Update Note

Class Diagrams to Code with All Creational Patterns implementation and testing tasks were tracked using GitHub Issues. Related issues were moved through the GitHub Project Kanban workflow as work progressed. Completed implementation and testing items should be moved to **Done** where applicable, and any defects or improvements identified during testing should be captured as new issues for follow-up.

In-Memory Repository Layer

HashMap-based in-memory repository implementations were added for the repository interfaces in `com.carwash.repository`.

### What was implemented

- A reusable generic `InMemoryRepository<T, ID>` base class for shared CRUD behavior.
- Entity-specific in-memory repositories for `User`, `Role`, `Vehicle`, `Service`, `Booking`, `QueueEntry`, and `Notification`.
- CRUD support (`save`, `findById`, `findAll`, `delete`) with `Optional` for `findById` and defensive `List` copy return for `findAll`.
- Interface-specific finder methods implemented in-memory where domain relationships are available.

### Why this helps

- Supports fast development and unit testing without database dependencies.
- Keeps persistence concerns behind repository interfaces.
- Allows easy replacement with filesystem/database/API persistence implementations later without changing service/domain consumers.

### Inspecting in-memory HashMap state

You can inspect how the in-memory `HashMap` is being used during runtime:

```text
InMemoryUserRepository repo = new InMemoryUserRepository();
repo.save(user);

// immutable snapshot (safe for debugging)
Map<String, User> snapshot = repo.storageSnapshot();

// string rendering of current map entries
String view = repo.storageAsString();

// direct console print
repo.printStorage();
```

This is useful for debugging CRUD flows without any database connection.

### Repository Storage Abstraction

For this section, the **Factory Pattern** was selected to implement storage abstraction because it clearly demonstrates how repository implementations can be switched between storage backends.

Application code now depends on repository interfaces (for example, `UserRepository`) rather than concrete `HashMap`-based classes. The factory controls which implementation is returned.

Current supported backend:
- `MEMORY`

Planned future backend options:
- `DATABASE`
- `FILESYSTEM`
- `API`

Any unsupported storage type currently throws `UnsupportedOperationException`, making unsupported options explicit while keeping the interface stable.

This approach improves separation of concerns, testability, and future scalability. Dependency Injection can still be introduced later when integrating with Spring services, but the Factory Pattern was chosen here because it is simple, explicit, and aligns with the assignment scope.

Example usage:

```java
UserRepository userRepository =
        RepositoryFactory.getUserRepository(StorageType.MEMORY);
```

### Repository Future-Proofing

The repository layer is designed so that storage backends can be swapped without changing business logic. Application code depends on repository interfaces, and each backend provides concrete implementations of those same contracts.

- In-memory repositories are currently implemented for fast testing and simple persistence.
- A database-backed repository stub (`DatabaseUserRepository`) was added to demonstrate how future persistence can be introduced.
- The factory abstraction already includes future storage types such as `DATABASE`, `FILESYSTEM`, and `API`.
- Future implementations can replace in-memory repositories by implementing the same repository interfaces.
- The updated repository class diagram documents this extension point.

[Repository Class Diagram](documentation/REPOSITORY-CLASS-DIAGRAM.md)

## Service Layer

A service layer has been introduced between future controllers and the repository layer.

- Services now host business rules and validation logic.
- Repositories remain responsible for persistence operations.
- Current MVP service coverage includes: User, Vehicle, Service, Booking, and QueueEntry.
- Role remains a supporting access-control concept and is not implemented as a separate service in this task.
-

## REST API

REST controllers were added for User, Vehicle, Service, Booking, and QueueEntry. Controllers delegate to service-layer classes (no business logic in controllers) and currently run on in-memory repositories.

| Entity | Base Endpoint | Supported Operations |
|--------|---------------|----------------------|
| User | `/api/users` | CRUD |
| Vehicle | `/api/vehicles` | CRUD |
| Service | `/api/services` | CRUD, activate/deactivate |
| Booking | `/api/bookings` | CRUD, confirm/cancel |
| QueueEntry | `/api/queue-entries` | CRUD, call/start/complete |

### API Documentation

Swagger UI is available after running the Spring Boot application:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

See: [API Documentation](documentation/API-DOCUMENTATION.md)

Swagger UI screenshots: [Swagger UI Screenshots](documentation/screenshots/)
- [Services](documentation/screenshots/swagger-ui-services.png)
- [Users and Queue Entries](documentation/screenshots/swagger-ui-users-and-queue-entries.png)
- [Bookings and Vehicles](documentation/screenshots/swagger-ui-bookings-and-vehicles.png)
- [Users and Queue Entries](documentation/screenshots/swagger-ui-schemas.png)
- [Bookings and Vehicles](documentation/screenshots/v3-api-docs.png)

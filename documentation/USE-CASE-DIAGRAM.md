# Use Case Diagram

## 1. Use Case Diagram

The following UML-style use case diagram models the MVP interactions for the **Web-Based Car Wash Booking and Queue Management System**, aligned with the functional requirements from the product requirements (FR-01 to FR-07).

![alt text](../assets/Use-Case-Diagram.png)

## 2. Explanation

### 2.1 Key actors and roles

- **Customer** uses the platform to register, sign in, browse available services, create bookings, join the queue, and monitor queue status.
- **Business Owner** controls service offerings and operations through booking/queue management and reporting views.
- **Service Staff** executes day-to-day queue work by updating service progress and booking status.
- **System Administrator** ensures controlled access and platform governance through user/role administration and dashboard visibility.
- **IT Support** maintains technical continuity by monitoring system health and handling platform configuration.
- **Platform Owner** uses reporting and health indicators to evaluate adoption and overall product performance.

### 2.2 Relationships between actors and use cases

The diagram maps all major stakeholders identified in the product requirements to concrete interactions in the MVP:

- **Customer-facing flow** centers on FR-01, FR-02, FR-03, FR-04, and FR-05.
- **Operations flow** for Business Owner and Service Staff centers on FR-02, FR-04, and FR-06.
- **Governance and oversight flow** for System Administrator, IT Support, and Platform Owner supports FR-06 and FR-07 while reinforcing NFR reliability and maintainability concerns.

### 2.3 `<<include>>` and `<<extend>>` modeling choices

- `Create Booking` **includes** `Authenticate User` and `Browse Service Catalog`, because these are mandatory sub-behaviors for a valid booking flow.
- `Join Virtual Queue` **includes** `Authenticate User` and `View Queue Position`, representing required queue context for users.
- `Manage Bookings and Queue` **includes** `Update Service Status`, because status progression is part of queue execution in operations.
- `View Admin Dashboard` **includes** `Generate Basic Reports`, since dashboard visibility depends on report aggregates.
- `Receive Booking Notifications` **extends** both `Create Booking` and `Join Virtual Queue`, because notification behavior is conditional and event-driven.
- `Generate Basic Reports` **extends** `View Admin Dashboard` to represent deeper analytics access that is initiated from dashboard activity.

### 2.4 Alignment with Product Scope and Requirements

This use case model remains consistent with the current architecture and MVP scope by keeping to MVP functions (booking, queue, service management, and basic reporting) without introducing out-of-scope capabilities such as payments or loyalty programs.

It also directly reflects the product requirements stakeholder concerns by:

- reducing customer uncertainty through booking, queue visibility, and notifications,
- improving business operational control through service and queue workflows,
- supporting service staff task clarity through status updates,
- and enabling administrative and technical oversight for reliability, maintainability, and platform monitoring.

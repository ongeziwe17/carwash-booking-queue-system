# Domain Model

## 1. Overview

The domain model defines the core business entities, behaviors, and relationships for the MVP of the Web-Based Car Wash Booking and Queue Management System. It supports object-oriented design by identifying the main classes, their responsibilities, and the rules that govern interactions across booking, queue, and notification workflows.

This model aligns with:

- **Functional requirements** (authentication, service catalog, booking, queue, notifications, admin support)
- **Use Cases** (register, authenticate, browse services, create booking, join/view queue, manage operations)
- **Behavioral Models** (state transitions and workflow activities for booking and queue operations)

## 2. Domain Entities

| Entity | Attributes | Methods / Responsibilities | Relationships | Business Rules / Notes |
|---|---|---|---|---|
| **User** | `userId`, `fullName`, `email`, `phone`, `passwordHash`, `accountStatus`, `createdAt`, `lastLoginAt` | Register account, authenticate, manage profile, create/cancel bookings, view queue position, receive notifications | Many-to-one with **Role**; one-to-many with **Vehicle**, **Booking**, **Notification** | Email must be unique. Only active users can create bookings or join queue. |
| **Role** | `roleId`, `roleName` (Customer/Admin/Staff), `description`, `permissions` | Define access level, authorize actions in dashboard and operations | One-to-many with **User** | Role controls visibility and permissions for FR-06 administrative functions. |
| **Vehicle** | `vehicleId`, `ownerUserId`, `plateNumber`, `vehicleType`, `brand`, `model`, `color`, `notes` | Store customer vehicle profile for booking reuse and service eligibility checks | Many-to-one with **User**; one-to-many with **Booking** | Plate number should be unique per owner. Vehicle must belong to booking user. |
| **Service** | `serviceId`, `serviceName`, `description`, `price`, `estimatedDurationMin`, `isActive`, `createdAt` | Expose service catalog, support browsing and admin management, provide booking duration basis | One-to-many with **Booking** and **QueueEntry** | Inactive services cannot be selected for new bookings. Estimated duration drives queue ETA. |
| **Booking** | `bookingId`, `userId`, `vehicleId`, `serviceId`, `scheduledDateTime`, `status`, `createdAt`, `specialRequest` | Create booking, validate slot, update/cancel booking, trigger queue entry and notification events | Many-to-one with **User**, **Vehicle**, **Service**; one-to-one or one-to-many lifecycle link with **QueueEntry**; one-to-many with **Notification** (event-driven) | Booking status transitions should follow the product behavior models state flow (e.g., Pending → Confirmed → InService → Completed / Cancelled). |
| **QueueEntry** | `queueEntryId`, `bookingId`, `serviceId`, `position`, `queueStatus`, `joinedAt`, `calledAt`, `startedAt`, `completedAt`, `estimatedWaitMin` | Join virtual queue, update queue order, track service progress, expose live queue position | Many-to-one with **Booking** and **Service** | Active queue positions must be unique and sequential. Queue status transitions map to the product behavior models activity/state models. |
| **Notification** | `notificationId`, `userId`, `bookingId`, `type`, `message`, `channel`, `sentAt`, `readAt`, `deliveryStatus` | Send booking/queue updates, remind users of schedule changes, record delivery/read states | Many-to-one with **User**; optional many-to-one with **Booking** | Notifications should be generated for key events: booking confirmation/cancellation, queue call, and completion updates. |

## 3. Design Notes

These seven entities were selected because they represent the minimum cohesive object model needed to deliver the MVP scope without introducing out-of-scope enterprise concepts:

- **Identity and access** are handled by `User` + `Role`.
- **Operational context** is captured by `Vehicle` + `Service`.
- **Core transactional flow** is implemented through `Booking` + `QueueEntry`.
- **Communication flow** is handled by `Notification`.

Together, these entities cover the end-to-end lifecycle from account creation and service discovery to booking execution, queue tracking, and customer updates, directly reflecting the project’s prior requirements and behavioral artifacts.

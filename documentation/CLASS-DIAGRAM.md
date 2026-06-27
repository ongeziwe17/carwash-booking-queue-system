# Class Diagram

## 1. Overview

This class diagram translates the current domain model into an object-oriented design view for implementation. It defines the core classes, their attributes and responsibilities, and the relationships that support user management, booking, queue flow, and notifications in the MVP.

## 2. Mermaid Class Diagram

```mermaid
classDiagram
    class User {
      +UUID userId
      +String fullName
      +String email
      +String phone
      +String passwordHash
      +AccountStatus accountStatus
      +DateTime createdAt
      +DateTime lastLoginAt
      +registerAccount()
      +authenticate(credentials)
      +updateProfile(profileData)
      +createBooking(vehicleId, serviceId, schedule)
      +cancelBooking(bookingId)
      +viewQueuePosition(queueEntryId)
      +markNotificationAsRead(notificationId)
    }

    class Role {
      +UUID roleId
      +String roleName
      +String description
      +Set~String~ permissions
      +assignPermission(permission)
      +revokePermission(permission)
      +hasPermission(permission) bool
    }

    class Vehicle {
      +UUID vehicleId
      +UUID ownerUserId
      +String plateNumber
      +String vehicleType
      +String brand
      +String model
      +String color
      +String notes
      +validateOwnership(userId) bool
      +updateVehicleDetails(data)
    }

    class Service {
      +UUID serviceId
      +String serviceName
      +String description
      +Decimal price
      +int estimatedDurationMin
      +bool isActive
      +DateTime createdAt
      +activate()
      +deactivate()
      +updateDetails(data)
      +calculateEstimatedEndTime(startTime) DateTime
    }

    class Booking {
      +UUID bookingId
      +UUID userId
      +UUID vehicleId
      +UUID serviceId
      +DateTime scheduledDateTime
      +BookingStatus status
      +DateTime createdAt
      +String specialRequest
      +create()
      +confirm()
      +cancel(reason)
      +startService()
      +completeService()
      +validateStatusTransition(nextStatus) bool
    }

    class QueueEntry {
      +UUID queueEntryId
      +UUID bookingId
      +UUID serviceId
      +int position
      +QueueStatus queueStatus
      +DateTime joinedAt
      +DateTime calledAt
      +DateTime startedAt
      +DateTime completedAt
      +int estimatedWaitMin
      +joinQueue()
      +updatePosition(newPosition)
      +callNext()
      +startService()
      +complete()
      +recalculateEstimatedWait()
    }

    class Notification {
      +UUID notificationId
      +UUID userId
      +UUID bookingId
      +String type
      +String message
      +String channel
      +DateTime sentAt
      +DateTime readAt
      +DeliveryStatus deliveryStatus
      +send()
      +markAsRead()
      +retryDelivery()
      +formatMessage(context)
    }

    Role "1" <-- "0..*" User : assignedRole
    User "1" o-- "0..*" Vehicle : owns
    User "1" -- "0..*" Booking : creates
    Vehicle "1" -- "0..*" Booking : usedFor
    Service "1" -- "0..*" Booking : selectedIn
    Booking "1" *-- "0..1" QueueEntry : queueLifecycle
    Service "1" -- "0..*" QueueEntry : queueForService
    User "1" -- "0..*" Notification : receives
    Booking "1" -- "0..*" Notification : triggers

    note for Booking "Status transitions follow state diagrams 
    (Pending -> Confirmed -> InService -> Completed/Cancelled)."

    note for QueueEntry "Queue position must be unique
    within active entries for a service queue."
```

## 3. Key Design Decisions

- **Class selection:** The model keeps exactly the seven MVP classes already established in the domain model (`User`, `Role`, `Vehicle`, `Service`, `Booking`, `QueueEntry`, `Notification`) to maintain consistency and avoid scope creep.
- **Relationship choices:**
  - `User` to `Role` is an association (many users can share one role).
  - `User` to `Vehicle` uses **aggregation** because a user owns vehicles, but vehicle records can still be managed independently.
  - `Booking` to `QueueEntry` uses **composition** because a queue entry exists only as part of a booking’s queue lifecycle.
  - Remaining links are associations for operational interactions.
- **Multiplicity decisions:** Multiplicities reflect expected MVP behavior (e.g., one user can create many bookings, one booking can have zero or one queue entry, one service can appear in many bookings/queue entries).
- **Inheritance choice:** No inheritance was used for Customer/Admin/Staff; role-based access is modeled via `User` + `Role` as recommended, keeping the model simpler and more maintainable.

## 4. Alignment with Prior Work

- **Functional Requirements:** Supports FR-01 to FR-07 through authentication (`User`/`Role`), service catalog (`Service`), booking and queue operations (`Booking`/`QueueEntry`), notifications (`Notification`), and admin operations through role permissions.
- **Use Cases:** Directly maps to register/authenticate, browse services, create booking, join/view queue, and manage bookings/services.
- **Behavioral Models:** Booking and queue status methods/notes align with previously defined state and activity workflows.

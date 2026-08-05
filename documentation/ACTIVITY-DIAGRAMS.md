# Activity Workflow Modeling

## 1. Overview

Activity diagrams model how work moves through the system from start to finish, including who performs each action, where decisions happen, and where tasks can run in parallel. In this project, each diagram uses GitHub-friendly Mermaid flowcharts with role-based swimlanes (via `subgraph`) to represent responsibilities across customers, the platform, and operational actors.

## 2. Register Account Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Customer
      C1[Open registration page]
      C2[Enter profile and account details]
      C3[Submit registration form]
      C4[Review validation errors and correct input]
      C5[Open verification email/SMS]
      C6[Confirm account]
      C7[Proceed to login]
    end

    subgraph System
      SY1[Display registration form]
      SY2[Validate required fields and format]
      D1{Input valid?}
      SY3[Create pending account]
      SY4[Send verification token]
      D2{Verification confirmed before expiry?}
      SY5[Activate account]
      SY6[Show success message]
      SY7[Show error messages]
      SY8[Invalidate token and request resend]
    end

    S --> C1 --> SY1 --> C2 --> C3 --> SY2 --> D1
    D1 -- No --> SY7 --> C4 --> C3
    D1 -- Yes --> SY3 --> SY4 --> C5 --> C6 --> D2
    D2 -- No --> SY8 --> C5
    D2 -- Yes --> SY5 --> SY6 --> C7 --> E
```

### Explanation

- Key flow: the customer submits registration data, the system validates it, creates a pending account, and activates it after verification.
- Major decisions: input validation and token confirmation-before-expiry control whether the process loops or succeeds.
- Parallel concern: account creation and verification token dispatch are system-side sequential actions that enable asynchronous customer completion.
- Stakeholder concerns: customer usability (clear error feedback) and business security (verified accounts only).
- Traceability: aligns with **FR-01 User Registration and Authentication** and the product use cases **Register Account** use case; supports the product backlog stories/delivery cycle work on onboarding and account setup.

## 3. Authenticate User Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Customer
      C1[Open login page]
      C2[Enter email/username and password]
      C3[Submit credentials]
      C4[Re-enter credentials]
      C5[Enter MFA code]
      C6[Access dashboard]
    end

    subgraph System
      SY1[Display login form]
      SY2[Validate credential format]
      D1{Format valid?}
      SY3[Check credentials in identity store]
      D2{Credentials correct?}
      D3{MFA enabled?}
      SY4[Generate MFA challenge]
      SY5[Verify MFA code]
      D4{MFA valid?}
      SY6[Create session and auth token]
      SY7[Log login event]
      SY8[Increment failed attempt counter]
      SY9[Show authentication error]
      SY10[Lock account temporarily]
      D5{Failed attempts threshold reached?}
    end

    S --> C1 --> SY1 --> C2 --> C3 --> SY2 --> D1
    D1 -- No --> SY9 --> C4 --> C3
    D1 -- Yes --> SY3 --> D2
    D2 -- No --> SY8 --> D5
    D5 -- Yes --> SY10 --> E
    D5 -- No --> SY9 --> C4 --> C3
    D2 -- Yes --> D3
    D3 -- Yes --> SY4 --> C5 --> SY5 --> D4
    D4 -- No --> SY9 --> C5
    D4 -- Yes --> SY6 --> SY7 --> C6 --> E
    D3 -- No --> SY6 --> SY7 --> C6 --> E
```

### Explanation

- Key flow: customer credentials are validated, optionally followed by MFA, before a session is created.
- Major decisions: credential validity, failed-attempt threshold, and MFA verification branch the path.
- Parallel action intent: successful login triggers both session creation and audit logging as independent system outcomes.
- Stakeholder concerns: security (lockout and MFA), traceability (login event logging), and customer access continuity.
- Traceability: maps directly to **FR-01** and the product use cases **Authenticate User**; reinforces the product backlog delivery cycle scope around secure authentication.

## 4. Browse Service Catalog Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Customer
      C1[Open service catalog]
      C2[Apply filters/search]
      C3[Select service for details]
      C4[Review service details and estimated duration]
      C5[Proceed to booking flow]
      C6[Exit catalog]
    end

    subgraph System
      SY1[Load active services]
      SY2[Return catalog list]
      SY3[Filter and sort results]
      D1{Matching services found?}
      SY4[Display matching services]
      SY5[Display no-results message and suggestions]
      SY6[Fetch selected service detail]
      SY7[Show price, inclusions, duration, availability hints]
    end

    S --> C1 --> SY1 --> SY2 --> C2 --> SY3 --> D1
    D1 -- No --> SY5 --> C2
    D1 -- Yes --> SY4 --> C3 --> SY6 --> SY7 --> C4
    C4 -->|Continue browsing| C2
    C4 -->|Book now| C5 --> E
    C4 -->|Leave| C6 --> E
```

### Explanation

- Key flow: users discover services by browsing, filtering, and viewing details before deciding next action.
- Major decisions: whether filters return matching services determines continue/refine behavior.
- Parallel concern: catalog responses can include computed availability hints while showing static service metadata.
- Stakeholder concerns: customer transparency (pricing and duration) and business merchandising (service discoverability).
- Traceability: supports **FR-02 Service Catalog** and the product use cases **Browse Service Catalog**; aligns with the product backlog stories for service visibility in MVP.

## 5. Create Booking Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Customer
      C1[Choose service from catalog]
      C2[Select vehicle and preferred slot]
      C3[Submit booking request]
      C4[Adjust selection]
      C5[Confirm booking]
      C6[Receive confirmation]
    end

    subgraph System
      SY1[Validate service and vehicle selection]
      SY2[Check slot availability]
      D1{Slot available?}
      SY3[Suggest next available slots]
      SY4[Create provisional booking]
      SY5[Calculate estimated wait/service window]
      SY6[Persist booking record]
      SY7[Send booking notification]
      SY8[Display booking confirmation]
      SY9[Release provisional lock]
    end

    S --> C1 --> C2 --> C3 --> SY1 --> SY2 --> D1
    D1 -- No --> SY3 --> C4 --> C2
    D1 -- Yes --> SY4 --> C5 --> SY5 --> SY6
    SY6 --> SY7
    SY6 --> SY8
    SY7 --> C6 --> E
    SY8 --> C6
    C4 --> SY9
```

### Explanation

- Key flow: customer selects service/slot, system verifies availability, then booking is confirmed and communicated.
- Major decision: slot availability determines retry loop or progression to confirmation.
- Parallel actions: after booking persistence, system can send notifications and render confirmation concurrently.
- Stakeholder concerns: avoiding double-booking, fast customer feedback, and reliable booking records.
- Traceability: corresponds to **FR-03 Booking System** and the product use cases **Create Booking**; aligns with the product backlog delivery cycle/backlog focus on booking capability.

## 6. Join Virtual Queue Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Customer
      C1[Open queue entry from booking or walk-in flow]
      C2[Confirm queue join request]
      C3[Review queue token and ETA]
    end

    subgraph System
      SY1[Validate active booking/service eligibility]
      D1{Eligible to join queue?}
      SY2[Check queue capacity]
      D2{Queue open/capacity available?}
      SY3[Create queue ticket number]
      SY4[Compute current position and ETA]
      SY5[Trigger join confirmation notification]
      SY6[Show rejection reason]
    end

    subgraph Service_Staff
      ST1[View updated queue dashboard]
    end

    S --> C1 --> C2 --> SY1 --> D1
    D1 -- No --> SY6 --> E
    D1 -- Yes --> SY2 --> D2
    D2 -- No --> SY6 --> E
    D2 -- Yes --> SY3 --> SY4
    SY4 --> C3 --> E
    SY3 --> ST1
    SY3 --> SY5
```

### Explanation

- Key flow: an eligible customer joins the queue and immediately receives token/ETA feedback.
- Major decisions: eligibility checks and queue capacity control whether entry is accepted.
- Parallel actions: queue creation updates staff dashboard while sending customer confirmation notifications.
- Stakeholder concerns: queue fairness, capacity control, and real-time operational visibility for staff.
- Traceability: implements **FR-04 Queue Management** and the product use cases **Join Virtual Queue**; supports the product backlog queue-related stories.

## 7. View Queue Position Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Customer
      C1[Open queue status page]
      C2[Request refresh / auto-refresh active]
      C3[View position, ETA, and status]
      C4[Acknowledge ready notification]
      C5[Proceed to service bay/check-in]
    end

    subgraph System
      SY1[Fetch active queue ticket]
      D1{Ticket still active?}
      SY2[Calculate latest position and ETA]
      SY3[Display live status]
      D2{Customer is next/ready?}
      SY4[Send ready notification]
      SY5[Show completed/expired message]
    end

    S --> C1 --> C2 --> SY1 --> D1
    D1 -- No --> SY5 --> E
    D1 -- Yes --> SY2 --> SY3 --> C3 --> D2
    D2 -- No --> C2
    D2 -- Yes --> SY4 --> C4 --> C5 --> E
```

### Explanation

- Key flow: customer repeatedly checks queue status until called for service.
- Major decisions: ticket-active validation and readiness detection determine loop continuation or completion.
- Parallel concern: periodic ETA recalculation can occur alongside client refresh behavior.
- Stakeholder concerns: customer predictability and reduced physical waiting uncertainty.
- Traceability: linked to **FR-04 Queue Management** and the product use cases **View Queue Position**; fits the product backlog user stories around queue transparency.

## 8. Manage Services Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Business_Owner_or_Admin
      A1[Open service management panel]
      A2[Create/update/disable service]
      A3[Submit service changes]
      A4[Resolve validation/business rule issues]
      A5[Review published result]
    end

    subgraph System
      SY1[Load existing service catalog configuration]
      SY2[Validate fields: name, price, duration, status]
      D1{Valid and authorized?}
      SY3[Persist service configuration changes]
      SY4[Publish updated catalog]
      SY5[Write admin audit log]
      SY6[Return validation or authorization errors]
    end

    S --> A1 --> SY1 --> A2 --> A3 --> SY2 --> D1
    D1 -- No --> SY6 --> A4 --> A3
    D1 -- Yes --> SY3 --> SY4 --> A5 --> E
    SY3 --> SY5
```

### Explanation

- Key flow: authorized administrative actors maintain service definitions used by booking and catalog flows.
- Major decisions: authorization and data validation decide save or correction loop.
- Parallel actions: after persistence, catalog publication and audit logging can run independently.
- Stakeholder concerns: operational control, pricing accuracy, and governance through audit trails.
- Traceability: maps to **FR-02 Service Catalog** and **FR-06 Administrative Dashboard**, with direct relation to the product use cases **Manage Services** use case.

## 9. Manage Bookings and Queue Activity Diagram

```mermaid
flowchart LR
    S([Start])
    E([End])

    subgraph Service_Staff_or_Admin
      A1[Open booking and queue operations dashboard]
      A2[Select booking/queue item]
      A3[Choose action: confirm, reschedule, cancel, start, complete]
      A4[Provide reason/notes if required]
      A5[Monitor updated operational status]
    end

    subgraph System
      SY1[Load live bookings and queue state]
      SY2[Validate role permissions and item state]
      D1{Action permitted for current state?}
      SY3[Apply state transition]
      D2{Transition affects queue order/ETA?}
      SY4[Recalculate queue positions and ETAs]
      SY5[Update booking and queue records]
      SY6[Send impacted customer notifications]
      SY7[Log operational event for reporting]
      SY8[Return action error]
    end

    S --> A1 --> SY1 --> A2 --> A3 --> A4 --> SY2 --> D1
    D1 -- No --> SY8 --> A2
    D1 -- Yes --> SY3 --> D2
    D2 -- Yes --> SY4 --> SY5
    D2 -- No --> SY5
    SY5 --> SY6
    SY5 --> SY7
    SY5 --> A5 --> E
```

### Explanation

- Key flow: service staff/administrators manage booking and queue lifecycles from a central operations dashboard.
- Major decisions: transition legality and whether recalculation is needed before updates are finalized.
- Parallel actions: after record updates, notifications and event logging proceed concurrently.
- Stakeholder concerns: operational continuity, customer communication, and auditable management actions.
- Traceability: aligns with **FR-04 Queue Management**, **FR-06 Administrative Dashboard**, and supports **FR-07 Basic Reporting** through event logging; maps to the product use cases **Manage Bookings and Queue**.

# Use Case Specifications

This section defines eight critical use case specifications for the **Car Wash Booking and Queue Management Platform**. The specifications are aligned with the MVP scope and remain consistent with the previously documented architecture, stakeholders, and functional requirements.

---

## Use Case: Register Account

**Actor:**
Customer

**Description:**
This use case allows a new customer to create an account on the platform using personal and login details. It establishes a valid identity record that can later be used for secure access to booking and queue services.

**Preconditions:**

- The customer is not currently authenticated.
- The customer has access to the registration page.
- The customer has not yet registered with the same email address.

**Postconditions:**

- A new customer account is created and stored.
- The account is marked active (or pending verification, based on policy).
- The customer can proceed to authentication.

**Basic Flow:**

1. The customer selects **Create Account** from the landing page.
2. The system displays the registration form requesting full name, email, contact number, and password.
3. The customer enters all required fields and submits the form.
4. The system validates mandatory fields, email format, and password policy.
5. The system checks whether the email address already exists.
6. The system creates the account record and encrypts the password.
7. The system confirms successful registration and redirects the customer to the login page.

**Alternative Flows:**

- **A1: Invalid input**
  1. At Step 4, validation fails (e.g., weak password or invalid email format).
  2. The system highlights invalid fields and displays corrective guidance.
  3. The customer updates values and resubmits.
- **A2: Duplicate email detected**
  1. At Step 5, the email is already registered.
  2. The system rejects account creation and prompts the customer to log in or reset the password.
- **A3: User cancels action**
  1. Before Step 3, the customer selects **Cancel**.
  2. The system returns the customer to the landing page without creating an account.

---

## Use Case: Authenticate User

**Actor:**
Registered User (Customer, Business Owner, Service Staff, or Administrator)

**Description:**
This use case enables a registered user to securely log in and access authorized system functions based on a role. It enforces access control and protects operational data.

**Preconditions:**

- The user has an existing account.
- The user is on the login page.
- Authentication service is available.

**Postconditions:**

- The user is authenticated and an active session/token is issued.
- The user is redirected to the appropriate role-based dashboard.

**Basic Flow:**

1. The user opens the login page.
2. The system displays fields for email/username and password.
3. The user enters credentials and submits.
4. The system validates the input format and required fields.
5. The system verifies credentials against stored encrypted records.
6. The system identifies the user role and grants access permissions.
7. The system logs the login event and redirects to the relevant dashboard.

**Alternative Flows:**

- **A1: Invalid input**
  1. At Step 4, required fields are missing or malformed.
  2. The system displays validation messages and requests correction.
- **A2: Invalid credentials**
  1. At Step 5, credentials do not match any active account.
  2. The system denies login and displays an authentication failure message.
- **A3: User cancels action**
  1. Before Step 3, the user navigates away or closes the form.
  2. The system ends the attempt with no active session created.

---

## Use Case: Browse Service Catalog

**Actor:**
Customer

**Description:**
This use case allows a customer to view available car wash services, prices, and service descriptions before deciding to book or join a queue. It supports informed customer decision-making.

**Preconditions:**

- Service catalog data is available.
- The customer has access to the public or authenticated service listing view.

**Postconditions:**

- The customer has viewed current service options.
- A service may be selected for subsequent booking or queue actions.

**Basic Flow:**

1. The customer opens the **Service Catalog** page.
2. The system retrieves active services from the catalog database.
3. The system displays each service with a name, description, estimated duration, and price.
4. The customer filters or sorts services (e.g., by price or duration).
5. The system refreshes the list based on selected criteria.
6. The customer selects a service to view details.
7. The system displays the selected service details and provides **Book Now** / **Join Queue** options.

**Alternative Flows:**

- **A1: Data not available**
  1. At Step 2, no active services are returned.
  2. The system displays a "No services currently available" message.
- **A2: Invalid filter input**
  1. At Step 4, unsupported filter values are submitted.
  2. The system ignores invalid values and prompts the customer to choose valid filters.
- **A3: User cancels action**
  1. Before Step 6, the customer leaves the page.
  2. The system terminates browsing without a service selection.

---

## Use Case: Create Booking

**Actor:**
Customer

**Description:**
This use case allows an authenticated customer to reserve a car wash service slot for a selected date and time. It ensures booking records are valid, non-conflicting, and traceable.

**Preconditions:**

- The customer is authenticated.
- The customer has selected a valid service.
- Booking calendar and slot availability data are accessible.

**Postconditions:**

- A booking record is created with a unique booking reference.
- The selected time slot is reserved.
- A booking confirmation is shown and queued for notification delivery.

**Basic Flow:**

1. The customer selects **Book Now** from a chosen service.
2. The system displays available dates and time slots.
3. The customer selects preferred date, time, and vehicle details.
4. The system validates booking input and confirms that required fields are complete.
5. The system checks for slot availability and duplicate/conflicting bookings.
6. The customer confirms the booking request.
7. The system creates the booking record and reserves the slot.
8. The system displays confirmation details and booking reference number.

**Alternative Flows:**

- **A1: User not authenticated**
  1. At Step 1, no valid session is detected.
  2. The system redirects the customer to the login page.
- **A2: Slot unavailable**
  1. At Step 5, the selected slot is no longer available.
  2. The system prompts the customer to choose another slot.
- **A3: User cancels action**
  1. Before Step 6, the customer selects **Cancel Booking**.
  2. The system closes booking creation and releases provisional slot holds.

---

## Use Case: Join Virtual Queue

**Actor:**
Customer

**Description:**
This use case allows an authenticated customer to join a live service queue instead of scheduling a future booking slot. It supports walk-in style operations through controlled digital queue enrollment.

**Preconditions:**

- The customer is authenticated.
- A selected service supports queue-based processing.
- Queue capacity has not been reached.

**Postconditions:**

- The customer is assigned a queue ticket/position.
- Estimated waiting time is calculated and displayed.

**Basic Flow:**

1. The customer selects **Join Queue** for a chosen service.
2. The system verifies active authentication and customer profile completeness.
3. The system retrieves current queue status and capacity.
4. The system displays estimated waiting time and queue terms.
5. The customer confirms queue entry.
6. The system appends the customer to the queue and assigns a queue number.
7. The system displays queue position and estimated wait.
8. The system stores queue enrollment for notification updates.

**Alternative Flows:**

- **A1: User not authenticated**
  1. At Step 2, authentication check fails.
  2. The system redirects to login before queue entry can continue.
- **A2: Queue full / unavailable**
  1. At Step 3, capacity limit is reached or queue is temporarily closed.
  2. The system shows unavailability and suggests booking another time.
- **A3: User cancels action**
  1. Before Step 5, the customer chooses not to proceed.
  2. The system exits queue enrollment with no position assigned.

---

## Use Case: View Queue Position

**Actor:**
Customer

**Description:**
This use case enables a customer already in the queue to track their live queue position and estimated waiting time. It improves transparency and reduces uncertainty during service waiting periods.

**Preconditions:**

- The customer is authenticated.
- The customer has an active queue entry.
- Queue tracking service is operational.

**Postconditions:**

- The customer sees current queue position and updated estimated waiting time.
- The latest queue status is logged for audit/traceability.

**Basic Flow:**

1. The customer opens **My Queue Status**.
2. The system verifies the customer session.
3. The system retrieves the customer’s active queue ticket.
4. The system calculates current position and estimated waiting time based on active throughput.
5. The system displays queue number, current position, and progress indicators.
6. The customer refreshes status or enables auto-refresh.
7. The system updates displayed values in near real time.

**Alternative Flows:**

- **A1: No active queue entry**
  1. At Step 3, no active queue record is found.
  2. The system displays "No active queue" and provides **Join Queue** option.
- **A2: Tracking data unavailable**
  1. At Step 4, queue service data is temporarily unavailable.
  2. The system displays the most recent cached status with a warning message.
- **A3: User cancels action**
  1. After Step 5, the customer exits the status page.
  2. The system stops auto-refresh and returns to dashboard.

---

## Use Case: Manage Bookings and Queue

**Actor:**
Service Staff

**Description:**
This use case allows service staff to process daily operations by viewing bookings, managing queue order, and updating service progress. It supports controlled and efficient handling of customer flow.

**Preconditions:**

- Service staff user is authenticated and authorized.
- There are active or scheduled bookings/queue entries.
- Operational dashboard is available.

**Postconditions:**

- Booking and queue states are updated accurately.
- Operational changes are recorded in audit logs.
- Customers affected by changes are flagged for notifications.

**Basic Flow:**

1. Service staff logs in and opens the operations dashboard.
2. The system displays current bookings, queue list, and service statuses.
3. Staff selects the next customer or booking to process.
4. The system displays full job details and current status.
5. Staff updates status (e.g., In Progress, Completed, Delayed).
6. The system validates status transition rules.
7. The system saves updates, reorders queue if required, and refreshes the operational list.
8. The system triggers notification events for affected customers.

**Alternative Flows:**

- **A1: Invalid status transition**
  1. At Step 6, selected status violates workflow rules.
  2. The system rejects the update and displays permitted transitions.
- **A2: Data conflict**
  1. At Step 7, another staff member has already changed the same record.
  2. The system prompts refresh and requires reconfirmation.
- **A3: User cancels action**
  1. Before Step 5, staff exits update mode.
  2. The system keeps the previous state unchanged.

---

## Use Case: Manage Services

**Actor:**
Business Owner

**Description:**
This use case allows a business owner to create, update, or deactivate service offerings in the catalog. It ensures customers always see accurate service names, prices, and availability.

**Preconditions:**

- Business owner is authenticated and authorized.
- Service management module is accessible.
- Existing catalog data can be read and written.

**Postconditions:**

- Service catalog records are created/updated/deactivated.
- Updated service data becomes available to customer browsing and booking workflows.
- All changes are logged for administrative review.

**Basic Flow:**

1. The business owner opens **Service Management**.
2. The system displays current service list with edit controls.
3. The business owner selects **Add Service** or chooses an existing service to edit.
4. The system displays service fields (name, description, duration, price, availability).
5. The business owner enters or updates values and submits.
6. The system validates field completeness, business rules, and pricing format.
7. The system saves changes and updates the active catalog.
8. The system confirms success and displays the updated service list.

**Alternative Flows:**

- **A1: Invalid input**
  1. At Step 6, required fields are missing or invalid (e.g., negative price).
  2. The system rejects submission and highlights fields needing correction.
- **A2: Data not available**
  1. At Step 2, service records cannot be loaded due to temporary data access failure.
  2. The system shows an error notice and allows retry.
- **A3: User cancels action**
  1. Before Step 5, the owner selects **Cancel**.
  2. The system discards unsaved edits and returns to the service list.

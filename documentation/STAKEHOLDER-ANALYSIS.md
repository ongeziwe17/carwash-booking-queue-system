# Stakeholder Analysis

## Web-Based Car Wash Booking and Queue Management System

## 2. Stakeholder Analysis

| Stakeholder          | Role                                         | Key Concerns                                    | Pain Points                                                 | Success Metrics                                                |
|----------------------|----------------------------------------------|-------------------------------------------------|-------------------------------------------------------------|----------------------------------------------------------------|
| Customer             | End-user who books services and joins queues | Convenience, reduced waiting time, ease of use  | Long physical queues, no booking system, lack of visibility | 50% reduction in waiting time, booking completed in <3 minutes |
| Business Owner       | Manages operations and services              | Efficient booking management, increased revenue | Manual scheduling errors, poor tracking of bookings         | 30% increase in bookings, improved operational efficiency      |
| Service Staff        | Performs car wash services                   | Clear workflow, manageable workload             | Confusion in queue order, manual coordination               | Reduced task confusion, smooth workflow                        |
| System Administrator | Maintains system and security                | System reliability, data protection             | System downtime, security risks                             | 99% uptime, secure data handling                               |
| IT Support           | Handles technical issues and deployment      | System stability, maintainability               | Difficult deployments, lack of documentation                | Faster issue resolution, stable deployments                    |
| Platform Owner       | Oversees system success and usage            | User adoption, system usefulness                | Low engagement, lack of system insights                     | Increased system usage and user satisfaction                   |

## 2.1 Stakeholder-Driven Requirement Mapping (High-Level)

| Stakeholder          | High-Level Need                             | Pain Point                                   | System Capability                              |
|----------------------|---------------------------------------------|----------------------------------------------|------------------------------------------------|
| Customer             | Reduce waiting time and improve convenience | Long physical queues and no booking system   | Online booking and virtual queue system        |
| Customer             | Visibility into service process             | No visibility of queue position              | Real-time queue position tracking              |
| Business Owner       | Improve operational efficiency              | Manual booking and poor coordination         | Centralized booking and queue management       |
| Business Owner       | Better decision-making                      | Lack of operational insights                 | Basic reporting and booking insights           |
| Service Staff        | Clear workflow and task management          | Confusion in queue handling                  | Queue visibility and status updates            |
| System Administrator | Reliable and secure system                  | System downtime and security risks           | Authentication and access control              |
| IT Support           | Stable and maintainable system              | Difficult deployments and maintenance issues | Structured backend and deployable architecture |

---

## 2.2 Stakeholder-to-Requirement Mapping (Low-Level)

| Stakeholder          | Specific Need            | Requirement ID | Requirement Description              |
|----------------------|--------------------------|----------------|--------------------------------------|
| Customer             | Easy account access      | FR-01          | User registration and authentication |
| Customer             | Ability to book services | FR-03          | Booking system                       |
| Customer             | Know position in queue   | FR-04          | Queue management                     |
| Customer             | Receive booking updates  | FR-05          | Email notifications                  |
| Business Owner       | Manage services          | FR-02          | Service catalog management           |
| Business Owner       | Control bookings         | FR-06          | Administrative dashboard             |
| Business Owner       | View performance         | FR-07          | Basic reporting                      |
| Service Staff        | Know next task           | FR-04          | Queue visibility                     |
| Service Staff        | Track job progress       | FR-06          | Booking status updates               |
| System Administrator | Secure system            | NFR-SE-01      | Authentication and data protection   |
| System Administrator | System reliability       | NFR-RL-01      | System uptime and reliability        |
| IT Support           | System maintainability   | NFR-MT-01      | Modular and maintainable system      |
| IT Support           | Deployment capability    | NFR-DP-01      | Deployable system (local/cloud)      |

---

## 2.3 Scope Alignment Note

| Level                 | Description                                                                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| High-Level Scope      | Includes all identified stakeholders and potential enterprise-level capabilities such as analytics, loyalty systems, and fleet management |
| Low-Level (MVP Scope) | Focuses on core functionality: booking, queue management, basic administration, and notifications                                         |
| Future Expansion      | Advanced features (payments, loyalty programs, analytics, fleet management) can be added in later phases                                  |

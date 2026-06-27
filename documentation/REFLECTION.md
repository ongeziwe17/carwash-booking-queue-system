# Reflection

## Challenges in Requirements Engineering

Completing the stakeholder analysis and system requirements for the Web-Based Car Wash Booking and Queue Management System highlighted the complexity of balancing diverse stakeholder needs within a single system. One of the primary challenges encountered was aligning the priorities of the **Business Owner** and **Service Staff**. While the Business Owner focused on maximizing operational efficiency and increasing the number of daily bookings (FR-03, FR-07), the Service Staff required a manageable and clearly defined workflow to avoid confusion in queue handling (FR-04, FR-06). These needs are inherently interconnected but can conflict if not carefully designed. The solution was to ensure that the queue management system provides both operational visibility for the business and simplicity for staff execution.

Another significant challenge was balancing **usability** and **security requirements**. Customers require a fast and seamless booking experience (FR-01, FR-03), but the system must also enforce secure authentication and data protection (NFR-SE-01). Introducing authentication mechanisms such as login validation and encrypted password storage improves security but can add friction to the user experience. This required careful consideration to ensure that security controls do not negatively impact usability, while still meeting minimum protection standards.

A further challenge was ensuring **traceability between stakeholder needs and system requirements**. Initially, it was difficult to clearly map how each stakeholder concern translated into specific functional or non-functional requirements. For example, the Customer’s need for reduced waiting time was not only addressed by the booking system (FR-03) but also by queue visibility (FR-04) and system performance requirements (NFR-PF-01). This demonstrated that a single stakeholder concern can span multiple system components, reinforcing the importance of maintaining a clear mapping structure.

Additionally, defining the **scope of the system** required careful control. There was a tendency to introduce advanced features such as payment integration, loyalty systems, and analytics. However, these were intentionally excluded from the MVP to maintain feasibility within the constraints of a semester project. This reinforced the importance of distinguishing between core requirements and future enhancements.

Overall, this exercise demonstrated that requirements engineering is not only about defining system functionality, but also about managing trade-offs between competing stakeholder expectations. It highlighted the importance of clarity, traceability, and scope control in designing a system that is both practical to implement and aligned with stakeholder needs.

## Use Case Development

The process of gathering requirements and designing use cases for the Car Wash Booking and Queue Management System was both insightful and challenging. One of the key learning experiences was understanding how abstract stakeholder needs can be translated into structured system functionality. Initially, identifying and clearly defining user requirements proved difficult, particularly in distinguishing between what users explicitly request and what the system implicitly requires to function effectively. This required a shift from a surface-level understanding to a more analytical and system-oriented perspective.

A significant challenge encountered was structuring the use cases in a way that maintained logical consistency across all interactions. Defining the boundaries between basic flows and alternative flows was particularly demanding, as it required careful consideration of both normal and exceptional system behavior. Additionally, ensuring that each use case aligned with the overall system design without redundancy or ambiguity required iterative refinement.

Another difficulty was maintaining clarity while handling multiple actors and interactions within the system. It became evident that improper structuring could lead to overly complex or confusing representations, which would negatively impact both understanding and implementation.

Despite these challenges, the exercise significantly improved my ability to think in terms of real-world system operations and user interactions. It reinforced the importance of precision, clarity, and consistency in software design. Overall, the experience strengthened my understanding of requirements engineering and highlighted the critical role of use case modeling in bridging the gap between stakeholders and system implementation.

## Agile User Stories, Backlog, and Sprint Planning

### 1. Application of Agile Principles

The development process for this project follows Agile principles, particularly iterative development, incremental delivery, and continuous feedback. Instead of attempting to build the entire system at once, the approach focuses on delivering a functional vertical slice of the system during the sprint, beginning with core features such as authentication, service browsing, booking, and queue management.

This aligns with the Agile Manifesto’s emphasis on delivering working software frequently and prioritising customer value. By structuring the system around user stories and a prioritized backlog, development efforts remained focused on the most critical business functionalities required for a Minimum Viable Product (MVP).

## GitHub Project Templates and Kanban Board Implementation

Selecting and customizing a GitHub Project template for the Web-Based Car Wash Booking and Queue Management System was a useful exercise in understanding how project management tools influence workflow visibility and team coordination. One of the main challenges was deciding which project management structure best suited the needs of the system. Since the project had already been developed through requirements analysis, use cases, user stories, backlog planning, and sprint planning, the selected template needed to support both Agile workflow tracking and incremental MVP delivery.

A key challenge was choosing between GitHub’s available project approaches such as Kanban, Iterative Development, and Roadmaps. Kanban was ultimately selected because it provides the clearest visual workflow for active development tasks and integrates well with GitHub Issues. However, the decision was not simply about choosing the most familiar option. It required evaluating how each approach supported the actual needs of this project. For example, Iterative Development reflects how the project is being built incrementally, while Roadmaps are useful for higher-level planning and future expansion. Despite this, Kanban was the most suitable as the primary execution board because it offers immediate visibility into task progress.

Another challenge was customizing the board beyond the default workflow. A basic Kanban board is often limited to simple stages such as To Do, In Progress, and Done. For this project, that was not sufficient. Additional workflow stages such as Backlog, In Review, and Testing were added to better reflect the actual development lifecycle. This customization improved clarity, but it also required careful thought to avoid making the workflow unnecessarily complex. The goal was to strike a balance between realism and simplicity.

Comparing GitHub Projects to other tools such as Trello and Jira highlighted both strengths and limitations. Trello is simple and highly visual, which makes it useful for lightweight task tracking, but it lacks the deeper integration with repository work that GitHub provides. Jira offers much more advanced Agile functionality, including sprint metrics, reporting, and workflow automation, but it can be more complex and heavier to configure. GitHub Projects sits between these tools: it is more integrated than Trello and simpler than Jira, making it very suitable for a repository-centered academic or small-team project.

Overall, this exercise showed that selecting a project management template is not just about aesthetics or convenience. It is about choosing a structure that reflects the way work actually moves through the system lifecycle. Customizing the GitHub Kanban board made the workflow more realistic, improved traceability from issues to implementation, and strengthened the project’s alignment with Agile development practices.

---

### 2. Use of GitHub Projects and Issues

GitHub Issues and the Project Kanban board are used to simulate a real-world Agile workflow. Each user story is represented as an issue, allowing for clear traceability between requirements, development tasks, and sprint planning.

The Kanban board (Backlog, To Do, In progress, In review, Done) provides visibility into task progress and supported workflow management throughout the sprint. This helped in:

- tracking development progress
- identifying bottlenecks
- maintaining accountability for task completion

This approach reflects industry practices where tools such as Jira or Azure DevOps are used for Agile project management.

---

### 3. Sprint Planning Effectiveness

The sprint planning process ensures that only a manageable set of high-priority (Must-have) user stories are selected. By focusing on a small number of tightly related features, the sprint is structured to deliver a complete and usable customer journey rather than fragmented functionality.

Breaking down user stories into smaller, well-defined tasks improves estimation accuracy and ensured that work could be distributed effectively across the team. This reflects good Agile practice, where tasks are granular, actionable, and aligned with sprint goals.

---

### 4. Challenges Encountered

One of the key challenges in applying Agile within this context is the absence of a real development team and continuous stakeholder feedback. Agile methodologies typically rely heavily on collaboration, daily stand-ups, and iterative feedback, which are difficult to fully simulate in an individual setting.

Additionally, estimating effort without historical team velocity introduced some uncertainty in task sizing. This required making reasonable assumptions about development time and complexity.

---

### 5. Lessons Learned

This work reinforces several important software engineering principles:

- The importance of breaking down complex systems into manageable, iterative deliverables
- The value of prioritization (MoSCoW) in ensuring focus on high-impact features
- The effectiveness of vertical slicing in delivering usable functionality early
- The role of structured planning (backlogs, sprint plans) in reducing development risk

It also highlights that good system design is not only about architecture, but also about planning how the system is built over time.

---

### 6. Conclusion

Overall, the Agile approach used in this project proves to be effective for managing complexity and ensuring structured progress toward an MVP. While certain aspects of Agile (such as real-time collaboration and continuous stakeholder feedback) are limited in this context, the use of user stories, backlog prioritization, sprint planning, and task tracking provided a strong foundation for disciplined and incremental software development.

This approach closely mirrors real-world Agile practices and provides a solid basis for future team-based software engineering projects.

## Object State Modeling and Activity Workflow Modeling

Completing the object state modeling and activity workflow modeling for the **Web-Based Car Wash Booking and Queue Management System** provided a deeper understanding of the system’s dynamic behavior. While previous work focused on requirements, use cases, Agile planning, and project workflow, this required a more detailed view of how objects behave over time and how system processes unfold step by step.

One of the main challenges was choosing the right **granularity** for both states and workflow actions. If too much detail was added, the diagrams became difficult to read and started resembling implementation logic rather than analysis models. If too little detail was included, the diagrams became too abstract and did not fully explain the lifecycle or workflow of the system. This balancing act was especially noticeable in objects such as **Booking**, **Queue Entry**, and **Authentication Session**, where multiple valid intermediate states could exist. The challenge was to include enough detail to make the diagrams meaningful while still keeping them aligned to the MVP scope established in earlier.

Another challenge was ensuring that the diagrams remained consistent with the **Agile user stories and sprint planning** completed. Agile artefacts such as user stories usually describe user value and expected behavior in a concise way, while state and activity diagrams require a more structured and detailed representation of transitions, decisions, and actions. This meant that not every technical possibility could or should be modeled. Instead, the diagrams had to be grounded in the core functionality already prioritized for the MVP, such as registration, authentication, browsing services, creating bookings, and joining the virtual queue. This reinforced the importance of traceability across.

A useful insight from this exercise was the distinction between **state diagrams** and **activity diagrams**. State diagrams focus on the lifecycle of a specific object and how it changes from one condition to another over time. For example, a booking moves through states such as draft, confirmed, queued, completed, or cancelled. In contrast, activity diagrams focus on the process flow of a task or workflow, such as the steps a customer follows to create a booking or the sequence of actions staff perform to manage bookings and queues. In other words, state diagrams answer the question **“what states can this object be in?”**, while activity diagrams answer **“how does this process happen from start to finish?”**

This also highlighted the importance of consistency across different modeling techniques. A workflow cannot be realistic if it contradicts the object lifecycle, and an object lifecycle is less useful if it is not reflected in system workflows. As a result, both modeling approaches needed to be aligned with the same functional requirements, use cases, user stories, and backlog priorities.

Overall, this strengthened my ability to model dynamic system behavior in a structured and readable way. It showed that software design is not only about identifying features, but also about understanding how objects evolve, how workflows operate, and how these views must stay aligned with prior analysis and Agile planning. This is an important step toward implementation because it provides a clearer behavioral blueprint for the system.

## Domain Modeling and Class Diagram Development

Developing the domain model and class diagram for the **Web-Based Car Wash Booking and Queue Management System** was an important step in moving from behavioral understanding to structural design. Earlier focused on requirements, use cases, Agile planning, and dynamic system behavior. This work required a different kind of thinking: identifying the most important domain concepts, deciding how they should be represented as classes, and determining how those classes relate to one another in a way that supports both the MVP and future implementation.

One of the main challenges in designing the domain model was **abstraction**. The system includes many possible concepts that could be modeled, such as customers, business owners, staff, reports, payments, analytics, ratings, and scheduling policies. The difficulty was deciding which of these truly belonged in the **core domain model** for the MVP and which should remain outside the current scope. If too many entities were included, the model would become overly complex and would drift away from the system that had been defined in previous work. To avoid this, the model was intentionally centered on the most important entities needed for the MVP: `User`, `Role`, `Vehicle`, `Service`, `Booking`, `QueueEntry`, and `Notification`. This helped maintain consistency with the requirements and use cases while keeping the design manageable.

Another challenge was defining the **relationships** between entities. Some relationships were straightforward, such as a user owning multiple vehicles or a service being used in multiple bookings. Others required more careful thought. For example, the relationship between `Booking` and `QueueEntry` raised an important design question: should a queue entry be modeled as something fully contained within a booking, or as an associated object with its own lifecycle? I chose to model this relationship conservatively, recognizing that while a queue entry is strongly linked to a booking, it still has distinct behavior in the system’s queue workflow. This reflected a broader lesson that relationship choice in object-oriented design is not just about technical notation, but about accurately representing how the business domain behaves.

A further design trade-off involved **inheritance versus composition/association**. At first, it was tempting to model customer, administrator, and staff as separate subclasses of `User`. However, doing this would have added unnecessary complexity for the MVP and would have required additional specialization logic. Instead, I used a `User` + `Role` design, where roles govern permissions and operational access. This approach was simpler, easier to maintain, and more consistent with the system’s earlier role-based access control logic. This decision highlighted the importance of avoiding inheritance where a simpler association-based design is sufficient.

The class diagram also had to align with prior work. The chosen classes and relationships directly support **functional requirements**, especially authentication, service catalog management, booking, queue management, and notifications. They also align with **use cases**, since each major use case now maps to one or more classes and operations. In addition, the class structure had to remain compatible with **state and activity diagrams**, particularly for objects such as `Booking`, `QueueEntry`, and `Notification`, whose lifecycles and workflows had already been modeled dynamically. This showed how structural design must remain grounded in previously established behavior.

One of the most valuable lessons from this was that object-oriented design is not just about drawing classes and connecting them with lines. It is about making thoughtful design decisions that balance clarity, correctness, maintainability, and scope. The exercise improved my understanding of how domain entities emerge from requirements, how business rules influence relationships, and how UML class diagrams can serve as a bridge between analysis and implementation.

Overall, this strengthened my ability to think about software systems in terms of both **business concepts** and **design structure**. It reinforced the importance of abstraction, careful relationship modeling, and consistency with prior requirements and behavioral models. Most importantly, it showed that a well-designed class diagram is not only a documentation artifact, but also a practical guide for implementation.

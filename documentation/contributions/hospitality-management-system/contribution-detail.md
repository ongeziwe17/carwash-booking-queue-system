# Contribution Detail: Guest Service Requests PR

## Repository: https://github.com/Kamva-Ntlanga/hospitality-management-system

## Pull Request: https://github.com/Kamva-Ntlanga/hospitality-management-system/pull/57

## User Story

As a Hotel Guest, I want to request services like extra towels or room service so that I can get assistance without calling reception.

## Contribution Summary

This contribution adds a guest service request workflow that allows hotel guests to submit requests for housekeeping, room service, or maintenance support through the system API. The workflow supports special instructions, calculates an estimated completion time based on the selected service category, persists the request using the existing repository pattern, and creates an internal staff notification placeholder.

## Scope of Work

The PR focuses on the following areas:

- Service request domain model updates
- Service category validation
- Estimated completion time calculation
- Service request business logic
- In-memory persistence support
- Staff notification placeholder
- REST API endpoints for service request creation and retrieval
- OpenAPI documentation updates
- Unit and API test coverage for the new workflow

## Functional Requirements Addressed

| Requirement | Implementation |
|---|---|
| Guest can select a service category | Added support for `HOUSEKEEPING`, `ROOM_SERVICE`, and `MAINTENANCE`. |
| Guest can add special instructions | Added `specialInstructions` support in the API request and domain model. |
| Estimated completion time is shown | Added category-based estimated completion time values. |
| Staff receives notification | Added an internal in-memory notification placeholder for staff alerts. |
| Request can be persisted | Added in-memory service request repository support. |
| Tests pass | Added service-layer and API tests for the main workflow. |

## Main Changes

### Domain Model

Updated the service request entity to include:

- Guest identifier
- Room number
- Service category
- Special instructions
- Request status
- Estimated completion time
- Created and updated timestamps

### Service Layer

Added a service request business logic layer responsible for:

- Validating required fields
- Validating service category values
- Creating service request IDs
- Saving service requests through the repository
- Triggering staff notification placeholder behavior
- Returning created and persisted service request records

### Repository Layer

Added or connected repository support for service request persistence using the existing in-memory repository pattern already used elsewhere in the project.

### API Layer

Added REST API support for guest service requests:

- `POST /api/service-requests` creates a new guest service request.
- `GET /api/service-requests` lists service requests.
- `GET /api/service-requests/{request_id}` retrieves a single service request.

### Documentation

Updated OpenAPI documentation to include the new service request endpoints and supported request payload fields.

### Testing

Added tests covering:

- Successful service request creation
- Estimated completion times for supported categories
- Invalid category rejection
- Staff notification placeholder behavior
- API-level successful request creation
- API-level invalid category handling

## Acceptance Criteria Traceability

| Acceptance Criteria | Status | Notes |
|---|---|---|
| User can select service category | Complete | Categories are restricted to housekeeping, room service, and maintenance equivalents. |
| User can add special instructions | Complete | Instructions are accepted through the API and stored on the request. |
| Estimated completion time is shown | Complete | Response includes `estimatedCompletionMinutes`. |
| Staff receives notification | Complete with placeholder | Notification is represented through an in-memory staff notification service. |
| Request can be persisted | Complete | Request is saved through the service request repository. |
| Tests pass | Pending final environment verification | Non-API tests pass locally; full API tests require FastAPI dependencies. |

## Testing Notes

Recommended commands before merging:

```bash
python -m pytest tests/ -v
python -m pytest tests/test_services.py -v
python -m pytest tests/test_api.py -v
```

During review, non-API tests were verified successfully, but the full suite could not be completed in the review environment because FastAPI dependencies were unavailable and dependency installation was blocked by network restrictions.

## Known Limitations and Follow-Up Recommendations

Before merging, the following follow-up improvements are recommended:

1. Confirm the intended service request status workflow and avoid duplicate enum values for assigned/acknowledged states.
2. Add tests for missing required fields such as guest ID, room number, and category.
3. Add API retrieval tests to confirm that created requests can be fetched after persistence.
4. Remove any unrelated repository helper changes if they are not required for this feature.
5. Consider exposing staff notifications through a dedicated staff-facing workflow in a future PR.

## Suggested Commit Message

```text
feat: add guest service request workflow
```

## Suggested PR Title

```text
feat: add guest service request workflow
```

## Suggested PR Description

```markdown
## Summary

Added a guest service request workflow that allows hotel guests to request housekeeping, room service, or maintenance assistance without calling reception.

## Changes

- Added service request model updates
- Added service request creation business logic
- Added service category validation
- Added estimated completion time calculation
- Added staff notification placeholder
- Added in-memory persistence support
- Added service request API endpoints
- Updated OpenAPI documentation
- Added tests for the main service request workflow

## Testing

- `python -m pytest tests/ -v`
- `python -m pytest tests/test_services.py -v`
- `python -m pytest tests/test_api.py -v`

## Notes

Staff notification is currently implemented as an internal in-memory placeholder. Durable or external notifications can be added in a future focused PR if needed.
```

## Final PR Readiness Recommendation

**Needs minor fixes before PR.**

The feature is focused and mostly satisfies the requested user story. The main recommended cleanup is to confirm the service request status enum behavior, remove unrelated changes if unnecessary, and add a few missing validation/retrieval tests before opening the final peer review PR.

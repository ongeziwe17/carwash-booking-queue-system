# Course Validation Contribution

## Repository: https://github.com/keo-codes/the-it-code-academy

## Pull Request: https://github.com/keo-codes/the-it-code-academy/pull/53

**Title:** `fix: validate duplicate course creation`

## Technical Summary

This contribution improves the Assignment 12 Course Service by adding validation that prevents invalid course records from being saved to JSON persistence.

The Course Service already supported creating, updating, listing, and deleting course records, but it did not fully protect the integrity of the course data. A user could create multiple courses with the same `course_id`, and course titles made only of whitespace were accepted as valid input. These edge cases could lead to ambiguous course records and poor data quality.

The change adds a small, focused validation layer inside `CourseService` and includes unit tests that prove the new behavior works as expected.

---

## Problem Identified

### 1. Duplicate course IDs were allowed

The previous `create_course` implementation appended every new course to the `courses` list without checking whether another course already used the same `course_id`.

That meant this data could be persisted:

```json
[
  {
    "course_id": 101,
    "title": "Python Basics",
    "description": "Introductory Python course",
    "instructor_id": 1
  },
  {
    "course_id": 101,
    "title": "Python Basics Duplicate",
    "description": "Duplicate course",
    "instructor_id": 1
  }
]
```

This is a data integrity issue because API clients and service methods depend on `course_id` behaving like a unique identifier.

### 2. Whitespace-only course titles were accepted

The previous title validation only checked whether `title` was truthy. A string such as `"   "` is truthy in Python, so it passed validation even though it is effectively empty.

This allowed invalid course records such as:

```json
{
  "course_id": 102,
  "title": "   ",
  "description": "Invalid title",
  "instructor_id": 1
}
```

### 3. Course update could replace a valid title with a blank title

The update flow also needed the same validation rule. Without it, an existing valid course could be updated to use a whitespace-only title.

---

## Why This Matters

This fix improves the reliability and correctness of the Course Service in several ways:

- **Data integrity:** prevents duplicate course identifiers from being stored.
- **Cleaner API behavior:** invalid input is rejected before persistence.
- **Consistency:** course validation now better matches the existing student creation pattern, which already rejects duplicate student IDs.
- **Maintainability:** the course lookup logic is centralized in one helper method instead of being repeated in multiple CRUD methods.
- **Regression safety:** new tests document and enforce the expected behavior.

---

## Root Cause

The root cause was missing validation in `CourseService.create_course` and incomplete title validation in both `create_course` and `update_course`.

Before the fix, the service performed only this title check during course creation:

```python
if not title:
    raise ValueError("Course title is required")
```

That check rejected `None` and empty strings, but it did not reject whitespace-only strings. The service also did not check for an existing course before appending a new one.

---

## Solution Implemented

### 1. Added a reusable course lookup helper

A private helper method was added to centralize course lookup by ID:

```python
def _get_course_by_id(self, course_id: int):
    return next((c for c in self.courses if c["course_id"] == course_id), None)
```

This helper is now used by create, update, and delete logic.

### 2. Added duplicate course ID validation

Course creation now checks for an existing course before adding a new record:

```python
if self._get_course_by_id(course_id):
    raise ValueError(f"Course with ID {course_id} already exists.")
```

### 3. Strengthened course title validation

Course creation now rejects missing or whitespace-only titles:

```python
if not title or not title.strip():
    raise ValueError("Course title is required")
```

Course update now rejects a provided title if it is blank after trimming whitespace:

```python
if title is not None:
    if not title.strip():
        raise ValueError("Course title is required")
    course["title"] = title
```

---

## Files Modified

| File | Purpose |
| --- | --- |
| `Assignment 12/services/course_service.py` | Adds course lookup reuse, duplicate ID validation, and stronger title validation. |
| `Assignment 12/tests/test_course_service.py` | Adds focused unit tests for the new validation behavior. |

---

## Test Coverage Added

### `test_create_course_rejects_duplicate_course_id`

Verifies that:

- the first course with a new ID is created successfully;
- creating another course with the same `course_id` raises `ValueError`;
- only the original course remains in memory;
- only the original course is persisted to `data/courses.json`.

### `test_create_course_rejects_blank_title`

Verifies that:

- a whitespace-only title is rejected during course creation;
- no invalid course is added to the service state.

### `test_update_course_rejects_blank_title`

Verifies that:

- an existing course cannot be updated to use a blank title;
- the original valid course remains unchanged after the failed update.

---

## Testing Commands

The focused Assignment 12 test suite passed:

```bash
python -m pytest 'Assignment 12/tests'
```

Result:

```text
4 passed
```

A repository-root test run was also attempted:

```bash
python -m pytest
```

That run did not complete because of pre-existing import path issues in other assignment folders, specifically Assignment 10 and Assignment 11. Those failures are unrelated to the Course Service change.

---

## Expected Behavior After the Fix

### Duplicate ID request

Attempting to create a second course with the same ID now raises an error:

```python
service.create_course(101, "Python Basics", "Introductory Python course")
service.create_course(101, "Duplicate", "Duplicate course")
```

Expected result:

```text
ValueError: Course with ID 101 already exists.
```

### Blank title request

Attempting to create or update a course with a whitespace-only title now raises an error:

```python
service.create_course(102, "   ", "Invalid title")
```

Expected result:

```text
ValueError: Course title is required
```

---

## Impact

This is a small, production-quality change with low risk and clear value:

- does not change public API routes;
- does not introduce new dependencies;
- does not alter the JSON persistence format;
- preserves existing successful course creation behavior;
- only rejects invalid input that should not be persisted;
- adds tests to confirm the bug fix and validation behavior.

---

## Commit Message

```text
fix: validate duplicate course creation
```

---
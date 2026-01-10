# Requirements Document

## Introduction

This document specifies the requirements for adding execution cancellation functionality to the JMeter Web Runner application. Users need the ability to cancel test executions that are either queued or currently running, providing better control over long-running or mistakenly started tests.

## Glossary

- **Execution**: A JMeter test run initiated by a user, which can be in QUEUED, RUNNING, COMPLETED, FAILED, or CANCELLED status
- **ExecutionQueue**: The system component that manages queued and running executions with concurrency limits
- **JMeterEngine**: The service responsible for executing JMeter tests as external processes
- **ExecutionService**: The service that orchestrates execution lifecycle and coordinates with the queue
- **Process**: The operating system process running the JMeter test

## Requirements

### Requirement 1: Cancel Queued Execution

**User Story:** As a user, I want to cancel a queued execution, so that I can remove tests from the queue that I no longer want to run.

#### Acceptance Criteria

1. WHEN a user requests to cancel an execution with status QUEUED, THE System SHALL remove the execution from the queue
2. WHEN a queued execution is cancelled, THE System SHALL update the execution status to CANCELLED
3. WHEN a queued execution is cancelled, THE System SHALL set the completedAt timestamp to the current time
4. WHEN a queued execution is cancelled, THE System SHALL update queue positions for remaining queued executions
5. WHEN a user attempts to cancel an execution that is not QUEUED or RUNNING, THE System SHALL return an error indicating the execution cannot be cancelled

### Requirement 2: Cancel Running Execution

**User Story:** As a user, I want to cancel a running execution, so that I can stop long-running tests that are taking too long or were started by mistake.

#### Acceptance Criteria

1. WHEN a user requests to cancel an execution with status RUNNING, THE System SHALL terminate the JMeter process
2. WHEN a running execution is cancelled, THE System SHALL update the execution status to CANCELLED
3. WHEN a running execution is cancelled, THE System SHALL set the completedAt timestamp to the current time
4. WHEN a running execution is cancelled, THE System SHALL calculate and store the duration from startedAt to completedAt
5. WHEN a running execution is cancelled, THE System SHALL free up execution capacity in the queue
6. WHEN a running execution is cancelled, THE System SHALL attempt to start the next queued execution if available

### Requirement 3: Process Termination

**User Story:** As a system administrator, I want the system to properly terminate JMeter processes, so that cancelled executions do not continue consuming system resources.

#### Acceptance Criteria

1. WHEN terminating a JMeter process, THE System SHALL destroy the process and all its child processes
2. WHEN a process termination fails, THE System SHALL log the error and still update the execution status to CANCELLED
3. WHEN a process is terminated, THE System SHALL clean up any temporary resources associated with the execution

### Requirement 4: API Endpoint for Cancellation

**User Story:** As a frontend developer, I want a REST API endpoint to cancel executions, so that I can provide cancellation functionality in the user interface.

#### Acceptance Criteria

1. THE System SHALL provide a DELETE endpoint at /api/executions/{id}/cancel
2. WHEN a valid execution ID is provided, THE System SHALL return HTTP 200 with the updated execution details
3. WHEN an invalid execution ID is provided, THE System SHALL return HTTP 404 with an error message
4. WHEN an execution cannot be cancelled due to its status, THE System SHALL return HTTP 400 with an error message
5. WHEN a cancellation request is received, THE System SHALL log the cancellation attempt with the execution ID

### Requirement 5: UI Integration

**User Story:** As a user, I want a cancel button in the web interface, so that I can easily cancel executions without using API tools.

#### Acceptance Criteria

1. WHEN an execution has status QUEUED or RUNNING, THE UI SHALL display a "Cancel" button next to the execution
2. WHEN an execution has status COMPLETED, FAILED, or CANCELLED, THE UI SHALL NOT display a "Cancel" button
3. WHEN a user clicks the Cancel button, THE UI SHALL send a cancellation request to the API
4. WHEN a cancellation is successful, THE UI SHALL update the execution status to "cancelled" immediately
5. WHEN a cancellation fails, THE UI SHALL display an error message to the user

### Requirement 6: Execution Status Model

**User Story:** As a developer, I want a CANCELLED status in the execution model, so that cancelled executions are clearly distinguished from completed or failed executions.

#### Acceptance Criteria

1. THE ExecutionStatus enum SHALL include a CANCELLED value
2. WHEN displaying execution status, THE System SHALL format CANCELLED status as "cancelled" in lowercase
3. WHEN an execution is cancelled, THE System SHALL persist the CANCELLED status to the repository

# Implementation Plan: Execution Cancellation

## Overview

This implementation plan breaks down the execution cancellation feature into discrete coding tasks. The implementation will add the ability to cancel both queued and running JMeter test executions, providing users with better control over their test runs.

## Tasks

- [x] 1. Add CANCELLED status to ExecutionStatus enum
  - Add CANCELLED value to the ExecutionStatus enum
  - _Requirements: 6.1_

- [x] 2. Implement process tracking in JMeterEngine
  - [x] 2.1 Add ConcurrentHashMap to track active processes by execution ID
    - Add field `private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>()`
    - _Requirements: 2.1, 3.1_
  
  - [x] 2.2 Modify executeTest to track processes
    - Store process in activeProcesses map after starting
    - Remove process from map in finally block when execution completes
    - _Requirements: 2.1, 3.1_
  
  - [x] 2.3 Implement terminateExecution method
    - Check if process exists and is alive
    - Call process.destroyForcibly() to terminate process and children
    - Remove process from tracking map
    - Return boolean indicating if termination was attempted
    - Log any errors but don't throw exceptions
    - _Requirements: 2.1, 3.1, 3.2_

- [x] 3. Add queue management methods to ExecutionQueue
  - [x] 3.1 Implement removeFromQueue method
    - Remove execution ID from queuedExecutions
    - Return boolean indicating if removal was successful
    - Use synchronized to ensure thread safety
    - _Requirements: 1.1, 1.4_
  
  - [x] 3.2 Implement cancelRunning method
    - Remove execution from runningExecutions map
    - Decrement runningCount
    - Use synchronized to ensure thread safety
    - _Requirements: 2.5_

- [x] 4. Implement cancellation logic in ExecutionService
  - [x] 4.1 Implement cancelExecution method
    - Retrieve execution by ID
    - Verify execution status is QUEUED or RUNNING
    - Throw IllegalStateException if status is not cancellable
    - Delegate to cancelQueuedExecution or cancelRunningExecution based on status
    - _Requirements: 1.2, 1.5, 2.2_
  
  - [x] 4.2 Implement cancelQueuedExecution method
    - Call executionQueue.removeFromQueue
    - Update execution status to CANCELLED
    - Set completedAt timestamp
    - Save and return updated execution
    - _Requirements: 1.1, 1.2, 1.3_
  
  - [x] 4.3 Implement cancelRunningExecution method
    - Call jmeterEngine.terminateExecution
    - Update execution status to CANCELLED
    - Set completedAt timestamp
    - Calculate duration from startedAt to completedAt
    - Save execution
    - Call executionQueue.cancelRunning to free capacity
    - Call processQueue to start next execution
    - Return updated execution
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

- [x] 5. Add cancellation API endpoint to ExecutionController
  - [x] 5.1 Implement DELETE /api/executions/{id}/cancel endpoint
    - Call executionService.cancelExecution
    - Return HTTP 200 with execution details on success
    - Catch ResourceNotFoundException and return HTTP 404
    - Catch IllegalStateException and return HTTP 400
    - Log cancellation attempts
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 6. Checkpoint - Ensure backend implementation compiles
  - Ensure all code compiles without errors
  - Ask the user if questions arise

- [x] 7. Update frontend to support cancellation
  - [x] 7.1 Add Cancel button to execution UI
    - Display Cancel button for executions with status "queued" or "running"
    - Hide Cancel button for executions with status "completed", "failed", or "cancelled"
    - _Requirements: 5.1, 5.2_
  
  - [x] 7.2 Implement cancel button click handler
    - Send DELETE request to /api/executions/{id}/cancel
    - Update execution status to "cancelled" on success
    - Display error message on failure
    - Refresh execution list after cancellation
    - _Requirements: 5.3, 5.4, 5.5_

- [x] 8. Write unit tests for cancellation functionality
  - [x] 8.1 Test cancellation of queued execution
    - Create a queued execution and cancel it
    - Verify status changes to CANCELLED
    - Verify execution is removed from queue
    - _Requirements: 1.1, 1.2_
  
  - [x] 8.2 Test cancellation of running execution
    - Create a running execution and cancel it
    - Verify status changes to CANCELLED
    - Verify JMeterEngine.terminateExecution is called
    - Verify queue capacity is freed
    - _Requirements: 2.1, 2.2, 2.5_
  
  - [x] 8.3 Test cancellation of completed execution fails
    - Create a completed execution
    - Attempt to cancel it
    - Verify IllegalStateException is thrown
    - _Requirements: 1.5_
  
  - [x] 8.4 Test API endpoint returns 404 for invalid ID
    - Send DELETE request with non-existent execution ID
    - Verify HTTP 404 response
    - _Requirements: 4.3_
  
  - [x] 8.5 Test API endpoint returns 400 for non-cancellable status
    - Create execution with COMPLETED status
    - Send DELETE request to cancel it
    - Verify HTTP 400 response
    - _Requirements: 4.4_

- [ ] 9. Write property-based tests for cancellation
  - [ ] 9.1 Property test: Queued execution removal
    - **Property 1: Queued execution removal**
    - **Validates: Requirements 1.1**
    - Generate random queued executions
    - Cancel each execution
    - Verify execution is removed from queue
  
  - [ ] 9.2 Property test: Status transition to CANCELLED
    - **Property 2: Status transition to CANCELLED**
    - **Validates: Requirements 1.2, 2.2**
    - Generate random executions with QUEUED or RUNNING status
    - Cancel each execution
    - Verify status is CANCELLED
  
  - [ ] 9.3 Property test: Completion timestamp setting
    - **Property 3: Completion timestamp setting**
    - **Validates: Requirements 1.3, 2.3**
    - Generate random cancellable executions
    - Cancel each execution
    - Verify completedAt is set and within 5 seconds of current time
  
  - [ ] 9.4 Property test: Queue position updates
    - **Property 4: Queue position updates**
    - **Validates: Requirements 1.4**
    - Generate random set of queued executions
    - Cancel one execution
    - Verify remaining executions have sequential positions starting from 1
  
  - [ ] 9.5 Property test: Invalid status cancellation rejection
    - **Property 5: Invalid status cancellation rejection**
    - **Validates: Requirements 1.5**
    - Generate random executions with COMPLETED, FAILED, or CANCELLED status
    - Attempt to cancel each execution
    - Verify IllegalStateException is thrown
  
  - [ ] 9.6 Property test: Running execution duration calculation
    - **Property 6: Running execution duration calculation**
    - **Validates: Requirements 2.4**
    - Generate random running executions with startedAt timestamps
    - Cancel each execution
    - Verify duration equals (completedAt - startedAt) in seconds
  
  - [ ] 9.7 Property test: Capacity release on cancellation
    - **Property 7: Capacity release on cancellation**
    - **Validates: Requirements 2.5**
    - Generate random running executions
    - Record initial running count
    - Cancel each execution
    - Verify running count decreased by 1
  
  - [ ] 9.8 Property test: Next execution processing
    - **Property 8: Next execution processing**
    - **Validates: Requirements 2.6**
    - Generate system state with running execution and queued executions
    - Cancel running execution
    - Verify next queued execution transitions to RUNNING
  
  - [ ] 9.9 Property test: API response for valid cancellation
    - **Property 9: API response for valid cancellation**
    - **Validates: Requirements 4.2**
    - Generate random valid execution IDs with QUEUED or RUNNING status
    - Send DELETE request to cancel
    - Verify HTTP 200 response with status "cancelled"
  
  - [ ] 9.10 Property test: API response for invalid execution ID
    - **Property 10: API response for invalid execution ID**
    - **Validates: Requirements 4.3**
    - Generate random non-existent execution IDs
    - Send DELETE request to cancel
    - Verify HTTP 404 response
  
  - [ ] 9.11 Property test: API response for non-cancellable status
    - **Property 11: API response for non-cancellable status**
    - **Validates: Requirements 4.4**
    - Generate random executions with COMPLETED, FAILED, or CANCELLED status
    - Send DELETE request to cancel
    - Verify HTTP 400 response
  
  - [ ] 9.12 Property test: Status formatting
    - **Property 12: Status formatting**
    - **Validates: Requirements 6.2**
    - Generate random executions with CANCELLED status
    - Format for API response
    - Verify status field is lowercase "cancelled"
  
  - [ ] 9.13 Property test: Status persistence
    - **Property 13: Status persistence**
    - **Validates: Requirements 6.3**
    - Generate random executions
    - Cancel each execution
    - Retrieve from repository
    - Verify status is CANCELLED

- [ ] 10. Final checkpoint - Ensure all tests pass
  - Run all unit tests
  - Verify all tests pass
  - Ask the user if questions arise

## Notes

- Each task references specific requirements for traceability
- Property tests should run minimum 100 iterations each
- Process termination uses destroyForcibly() for reliable termination
- All queue operations use synchronized blocks for thread safety
- Cancellation is resilient to process termination failures
- All tests are required for comprehensive validation

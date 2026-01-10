# Design Document: Execution Cancellation

## Overview

This design document describes the implementation of execution cancellation functionality for the JMeter Web Runner application. The feature allows users to cancel test executions that are either queued (waiting to run) or currently running. This provides users with better control over their test executions and prevents wasted resources on unwanted or mistakenly started tests.

The implementation extends the existing execution management system by:
- Adding a CANCELLED status to the ExecutionStatus enum
- Implementing cancellation logic in ExecutionService for both queued and running executions
- Tracking JMeter processes to enable termination of running tests
- Providing a REST API endpoint for cancellation requests
- Updating the web UI to display cancel buttons for cancellable executions

## Architecture

The cancellation feature integrates with the existing execution architecture:

```
┌─────────────┐
│   Web UI    │
└──────┬──────┘
       │ DELETE /api/executions/{id}/cancel
       ▼
┌─────────────────────┐
│ExecutionController  │
└──────┬──────────────┘
       │ cancelExecution(id)
       ▼
┌─────────────────────┐         ┌──────────────────┐
│ ExecutionService    │◄────────┤ ExecutionQueue   │
└──────┬──────────────┘         └──────────────────┘
       │
       │ terminateProcess(id)
       ▼
┌─────────────────────┐
│   JMeterEngine      │
└─────────────────────┘
```

The cancellation flow differs based on execution status:

**Queued Execution Cancellation:**
1. User requests cancellation via API
2. ExecutionService verifies execution is QUEUED
3. ExecutionQueue removes execution from queue
4. ExecutionService updates status to CANCELLED
5. Queue positions are recalculated for remaining executions

**Running Execution Cancellation:**
1. User requests cancellation via API
2. ExecutionService verifies execution is RUNNING
3. JMeterEngine terminates the JMeter process
4. ExecutionService updates status to CANCELLED
5. ExecutionQueue frees up capacity
6. Next queued execution starts if available

## Components and Interfaces

### ExecutionStatus Enum Extension

Add CANCELLED status to the existing enum:

```java
public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED  // New status
}
```

### JMeterEngine Process Tracking

Modify JMeterEngine to track running processes for cancellation:

```java
public class JMeterEngine {
    // Track active processes by execution ID
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    
    public JMeterExecutionResult executeTest(String testFilePath, String executionId) {
        // ... existing setup code ...
        
        Process process = processBuilder.start();
        
        // Track the process
        activeProcesses.put(executionId, process);
        
        try {
            // ... existing execution code ...
        } finally {
            // Remove from tracking when complete
            activeProcesses.remove(executionId);
        }
    }
    
    public boolean terminateExecution(String executionId) {
        Process process = activeProcesses.get(executionId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();  // Forcefully terminate process and children
            activeProcesses.remove(executionId);
            return true;
        }
        return false;
    }
}
```

### ExecutionQueue Cancellation Support

Add methods to ExecutionQueue for removing queued executions:

```java
public class ExecutionQueue {
    public synchronized boolean removeFromQueue(String executionId) {
        boolean removed = queuedExecutions.remove(executionId);
        if (removed) {
            log.info("Execution {} removed from queue. Queue size: {}", 
                     executionId, queuedExecutions.size());
        }
        return removed;
    }
    
    public synchronized void cancelRunning(String executionId) {
        if (runningExecutions.remove(executionId) != null) {
            runningCount.decrementAndGet();
            log.info("Execution {} cancelled. Running: {}/{}", 
                     executionId, runningCount.get(), maxConcurrentExecutions);
        }
    }
}
```

### ExecutionService Cancellation Logic

Add cancellation method to ExecutionService:

```java
public class ExecutionService {
    public Execution cancelExecution(String executionId) {
        Execution execution = getExecution(executionId);
        
        // Verify execution can be cancelled
        if (execution.getStatus() != ExecutionStatus.QUEUED && 
            execution.getStatus() != ExecutionStatus.RUNNING) {
            throw new IllegalStateException(
                "Cannot cancel execution with status: " + execution.getStatus());
        }
        
        log.info("Cancelling execution: {} with status: {}", 
                 executionId, execution.getStatus());
        
        if (execution.getStatus() == ExecutionStatus.QUEUED) {
            return cancelQueuedExecution(execution);
        } else {
            return cancelRunningExecution(execution);
        }
    }
    
    private Execution cancelQueuedExecution(Execution execution) {
        // Remove from queue
        boolean removed = executionQueue.removeFromQueue(execution.getId());
        
        if (!removed) {
            log.warn("Execution {} not found in queue, may have already started", 
                     execution.getId());
        }
        
        // Update execution status
        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setCompletedAt(Instant.now());
        
        return executionRepository.save(execution);
    }
    
    private Execution cancelRunningExecution(Execution execution) {
        // Terminate the JMeter process
        boolean terminated = jmeterEngine.terminateExecution(execution.getId());
        
        if (!terminated) {
            log.warn("Failed to terminate process for execution: {}", 
                     execution.getId());
        }
        
        // Update execution status
        Instant completedAt = Instant.now();
        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setCompletedAt(completedAt);
        
        // Calculate duration if started
        if (execution.getStartedAt() != null) {
            execution.setDuration(
                Duration.between(execution.getStartedAt(), completedAt).getSeconds());
        }
        
        Execution savedExecution = executionRepository.save(execution);
        
        // Free up capacity and process queue
        executionQueue.cancelRunning(execution.getId());
        processQueue();
        
        return savedExecution;
    }
}
```

### ExecutionController API Endpoint

Add DELETE endpoint for cancellation:

```java
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {
    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelExecution(@PathVariable String id) {
        log.info("Received cancellation request for execution: {}", id);
        
        try {
            Execution execution = executionService.cancelExecution(id);
            Map<String, Object> response = buildExecutionResponse(execution);
            log.info("Execution {} cancelled successfully", id);
            return ResponseEntity.ok(response);
            
        } catch (ResourceNotFoundException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Execution not found: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (IllegalStateException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
}
```

## Data Models

### Execution Model

No changes needed to the Execution model - it already has all required fields:
- `status`: Will now support CANCELLED value
- `completedAt`: Set when execution is cancelled
- `duration`: Calculated for cancelled running executions

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Queued execution removal

*For any* execution in QUEUED status, when cancelled, it should be removed from the execution queue and no longer appear in the queued executions list.

**Validates: Requirements 1.1**

### Property 2: Status transition to CANCELLED

*For any* execution with status QUEUED or RUNNING, when cancelled, the execution status should be updated to CANCELLED.

**Validates: Requirements 1.2, 2.2**

### Property 3: Completion timestamp setting

*For any* execution that is cancelled, the completedAt timestamp should be set and should be within a reasonable time window (e.g., 5 seconds) of the current time.

**Validates: Requirements 1.3, 2.3**

### Property 4: Queue position updates

*For any* set of queued executions, when one execution is cancelled, the queue positions of all remaining executions should be recalculated such that positions are sequential starting from 1 with no gaps.

**Validates: Requirements 1.4**

### Property 5: Invalid status cancellation rejection

*For any* execution with status COMPLETED, FAILED, or CANCELLED, attempting to cancel it should result in an IllegalStateException being thrown.

**Validates: Requirements 1.5**

### Property 6: Running execution duration calculation

*For any* running execution that is cancelled, the duration field should be calculated as the difference in seconds between startedAt and completedAt timestamps.

**Validates: Requirements 2.4**

### Property 7: Capacity release on cancellation

*For any* running execution that is cancelled, the running count in the execution queue should decrease by 1, freeing up capacity for the next queued execution.

**Validates: Requirements 2.5**

### Property 8: Next execution processing

*For any* system state where a running execution is cancelled and there are queued executions waiting, the next queued execution should transition to RUNNING status.

**Validates: Requirements 2.6**

### Property 9: API response for valid cancellation

*For any* valid execution ID with status QUEUED or RUNNING, a DELETE request to /api/executions/{id}/cancel should return HTTP 200 with the updated execution details including status "cancelled".

**Validates: Requirements 4.2**

### Property 10: API response for invalid execution ID

*For any* non-existent execution ID, a DELETE request to /api/executions/{id}/cancel should return HTTP 404 with an error message.

**Validates: Requirements 4.3**

### Property 11: API response for non-cancellable status

*For any* execution with status COMPLETED, FAILED, or CANCELLED, a DELETE request to /api/executions/{id}/cancel should return HTTP 400 with an error message indicating the execution cannot be cancelled.

**Validates: Requirements 4.4**

### Property 12: Status formatting

*For any* execution with CANCELLED status, when formatted for API response, the status field should be the lowercase string "cancelled".

**Validates: Requirements 6.2**

### Property 13: Status persistence

*For any* execution that is cancelled, retrieving the execution from the repository should return an execution with status CANCELLED.

**Validates: Requirements 6.3**

## Error Handling

### Process Termination Failures

When `Process.destroyForcibly()` fails or the process is already terminated:
- Log the failure with execution ID and error details
- Continue with status update to CANCELLED
- Do not throw exceptions to the caller
- The system should be resilient to process termination failures

### Race Conditions

Handle race conditions where execution status changes between check and cancellation:
- Use synchronized blocks in ExecutionQueue for queue modifications
- Re-check execution status after acquiring locks
- If execution has already transitioned (e.g., QUEUED → RUNNING), handle appropriately
- Return appropriate error messages to the user

### Concurrent Cancellation Requests

If multiple cancellation requests arrive for the same execution:
- First request processes normally
- Subsequent requests should see CANCELLED status and return error
- Use atomic operations where possible to prevent inconsistent state

## Testing Strategy

### Unit Tests

Unit tests will verify specific examples and edge cases:

1. **Cancellation of queued execution** - Verify a specific queued execution can be cancelled
2. **Cancellation of running execution** - Verify a specific running execution can be cancelled
3. **Cancellation of completed execution fails** - Verify attempting to cancel a completed execution throws exception
4. **API endpoint returns 404 for invalid ID** - Verify specific invalid ID returns 404
5. **API endpoint returns 400 for non-cancellable status** - Verify specific non-cancellable execution returns 400
6. **Process termination is called** - Verify JMeterEngine.terminateExecution is invoked for running executions
7. **Queue capacity is freed** - Verify running count decreases after cancelling running execution

### Property-Based Tests

Property-based tests will verify universal properties across all inputs using jqwik:

1. **Property 1-13** - Each correctness property listed above will be implemented as a property-based test
2. **Test configuration** - Each property test will run minimum 100 iterations
3. **Test tagging** - Each test will be tagged with: `Feature: execution-cancellation, Property N: [property text]`
4. **Generators** - Create smart generators for:
   - Executions in various states (QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED)
   - Valid and invalid execution IDs
   - Queue states with multiple executions
   - Timing scenarios for duration calculations

### Integration Tests

Integration tests will verify end-to-end cancellation flows:

1. **Full cancellation flow for queued execution** - Create, queue, and cancel an execution
2. **Full cancellation flow for running execution** - Start and cancel a real JMeter test
3. **Queue processing after cancellation** - Verify next execution starts after cancelling running execution
4. **API endpoint integration** - Test actual HTTP requests to cancellation endpoint

### Testing Notes

- Property tests handle comprehensive input coverage through randomization
- Unit tests focus on specific examples and integration points
- Both types of tests are necessary for comprehensive coverage
- Process termination testing may require platform-specific considerations (Windows vs Unix)
- UI testing will be manual or use separate frontend testing tools

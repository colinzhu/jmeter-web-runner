package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.exception.ResourceNotFoundException;
import io.github.colinzhu.jmeter.webrunner.model.Execution;
import io.github.colinzhu.jmeter.webrunner.model.ExecutionStatus;
import io.github.colinzhu.jmeter.webrunner.repository.ExecutionRepository;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import io.github.colinzhu.jmeter.webrunner.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for execution cancellation functionality
 * Tests: cancellation of queued and running executions, error cases
 * Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.5, 4.3, 4.4
 */
class ExecutionCancellationUnitTest {

    private ExecutionService executionService;
    private ExecutionRepository executionRepository;
    private FileRepository fileRepository;
    private ReportRepository reportRepository;
    private JMeterEngine jmeterEngine;
    private ExecutionQueue executionQueue;

    @BeforeEach
    void setUp() {
        executionRepository = new ExecutionRepository();
        fileRepository = mock(FileRepository.class);
        reportRepository = new ReportRepository();
        jmeterEngine = mock(JMeterEngine.class);
        executionQueue = mock(ExecutionQueue.class);

        executionService = new ExecutionService(
                executionRepository,
                fileRepository,
                reportRepository,
                jmeterEngine,
                executionQueue
        );
    }

    /**
     * Test cancellation of queued execution
     * Requirements: 1.1, 1.2
     */
    @Test
    void cancelQueuedExecution_shouldUpdateStatusAndRemoveFromQueue() {
        // Arrange: Create a queued execution
        String executionId = "test-execution-1";
        String fileId = "test-file-1";

        Execution queuedExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.QUEUED)
                .queuePosition(1)
                .createdAt(Instant.now())
                .build();

        executionRepository.save(queuedExecution);

        // Mock queue removal
        when(executionQueue.removeFromQueue(executionId)).thenReturn(true);

        // Act: Cancel the execution
        Execution cancelledExecution = executionService.cancelExecution(executionId);

        // Assert: Verify status changed to CANCELLED
        assertThat(cancelledExecution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(cancelledExecution.getCompletedAt()).isNotNull();

        // Verify execution was removed from queue
        verify(executionQueue, times(1)).removeFromQueue(executionId);

        // Verify execution is persisted with CANCELLED status
        Execution retrievedExecution = executionRepository.findById(executionId).orElseThrow();
        assertThat(retrievedExecution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
    }

    /**
     * Test cancellation of running execution
     * Requirements: 2.1, 2.2, 2.5
     */
    @Test
    void cancelRunningExecution_shouldTerminateProcessAndFreeCapacity() {
        // Arrange: Create a running execution
        String executionId = "test-execution-2";
        String fileId = "test-file-2";
        Instant startedAt = Instant.now().minusSeconds(10);

        Execution runningExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.RUNNING)
                .queuePosition(0)
                .createdAt(Instant.now().minusSeconds(20))
                .startedAt(startedAt)
                .build();

        executionRepository.save(runningExecution);

        // Mock process termination
        when(jmeterEngine.terminateExecution(executionId)).thenReturn(true);

        // Act: Cancel the execution
        Execution cancelledExecution = executionService.cancelExecution(executionId);

        // Assert: Verify status changed to CANCELLED
        assertThat(cancelledExecution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(cancelledExecution.getCompletedAt()).isNotNull();
        assertThat(cancelledExecution.getDuration()).isNotNull();
        assertThat(cancelledExecution.getDuration()).isGreaterThan(0);

        // Verify JMeterEngine.terminateExecution was called
        verify(jmeterEngine, times(1)).terminateExecution(executionId);

        // Verify queue capacity was freed
        verify(executionQueue, times(1)).cancelRunning(executionId);
    }

    /**
     * Test cancellation of completed execution fails
     * Requirements: 1.5
     */
    @Test
    void cancelCompletedExecution_shouldThrowIllegalStateException() {
        // Arrange: Create a completed execution
        String executionId = "test-execution-3";
        String fileId = "test-file-3";

        Execution completedExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.COMPLETED)
                .queuePosition(0)
                .createdAt(Instant.now().minusSeconds(60))
                .startedAt(Instant.now().minusSeconds(50))
                .completedAt(Instant.now().minusSeconds(10))
                .duration(40L)
                .build();

        executionRepository.save(completedExecution);

        // Act & Assert: Attempt to cancel should throw IllegalStateException
        assertThatThrownBy(() -> executionService.cancelExecution(executionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel execution with status: COMPLETED");

        // Verify no interactions with queue or engine
        verifyNoInteractions(executionQueue);
        verifyNoInteractions(jmeterEngine);
    }

    /**
     * Test cancellation with invalid execution ID
     * Requirements: 4.3
     */
    @Test
    void cancelExecution_withInvalidId_shouldThrowResourceNotFoundException() {
        // Arrange: Use a non-existent execution ID
        String invalidExecutionId = "non-existent-execution";

        // Act & Assert: Attempt to cancel should throw ResourceNotFoundException
        assertThatThrownBy(() -> executionService.cancelExecution(invalidExecutionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Execution not found with id: " + invalidExecutionId);

        // Verify no interactions with queue or engine
        verifyNoInteractions(executionQueue);
        verifyNoInteractions(jmeterEngine);
    }

    /**
     * Test cancellation of failed execution fails
     * Requirements: 1.5
     */
    @Test
    void cancelFailedExecution_shouldThrowIllegalStateException() {
        // Arrange: Create a failed execution
        String executionId = "test-execution-4";
        String fileId = "test-file-4";

        Execution failedExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.FAILED)
                .queuePosition(0)
                .createdAt(Instant.now().minusSeconds(60))
                .startedAt(Instant.now().minusSeconds(50))
                .completedAt(Instant.now().minusSeconds(10))
                .duration(40L)
                .error("Test execution failed")
                .build();

        executionRepository.save(failedExecution);

        // Act & Assert: Attempt to cancel should throw IllegalStateException
        assertThatThrownBy(() -> executionService.cancelExecution(executionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel execution with status: FAILED");

        // Verify no interactions with queue or engine
        verifyNoInteractions(executionQueue);
        verifyNoInteractions(jmeterEngine);
    }

    /**
     * Test cancellation of already cancelled execution fails
     * Requirements: 1.5
     */
    @Test
    void cancelCancelledExecution_shouldThrowIllegalStateException() {
        // Arrange: Create a cancelled execution
        String executionId = "test-execution-5";
        String fileId = "test-file-5";

        Execution cancelledExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.CANCELLED)
                .queuePosition(0)
                .createdAt(Instant.now().minusSeconds(60))
                .completedAt(Instant.now().minusSeconds(10))
                .build();

        executionRepository.save(cancelledExecution);

        // Act & Assert: Attempt to cancel should throw IllegalStateException
        assertThatThrownBy(() -> executionService.cancelExecution(executionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel execution with status: CANCELLED");

        // Verify no interactions with queue or engine
        verifyNoInteractions(executionQueue);
        verifyNoInteractions(jmeterEngine);
    }
}

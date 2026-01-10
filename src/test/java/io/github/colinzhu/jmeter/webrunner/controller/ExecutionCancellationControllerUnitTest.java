package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.exception.GlobalExceptionHandler;
import io.github.colinzhu.jmeter.webrunner.exception.ResourceNotFoundException;
import io.github.colinzhu.jmeter.webrunner.model.Execution;
import io.github.colinzhu.jmeter.webrunner.model.ExecutionStatus;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import io.github.colinzhu.jmeter.webrunner.service.ExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for execution cancellation API endpoints
 * Tests: API responses for valid and invalid cancellation requests
 * Requirements: 4.3, 4.4
 */
class ExecutionCancellationControllerUnitTest {

    private MockMvc mockMvc;
    private ExecutionService executionService;
    private FileRepository fileRepository;

    @BeforeEach
    void setUp() {
        executionService = mock(ExecutionService.class);
        fileRepository = mock(FileRepository.class);

        ExecutionController executionController = new ExecutionController(executionService, fileRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(executionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Test API endpoint returns 404 for invalid execution ID
     * Requirements: 4.3
     */
    @Test
    void cancelExecution_withInvalidId_shouldReturn404() throws Exception {
        // Arrange: Mock service to throw ResourceNotFoundException
        String invalidExecutionId = "non-existent-execution";
        when(executionService.cancelExecution(invalidExecutionId))
                .thenThrow(new ResourceNotFoundException("Execution not found with id: " + invalidExecutionId));

        // Act & Assert: Send DELETE request and verify 404 response
        mockMvc.perform(delete("/api/executions/{id}/cancel", invalidExecutionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("Execution not found: " + invalidExecutionId));

        verify(executionService, times(1)).cancelExecution(invalidExecutionId);
    }

    /**
     * Test API endpoint returns 400 for non-cancellable status (COMPLETED)
     * Requirements: 4.4
     */
    @Test
    void cancelExecution_withCompletedStatus_shouldReturn400() throws Exception {
        // Arrange: Mock service to throw IllegalStateException
        String executionId = "completed-execution";
        when(executionService.cancelExecution(executionId))
                .thenThrow(new IllegalStateException("Cannot cancel execution with status: COMPLETED"));

        // Act & Assert: Send DELETE request and verify 400 response
        mockMvc.perform(delete("/api/executions/{id}/cancel", executionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("Cannot cancel execution with status: COMPLETED"));

        verify(executionService, times(1)).cancelExecution(executionId);
    }

    /**
     * Test API endpoint returns 400 for non-cancellable status (FAILED)
     * Requirements: 4.4
     */
    @Test
    void cancelExecution_withFailedStatus_shouldReturn400() throws Exception {
        // Arrange: Mock service to throw IllegalStateException
        String executionId = "failed-execution";
        when(executionService.cancelExecution(executionId))
                .thenThrow(new IllegalStateException("Cannot cancel execution with status: FAILED"));

        // Act & Assert: Send DELETE request and verify 400 response
        mockMvc.perform(delete("/api/executions/{id}/cancel", executionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("Cannot cancel execution with status: FAILED"));

        verify(executionService, times(1)).cancelExecution(executionId);
    }

    /**
     * Test API endpoint returns 400 for non-cancellable status (CANCELLED)
     * Requirements: 4.4
     */
    @Test
    void cancelExecution_withCancelledStatus_shouldReturn400() throws Exception {
        // Arrange: Mock service to throw IllegalStateException
        String executionId = "cancelled-execution";
        when(executionService.cancelExecution(executionId))
                .thenThrow(new IllegalStateException("Cannot cancel execution with status: CANCELLED"));

        // Act & Assert: Send DELETE request and verify 400 response
        mockMvc.perform(delete("/api/executions/{id}/cancel", executionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("Cannot cancel execution with status: CANCELLED"));

        verify(executionService, times(1)).cancelExecution(executionId);
    }

    /**
     * Test API endpoint returns 200 for successful cancellation
     * Requirements: 4.2
     */
    @Test
    void cancelExecution_withValidQueuedExecution_shouldReturn200() throws Exception {
        // Arrange: Mock successful cancellation
        String executionId = "queued-execution";
        String fileId = "test-file";

        Execution cancelledExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.CANCELLED)
                .queuePosition(0)
                .createdAt(Instant.now().minusSeconds(30))
                .completedAt(Instant.now())
                .build();

        when(executionService.cancelExecution(executionId)).thenReturn(cancelledExecution);

        // Act & Assert: Send DELETE request and verify 200 response
        mockMvc.perform(delete("/api/executions/{id}/cancel", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(executionId))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.fileId").value(fileId));

        verify(executionService, times(1)).cancelExecution(executionId);
    }

    /**
     * Test API endpoint returns 200 for successful cancellation of running execution
     * Requirements: 4.2
     */
    @Test
    void cancelExecution_withValidRunningExecution_shouldReturn200() throws Exception {
        // Arrange: Mock successful cancellation
        String executionId = "running-execution";
        String fileId = "test-file";

        Execution cancelledExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.CANCELLED)
                .queuePosition(0)
                .createdAt(Instant.now().minusSeconds(60))
                .startedAt(Instant.now().minusSeconds(30))
                .completedAt(Instant.now())
                .duration(30L)
                .build();

        when(executionService.cancelExecution(executionId)).thenReturn(cancelledExecution);

        // Act & Assert: Send DELETE request and verify 200 response
        mockMvc.perform(delete("/api/executions/{id}/cancel", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(executionId))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.fileId").value(fileId))
                .andExpect(jsonPath("$.duration").value(30));

        verify(executionService, times(1)).cancelExecution(executionId);
    }
}

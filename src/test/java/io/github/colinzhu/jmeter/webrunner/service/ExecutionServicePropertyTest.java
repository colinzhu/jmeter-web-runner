package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.model.Execution;
import io.github.colinzhu.jmeter.webrunner.model.ExecutionStatus;
import io.github.colinzhu.jmeter.webrunner.repository.ExecutionRepository;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import io.github.colinzhu.jmeter.webrunner.repository.ReportRepository;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for ExecutionService
 * Feature: jmeter-web-runner
 */
class ExecutionServicePropertyTest {

    private ExecutionService createExecutionService(FileRepository fileRepository) {
        ExecutionRepository executionRepository = new ExecutionRepository();
        ReportRepository reportRepository = new ReportRepository();
        JMeterEngine jmeterEngine = mock(JMeterEngine.class);
        ExecutionQueue executionQueue = mock(ExecutionQueue.class);
        return new ExecutionService(executionRepository, fileRepository, reportRepository, jmeterEngine, executionQueue);
    }

    /**
     * Property 4: Execution Creation
     * For any valid uploaded file, triggering execution should create an execution record
     * with initial status "queued".
     * <p>
     * Feature: jmeter-web-runner, Property 4: Execution Creation
     * Validates: Requirements 2.1
     */
    @Property(tries = 100)
    void executionCreation_createsExecutionWithQueuedStatus(@ForAll("validFileIds") String fileId) {
        // Setup: Mock FileRepository to indicate the file exists
        FileRepository fileRepository = mock(FileRepository.class);
        when(fileRepository.existsById(fileId)).thenReturn(true);

        ExecutionService executionService = createExecutionService(fileRepository);

        // Act: Create execution for the valid file
        Execution execution = executionService.createExecution(fileId);

        // Assert: Execution should be created with QUEUED status
        assertThat(execution).isNotNull();
        assertThat(execution.getId()).isNotNull().isNotEmpty();
        assertThat(execution.getFileId()).isEqualTo(fileId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(execution.getCreatedAt()).isNotNull();
        assertThat(execution.getQueuePosition()).isNotNull();
    }

    /**
     * Property 5: Execution Status Tracking
     * For any execution, querying its status should return the current state
     * (queued, running, completed, or failed) along with relevant metadata.
     * <p>
     * Feature: jmeter-web-runner, Property 5: Execution Status Tracking
     * Validates: Requirements 2.2, 3.3
     */
    @Property(tries = 100)
    void executionStatusTracking_returnsCurrentStateWithMetadata(
            @ForAll("validFileIds") String fileId,
            @ForAll("executionStatuses") ExecutionStatus status) {
        // Setup: Mock FileRepository and create ExecutionService
        FileRepository fileRepository = mock(FileRepository.class);
        when(fileRepository.existsById(fileId)).thenReturn(true);

        ExecutionService executionService = createExecutionService(fileRepository);

        // Act: Create execution and update its status
        Execution execution = executionService.createExecution(fileId);
        String executionId = execution.getId();

        // Manually update the execution status in the repository to simulate state changes
        ExecutionRepository executionRepository = new ExecutionRepository();
        Execution updatedExecution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(status)
                .queuePosition(execution.getQueuePosition())
                .createdAt(execution.getCreatedAt())
                .build();
        executionRepository.save(updatedExecution);

        // Create a new service with the updated repository to query the status
        ReportRepository reportRepository = new ReportRepository();
        JMeterEngine jmeterEngine = mock(JMeterEngine.class);
        ExecutionQueue executionQueue = mock(ExecutionQueue.class);
        ExecutionService queryService = new ExecutionService(executionRepository, fileRepository, reportRepository, jmeterEngine, executionQueue);

        // Query the execution status
        Execution queriedExecution = queryService.getExecution(executionId);

        // Assert: The queried execution should have the correct status and metadata
        assertThat(queriedExecution).isNotNull();
        assertThat(queriedExecution.getId()).isEqualTo(executionId);
        assertThat(queriedExecution.getFileId()).isEqualTo(fileId);
        assertThat(queriedExecution.getStatus()).isEqualTo(status);
        assertThat(queriedExecution.getCreatedAt()).isNotNull();
    }

    /**
     * Provides valid file IDs for testing
     * File IDs can be various string formats (UUIDs, alphanumeric, etc.)
     */
    @Provide
    Arbitrary<String> validFileIds() {
        // Generate various valid file ID formats
        Arbitrary<String> uuidStyle = Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .withChars('-')
                .ofMinLength(10)
                .ofMaxLength(36);

        Arbitrary<String> alphanumeric = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(50);

        Arbitrary<String> withDashes = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(5)
                .ofMaxLength(50);

        return Arbitraries.oneOf(uuidStyle, alphanumeric, withDashes)
                .filter(s -> !s.trim().isEmpty());
    }

    /**
     * Property 19: Execution List Completeness
     * For any set of executions in the system, the executions list endpoint should return
     * all executions with their current status (active, queued, completed, or failed).
     * <p>
     * Feature: jmeter-web-runner, Property 19: Execution List Completeness
     * Validates: Requirements 6.5
     */
    @Property(tries = 100)
    void executionListCompleteness_returnsAllExecutionsWithStatus(
            @ForAll("executionLists") java.util.List<Execution> executions) {
        // Setup: Create ExecutionService with a fresh repository
        FileRepository fileRepository = mock(FileRepository.class);
        ExecutionRepository executionRepository = new ExecutionRepository();
        ReportRepository reportRepository = new ReportRepository();
        JMeterEngine jmeterEngine = mock(JMeterEngine.class);

        // Mock file repository to accept all file IDs
        when(fileRepository.existsById(any())).thenReturn(true);

        // Save all executions to the repository
        for (Execution execution : executions) {
            executionRepository.save(execution);
        }

        ExecutionQueue executionQueue = mock(ExecutionQueue.class);
        ExecutionService executionService = new ExecutionService(executionRepository, fileRepository, reportRepository, jmeterEngine, executionQueue);

        // Act: Get all executions
        java.util.List<Execution> retrievedExecutions = executionService.getAllExecutions();

        // Assert: All executions should be returned
        assertThat(retrievedExecutions).isNotNull();
        assertThat(retrievedExecutions).hasSize(executions.size());

        // Verify each execution is present with correct status
        for (Execution originalExecution : executions) {
            boolean found = retrievedExecutions.stream()
                    .anyMatch(e -> e.getId().equals(originalExecution.getId())
                            && e.getStatus().equals(originalExecution.getStatus())
                            && e.getFileId().equals(originalExecution.getFileId()));

            assertThat(found)
                    .withFailMessage("Execution with ID %s and status %s should be in the list",
                            originalExecution.getId(), originalExecution.getStatus())
                    .isTrue();
        }
    }

    /**
     * Provides lists of executions with various statuses for testing
     */
    @Provide
    Arbitrary<java.util.List<Execution>> executionLists() {
        return Arbitraries.integers().between(0, 10)
                .flatMap(size -> {
                    if (size == 0) {
                        return Arbitraries.just(java.util.Collections.emptyList());
                    }

                    return Combinators.combine(
                            validFileIds().list().ofSize(size),
                            executionStatuses().list().ofSize(size)
                    ).as((fileIds, statuses) -> {
                        java.util.List<Execution> executions = new java.util.ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            Execution execution = Execution.builder()
                                    .id(java.util.UUID.randomUUID().toString())
                                    .fileId(fileIds.get(i))
                                    .status(statuses.get(i))
                                    .queuePosition(i)
                                    .createdAt(java.time.Instant.now())
                                    .build();
                            executions.add(execution);
                        }
                        return executions;
                    });
                });
    }

    /**
     * Provides all possible execution statuses for testing
     */
    @Provide
    Arbitrary<ExecutionStatus> executionStatuses() {
        return Arbitraries.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.RUNNING,
                ExecutionStatus.COMPLETED,
                ExecutionStatus.FAILED
        );
    }
}

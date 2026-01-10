package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.exception.ResourceNotFoundException;
import io.github.colinzhu.jmeter.webrunner.model.Execution;
import io.github.colinzhu.jmeter.webrunner.model.ExecutionStatus;
import io.github.colinzhu.jmeter.webrunner.model.File;
import io.github.colinzhu.jmeter.webrunner.model.Report;
import io.github.colinzhu.jmeter.webrunner.repository.ExecutionRepository;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import io.github.colinzhu.jmeter.webrunner.repository.ReportRepository;
import io.github.colinzhu.jmeter.webrunner.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {
    private final ExecutionRepository executionRepository;
    private final FileRepository fileRepository;
    private final ReportRepository reportRepository;
    private final JMeterEngine jmeterEngine;
    private final ExecutionQueue executionQueue;

    public Execution createExecution(String fileId) {
        // Verify file exists
        if (!fileRepository.existsById(fileId)) {
            throw new ResourceNotFoundException("File not found with id: " + fileId);
        }

        String executionId = IdGenerator.generateTimestampId();

        Execution execution = Execution.builder()
                .id(executionId)
                .fileId(fileId)
                .status(ExecutionStatus.QUEUED)
                .queuePosition(0)
                .createdAt(Instant.now())
                .build();

        Execution savedExecution = executionRepository.save(execution);

        // Add to queue
        executionQueue.enqueue(executionId);

        // Update queue position
        int queuePosition = executionQueue.getQueuePosition(executionId);
        savedExecution.setQueuePosition(queuePosition);
        savedExecution = executionRepository.save(savedExecution);

        log.info("Created execution with ID: {} for file: {} at queue position: {}",
                executionId, fileId, queuePosition);

        // Try to process the queue
        processQueue();

        return savedExecution;
    }

    @Async("taskExecutor")
    public void executeTest(String executionId) {
        Execution execution = null;
        try {
            execution = getExecution(executionId);

            // Update status to RUNNING
            execution.setStatus(ExecutionStatus.RUNNING);
            execution.setStartedAt(Instant.now());
            execution.setQueuePosition(0);
            executionRepository.save(execution);

            // Mark as running in queue
            executionQueue.markRunning(execution);

            log.info("Starting execution: {}", executionId);

            // Get test file path
            String fileId = execution.getFileId();
            File file = fileRepository.findById(fileId)
                    .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

            // Execute JMeter test
            JMeterEngine.JMeterExecutionResult result = jmeterEngine.executeTest(file.getPath(), executionId);

            // Update execution based on result
            Instant completedAt = Instant.now();
            execution.setCompletedAt(completedAt);
            execution.setDuration(Duration.between(execution.getStartedAt(), completedAt).getSeconds());

            if (result.isSuccess()) {
                execution.setStatus(ExecutionStatus.COMPLETED);

                // Create report record
                String reportId = createReport(executionId, result.getReportPath());
                execution.setReportId(reportId);

                log.info("Execution completed successfully: {}", executionId);
            } else {
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setError(result.getErrorMessage());

                log.error("Execution failed: {} - {}", executionId, result.getErrorMessage());
            }

            executionRepository.save(execution);
        } catch (Exception e) {
            log.error("Execution {} failed with exception: {}", executionId, e.getMessage(), e);
            if (execution != null) {
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setError("Execution failed: " + e.getMessage());
                execution.setCompletedAt(Instant.now());
                if (execution.getStartedAt() != null) {
                    execution.setDuration(Duration.between(execution.getStartedAt(), Instant.now()).getSeconds());
                }
                executionRepository.save(execution);
            }
        } finally {
            // Always mark as completed to free up capacity
            executionQueue.markCompleted(executionId);
            processQueue();
        }
    }

    private String createReport(String executionId, String reportPath) {
        // Use the same ID as execution for linking
        String reportId = executionId;

        // Calculate report size
        long size = calculateDirectorySize(reportPath);

        Report report = Report.builder()
                .id(reportId)
                .executionId(executionId)
                .path(reportPath)
                .createdAt(Instant.now())
                .size(size)
                .build();

        reportRepository.save(report);

        log.info("Created report with ID: {} for execution: {}", reportId, executionId);

        return reportId;
    }

    private long calculateDirectorySize(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                return Files.walk(path)
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .sum();
            }
        } catch (IOException e) {
            log.warn("Failed to calculate directory size for: {}", directoryPath, e);
        }
        return 0L;
    }

    public Execution getExecution(String executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found with id: " + executionId));
    }

    public List<Execution> getAllExecutions() {
        return executionRepository.findAll();
    }

    /**
     * Process the queue and start executions when slots are available
     */
    private void processQueue() {
        log.info("processQueue called. Current running: {}/{}, queued: {}", 
                 executionQueue.getRunningCount(), 
                 executionQueue.getMaxConcurrentExecutions(),
                 executionQueue.getQueuedCount());
        
        // Fill all available slots since executeTest is async
        // Don't check hasCapacity() here - let dequeueNext() enforce the limit atomically
        while (true) {
            String nextExecutionId = executionQueue.dequeueNext();
            if (nextExecutionId == null) {
                break; // No capacity or no more queued executions
            }
            log.info("Processing queued execution: {}. After dequeue - running: {}/{}", 
                     nextExecutionId,
                     executionQueue.getRunningCount(),
                     executionQueue.getMaxConcurrentExecutions());
            executeTest(nextExecutionId);
        }
        
        log.info("processQueue finished. Final running: {}/{}, queued: {}", 
                 executionQueue.getRunningCount(), 
                 executionQueue.getMaxConcurrentExecutions(),
                 executionQueue.getQueuedCount());
    }

    /**
     * Cancel an execution
     * @param executionId the ID of the execution to cancel
     * @return the updated execution
     * @throws ResourceNotFoundException if execution not found
     * @throws IllegalStateException if execution status is not cancellable
     */
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

    /**
     * Cancel a queued execution
     * @param execution the execution to cancel
     * @return the updated execution
     */
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

    /**
     * Cancel a running execution
     * @param execution the execution to cancel
     * @return the updated execution
     */
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

    /**
     * Clear execution history by deleting all completed, failed, and cancelled executions.
     * Queued and running executions are preserved.
     * @return the number of executions deleted
     */
    public int clearHistory() {
        List<Execution> allExecutions = executionRepository.findAll();
        
        List<Execution> executionsToDelete = allExecutions.stream()
                .filter(e -> e.getStatus() == ExecutionStatus.COMPLETED ||
                             e.getStatus() == ExecutionStatus.FAILED ||
                             e.getStatus() == ExecutionStatus.CANCELLED)
                .toList();
        
        int deletedCount = 0;
        for (Execution execution : executionsToDelete) {
            executionRepository.deleteById(execution.getId());
            deletedCount++;
            log.info("Deleted execution {} with status {} from history",
                     execution.getId(), execution.getStatus());
        }
        
        log.info("Cleared {} executions from history", deletedCount);
        return deletedCount;
    }
}

package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.model.Execution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ExecutionQueue {

    private final ConcurrentLinkedQueue<String> queuedExecutions = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Execution> runningExecutions = new ConcurrentHashMap<>();
    private final AtomicInteger runningCount = new AtomicInteger(0);
    @Value("${app.jmeter.max-concurrent-executions:2}")
    private int maxConcurrentExecutions;

    /**
     * Add an execution to the queue
     *
     * @param executionId The ID of the execution to queue
     */
    public void enqueue(String executionId) {
        queuedExecutions.offer(executionId);
        log.info("Execution {} added to queue. Queue size: {}", executionId, queuedExecutions.size());
    }

    /**
     * Get the queue position for an execution (1-based)
     *
     * @param executionId The ID of the execution
     * @return The position in the queue, or 0 if not in queue
     */
    public int getQueuePosition(String executionId) {
        int position = 1;
        for (String id : queuedExecutions) {
            if (id.equals(executionId)) {
                return position;
            }
            position++;
        }
        return 0;
    }

    /**
     * Check if there is capacity to run another execution
     *
     * @return true if we can start another execution
     */
    public boolean hasCapacity() {
        return runningCount.get() < maxConcurrentExecutions;
    }

    /**
     * Get the next execution from the queue if capacity is available
     *
     * @return The execution ID to run, or null if no capacity or empty queue
     */
    public String dequeueNext() {
        // Atomically check and increment to avoid race condition
        int currentCount;
        do {
            currentCount = runningCount.get();
            if (currentCount >= maxConcurrentExecutions) {
                return null;
            }
        } while (!runningCount.compareAndSet(currentCount, currentCount + 1));

        // Now we have reserved a slot, try to get an execution
        String executionId = queuedExecutions.poll();
        if (executionId != null) {
            // Add a placeholder Execution object to track this dequeued execution
            // This ensures markCompleted() can properly decrement the counter
            Execution placeholder = new Execution();
            placeholder.setId(executionId);
            runningExecutions.put(executionId, placeholder);
            log.info("Dequeued execution {}. Running: {}/{}",
                    executionId, runningCount.get(), maxConcurrentExecutions);
        } else {
            // No execution available, release the reserved slot
            runningCount.decrementAndGet();
        }
        return executionId;
    }

    /**
     * Mark an execution as running
     *
     * @param execution The execution that is now running
     */
    public void markRunning(Execution execution) {
        runningExecutions.put(execution.getId(), execution);
        log.info("Execution {} marked as running", execution.getId());
    }

    /**
     * Mark an execution as completed and free up capacity
     *
     * @param executionId The ID of the completed execution
     */
    public void markCompleted(String executionId) {
        // Remove from running executions (may be null if only dequeued but not yet marked running)
        if (runningExecutions.containsKey(executionId)) {
            runningExecutions.remove(executionId);
            runningCount.decrementAndGet();
            log.info("Execution {} completed. Running: {}/{}",
                    executionId, runningCount.get(), maxConcurrentExecutions);
        }
    }

    /**
     * Get the number of currently running executions
     *
     * @return The count of running executions
     */
    public int getRunningCount() {
        return runningCount.get();
    }

    /**
     * Get the number of queued executions
     *
     * @return The count of queued executions
     */
    public int getQueuedCount() {
        return queuedExecutions.size();
    }

    /**
     * Check if an execution is currently running
     *
     * @param executionId The ID of the execution
     * @return true if the execution is running
     */
    public boolean isRunning(String executionId) {
        return runningExecutions.containsKey(executionId);
    }

    /**
     * Get the maximum concurrent executions allowed
     *
     * @return The max concurrent executions
     */
    public int getMaxConcurrentExecutions() {
        return maxConcurrentExecutions;
    }

    /**
     * Remove an execution from the queue
     *
     * @param executionId The ID of the execution to remove
     * @return true if the execution was removed, false if it was not in the queue
     */
    public boolean removeFromQueue(String executionId) {
        boolean removed = queuedExecutions.remove(executionId);
        if (removed) {
            log.info("Execution {} removed from queue. Queue size: {}", 
                     executionId, queuedExecutions.size());
        }
        return removed;
    }

    /**
     * Cancel a running execution and free up capacity
     *
     * @param executionId The ID of the execution to cancel
     */
    public void cancelRunning(String executionId) {
        if (runningExecutions.remove(executionId) != null) {
            runningCount.decrementAndGet();
            log.info("Execution {} cancelled. Running: {}/{}", 
                     executionId, runningCount.get(), maxConcurrentExecutions);
        }
    }
}

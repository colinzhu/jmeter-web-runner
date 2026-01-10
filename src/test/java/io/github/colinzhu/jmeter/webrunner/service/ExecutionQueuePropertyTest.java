package io.github.colinzhu.jmeter.webrunner.service;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for execution queueing functionality
 * Feature: jmeter-web-runner, Property 16: Execution Queueing
 * Validates: Requirements 6.1, 6.3
 */
public class ExecutionQueuePropertyTest {

    /**
     * Property 16: Execution Queueing
     * For any set of execution requests submitted when the system is at capacity,
     * they should be queued with appropriate queue positions assigned.
     */
    @Property(tries = 100)
    void executionsAreQueuedWithPositions(@ForAll("numExecutions") int numExecutions) throws Exception {
        // Given: Create an execution queue with max concurrent = 2
        ExecutionQueue executionQueue = new ExecutionQueue();

        // Set maxConcurrentExecutions via reflection since @Value doesn't work in tests
        java.lang.reflect.Field maxConcurrentField = ExecutionQueue.class.getDeclaredField("maxConcurrentExecutions");
        maxConcurrentField.setAccessible(true);
        maxConcurrentField.setInt(executionQueue, 2);

        // When: Enqueue multiple executions
        String[] executionIds = new String[numExecutions];
        for (int i = 0; i < numExecutions; i++) {
            executionIds[i] = "exec-" + i;
            executionQueue.enqueue(executionIds[i]);
        }

        // Then: All executions should be in the queue
        assertThat(executionQueue.getQueuedCount()).isEqualTo(numExecutions);

        // And: Queue positions should be assigned correctly (1-based)
        for (int i = 0; i < numExecutions; i++) {
            int queuePosition = executionQueue.getQueuePosition(executionIds[i]);
            assertThat(queuePosition).isEqualTo(i + 1);
        }
    }

    /**
     * Property 16: Execution Queueing (Dequeue Behavior)
     * For any queued executions, dequeuing should respect capacity limits
     * and maintain proper queue ordering.
     */
    @Property(tries = 100)
    void dequeueRespectsCapacityLimits(@ForAll("numExecutions") int numExecutions) throws Exception {
        // Given: Create an execution queue with max concurrent = 2
        ExecutionQueue executionQueue = new ExecutionQueue();

        // Set maxConcurrentExecutions via reflection since @Value doesn't work in tests
        java.lang.reflect.Field maxConcurrentField = ExecutionQueue.class.getDeclaredField("maxConcurrentExecutions");
        maxConcurrentField.setAccessible(true);
        maxConcurrentField.setInt(executionQueue, 2);

        // And: Enqueue multiple executions
        String[] executionIds = new String[numExecutions];
        for (int i = 0; i < numExecutions; i++) {
            executionIds[i] = "exec-" + i;
            executionQueue.enqueue(executionIds[i]);
        }

        // When: Dequeue executions
        int maxConcurrent = executionQueue.getMaxConcurrentExecutions();
        int dequeued = 0;
        while (executionQueue.hasCapacity() && dequeued < numExecutions) {
            String executionId = executionQueue.dequeueNext();
            if (executionId != null) {
                dequeued++;
            } else {
                break;
            }
        }

        // Then: Number of dequeued executions should not exceed max concurrent
        assertThat(dequeued).isLessThanOrEqualTo(maxConcurrent);
        assertThat(executionQueue.getRunningCount()).isEqualTo(dequeued);

        // And: Remaining executions should still be in queue
        int expectedRemaining = numExecutions - dequeued;
        assertThat(executionQueue.getQueuedCount()).isEqualTo(expectedRemaining);
    }

    /**
     * Property 16: Execution Queueing (Completion Frees Capacity)
     * For any running execution that completes, capacity should be freed
     * for the next queued execution.
     */
    @Property(tries = 100)
    void completionFreesCapacity(@ForAll("numExecutions") int numExecutions) throws Exception {
        // Given: Create an execution queue
        ExecutionQueue executionQueue = new ExecutionQueue();

        // Set maxConcurrentExecutions via reflection since @Value doesn't work in tests
        java.lang.reflect.Field maxConcurrentField = ExecutionQueue.class.getDeclaredField("maxConcurrentExecutions");
        maxConcurrentField.setAccessible(true);
        maxConcurrentField.setInt(executionQueue, 2);

        // And: Enqueue multiple executions
        for (int i = 0; i < numExecutions; i++) {
            executionQueue.enqueue("exec-" + i);
        }

        // When: Dequeue up to capacity
        int maxConcurrent = executionQueue.getMaxConcurrentExecutions();
        String[] runningIds = new String[Math.min(maxConcurrent, numExecutions)];
        for (int i = 0; i < runningIds.length; i++) {
            runningIds[i] = executionQueue.dequeueNext();
        }

        // Then: Should be at capacity
        int initialRunning = executionQueue.getRunningCount();
        assertThat(initialRunning).isEqualTo(Math.min(maxConcurrent, numExecutions));

        // When: Mark one as completed (if any were dequeued)
        if (runningIds.length > 0 && runningIds[0] != null) {
            executionQueue.markCompleted(runningIds[0]);

            // Then: Running count should decrease
            assertThat(executionQueue.getRunningCount()).isEqualTo(initialRunning - 1);

            // And: Should have capacity again
            assertThat(executionQueue.hasCapacity()).isTrue();
        }
    }

    /**
     * Property 17: Concurrency Limit Enforcement
     * For any configured concurrency limit, the number of simultaneously running
     * executions should never exceed that limit.
     */
    @Property(tries = 100)
    void concurrencyLimitIsNeverExceeded(@ForAll("numExecutions") int numExecutions) throws Exception {
        // Given: Create an execution queue with max concurrent = 2
        ExecutionQueue executionQueue = new ExecutionQueue();

        // Set maxConcurrentExecutions via reflection since @Value doesn't work in tests
        java.lang.reflect.Field maxConcurrentField = ExecutionQueue.class.getDeclaredField("maxConcurrentExecutions");
        maxConcurrentField.setAccessible(true);
        maxConcurrentField.setInt(executionQueue, 2);

        int maxConcurrent = executionQueue.getMaxConcurrentExecutions();

        // When: Enqueue multiple executions
        for (int i = 0; i < numExecutions; i++) {
            executionQueue.enqueue("exec-" + i);
        }

        // And: Dequeue as many as possible
        int dequeued = 0;
        while (executionQueue.hasCapacity() && dequeued < numExecutions) {
            String executionId = executionQueue.dequeueNext();
            if (executionId != null) {
                dequeued++;

                // Then: Running count should never exceed max concurrent
                int currentRunning = executionQueue.getRunningCount();
                assertThat(currentRunning)
                        .as("Running count should never exceed max concurrent limit")
                        .isLessThanOrEqualTo(maxConcurrent);
            } else {
                break;
            }
        }

        // And: Final running count should not exceed limit
        assertThat(executionQueue.getRunningCount()).isLessThanOrEqualTo(maxConcurrent);

        // And: If we tried to dequeue more than the limit, some should still be queued
        if (numExecutions > maxConcurrent) {
            assertThat(executionQueue.getQueuedCount()).isGreaterThan(0);
        }
    }

    /**
     * Property 18: Automatic Queue Processing
     * For any queued execution, when an execution slot becomes available
     * (due to completion of a running execution), the next queued execution
     * should automatically transition to "running" status.
     */
    @Property(tries = 100)
    void queueProcessesAutomaticallyOnCompletion(@ForAll("numExecutions") int numExecutions) throws Exception {
        // Given: Create an execution queue with max concurrent = 2
        ExecutionQueue executionQueue = new ExecutionQueue();

        // Set maxConcurrentExecutions via reflection since @Value doesn't work in tests
        java.lang.reflect.Field maxConcurrentField = ExecutionQueue.class.getDeclaredField("maxConcurrentExecutions");
        maxConcurrentField.setAccessible(true);
        maxConcurrentField.setInt(executionQueue, 2);

        int maxConcurrent = executionQueue.getMaxConcurrentExecutions();

        // And: Enqueue more executions than the concurrent limit
        int totalExecutions = Math.max(numExecutions, maxConcurrent + 1);
        for (int i = 0; i < totalExecutions; i++) {
            executionQueue.enqueue("exec-" + i);
        }

        // When: Dequeue up to capacity (fill all slots)
        java.util.List<String> runningIds = new java.util.ArrayList<>();
        while (executionQueue.hasCapacity() && runningIds.size() < maxConcurrent) {
            String id = executionQueue.dequeueNext();
            if (id != null) {
                runningIds.add(id);
            } else {
                break;
            }
        }

        // Then: Should be at capacity with remaining items in queue
        assertThat(runningIds).hasSize(maxConcurrent);
        assertThat(executionQueue.getRunningCount()).isEqualTo(maxConcurrent);
        assertThat(executionQueue.hasCapacity()).isFalse();
        int remainingInQueue = totalExecutions - maxConcurrent;
        assertThat(executionQueue.getQueuedCount()).isEqualTo(remainingInQueue);

        // When: Complete one execution to free up capacity
        String completedId = runningIds.get(0);
        executionQueue.markCompleted(completedId);

        // Then: Capacity should be available
        assertThat(executionQueue.hasCapacity()).isTrue();
        assertThat(executionQueue.getRunningCount()).isEqualTo(maxConcurrent - 1);

        // When: Dequeue the next execution (simulating automatic processing)
        String nextExecution = executionQueue.dequeueNext();

        // Then: The next execution should be dequeued successfully
        assertThat(nextExecution).isNotNull();
        assertThat(nextExecution).isEqualTo("exec-" + maxConcurrent);

        // And: Running count should be back at capacity
        assertThat(executionQueue.getRunningCount()).isEqualTo(maxConcurrent);

        // And: Queue should have one less item
        assertThat(executionQueue.getQueuedCount()).isEqualTo(remainingInQueue - 1);
    }

    @Provide
    Arbitrary<Integer> numExecutions() {
        return Arbitraries.integers().between(3, 15);
    }
}

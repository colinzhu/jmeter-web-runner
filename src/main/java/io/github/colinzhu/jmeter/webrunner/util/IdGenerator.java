package io.github.colinzhu.jmeter.webrunner.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class for generating timestamp-based IDs
 */
public class IdGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH-mm-ss")
            .withZone(ZoneId.systemDefault());

    private static final AtomicLong counter = new AtomicLong(0);
    private static volatile long lastTimestamp = 0;
    private static final Lock lock = new ReentrantLock();

    /**
     * Generate a timestamp-based ID in the format: 2026-01-10T11-02-50-123456789
     * Uses hyphens instead of colons and dots for filesystem compatibility
     * Includes nanoseconds (9 digits) and a counter to prevent duplicate IDs in concurrent scenarios
     *
     * @return timestamp-based ID string
     */
    public static String generateTimestampId() {
        Instant now = Instant.now();
        long currentTimestamp = now.toEpochMilli() * 1_000_000 + now.getNano() % 1_000_000;
        
        long count;
        lock.lock();
        try {
            // Reset counter if we're in a new timestamp
            if (currentTimestamp != lastTimestamp) {
                counter.set(0);
                lastTimestamp = currentTimestamp;
            }
            count = counter.getAndIncrement();
        } finally {
            lock.unlock();
        }
        
        ZonedDateTime zonedDateTime = now.atZone(ZoneId.systemDefault());
        String dateTimePart = DATE_TIME_FORMATTER.format(zonedDateTime);
        String nanosPart = String.format("%09d", now.getNano());
        
        // If counter is 0, use original format for backward compatibility
        if (count == 0) {
            return dateTimePart + "-" + nanosPart;
        } else {
            // Add counter suffix for concurrent calls
            return dateTimePart + "-" + nanosPart + "-" + count;
        }
    }
}

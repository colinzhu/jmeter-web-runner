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
        return generateTimestampId(null);
    }

    /**
     * Generate a timestamp-based ID that includes the jmx filename.
     * Format: {sanitized-filename}-{timestamp}-{nanos} or {timestamp}-{nanos} if filename is null
     * The filename will have ".jmx" extension removed and special characters replaced with "-"
     *
     * @param jmxFilename the jmx filename (can be null)
     * @return timestamp-based ID string with filename prefix
     */
    public static String generateTimestampId(String jmxFilename) {
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
        
        // Build the base ID
        String baseId;
        if (count == 0) {
            baseId = dateTimePart + "-" + nanosPart;
        } else {
            // Add counter suffix for concurrent calls
            baseId = dateTimePart + "-" + nanosPart + "-" + count;
        }
        
        // If filename is provided, prepend sanitized filename
        if (jmxFilename != null && !jmxFilename.isEmpty()) {
            String sanitizedFilename = sanitizeFilename(jmxFilename);
            return sanitizedFilename + "-" + baseId;
        }
        
        return baseId;
    }

    /**
     * Sanitize jmx filename by removing .jmx extension and replacing special characters with "-"
     *
     * @param filename the original filename
     * @return sanitized filename safe for filesystem use
     */
    private static String sanitizeFilename(String filename) {
        // Remove .jmx extension (case-insensitive)
        String name = filename;
        if (name.toLowerCase().endsWith(".jmx")) {
            name = name.substring(0, name.length() - 4);
        }
        
        // Replace special characters with "-"
        // Keep alphanumeric, hyphens, and underscores, replace everything else with "-"
        name = name.replaceAll("[^a-zA-Z0-9_-]", "-");
        
        // Replace multiple consecutive hyphens with a single hyphen
        name = name.replaceAll("-+", "-");
        
        // Remove leading/trailing hyphens
        name = name.replaceAll("^-+|-+$", "");
        
        // If empty after sanitization, use a default value
        if (name.isEmpty()) {
            name = "jmx";
        }
        
        return name;
    }
}

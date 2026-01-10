package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;

/**
 * Interface for managing JMeter installation configuration.
 * Provides methods for storing, retrieving, and verifying JMeter installation.
 */
public interface JMeterManager {

    /**
     * Set the JMeter installation path.
     *
     * @param path The path to the JMeter installation directory
     */
    void setInstallationPath(String path);

    /**
     * Get the full path to the JMeter binary executable.
     *
     * @return The full path to jmeter or jmeter.bat, or null if not configured
     */
    String getJMeterBinaryPath();

    /**
     * Check if JMeter is configured.
     *
     * @return true if JMeter installation path is set, false otherwise
     */
    boolean isConfigured();

    /**
     * Verify the JMeter installation and get version information.
     *
     * @return JMeterInfo object containing version, path, availability, and error details
     */
    JMeterInfo verifyInstallation();

    /**
     * Clear the JMeter configuration.
     */
    void clearConfiguration();
}

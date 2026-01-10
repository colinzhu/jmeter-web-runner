package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.JMeterConfig;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for JMeterManager
 * Feature: jmeter-setup-upload
 */
class JMeterManagerPropertyTest {

    /**
     * Helper method to create a JMeterManager with mocked persistence
     */
    private JMeterManager createManager(JMeterConfig config) {
        PersistenceService persistenceService = mock(PersistenceService.class);
        doNothing().when(persistenceService).save(anyString(), any());
        doNothing().when(persistenceService).delete(anyString());
        return new JMeterManagerImpl(config, persistenceService);
    }

    /**
     * Property 12: Configuration Persistence Round-Trip
     * For any JMeter configuration that is set, the configuration should be retrievable
     * and persist across manager instances (simulating application restart).
     * <p>
     * Feature: jmeter-setup-upload, Property 12: Configuration Persistence Round-Trip
     * Validates: Requirements 3.4
     */
    @Property(tries = 100)
    void configurationPersistence_roundTrip(@ForAll("validInstallationPaths") String installationPath) {
        // Create a JMeterConfig instance (simulating Spring configuration)
        JMeterConfig config = new JMeterConfig();

        // Create first manager instance and set configuration
        JMeterManager manager1 = createManager(config);
        manager1.setInstallationPath(installationPath);

        // Verify configuration is set
        assertThat(manager1.isConfigured())
                .as("Configuration should be marked as configured after setting path")
                .isTrue();

        String binaryPath1 = manager1.getJMeterBinaryPath();
        assertThat(binaryPath1)
                .as("Binary path should be retrievable after configuration")
                .isNotNull();

        // Normalize paths for comparison (handle cross-platform path separators)
        String normalizedBinaryPath1 = normalizePath(binaryPath1);
        String normalizedInstallationPath = normalizePath(installationPath);
        assertThat(normalizedBinaryPath1)
                .as("Binary path should contain the installation path (normalized)")
                .contains(normalizedInstallationPath);

        // Create second manager instance with same config (simulating restart)
        JMeterManager manager2 = createManager(config);

        // Verify configuration persists
        assertThat(manager2.isConfigured())
                .as("Configuration should persist across manager instances")
                .isTrue();

        String binaryPath2 = manager2.getJMeterBinaryPath();
        assertThat(binaryPath2)
                .as("Binary path should be the same across manager instances")
                .isEqualTo(binaryPath1);

        // Verify the installation path is preserved
        String normalizedBinaryPath2 = normalizePath(binaryPath2);
        assertThat(normalizedBinaryPath2)
                .as("Binary path should still contain the original installation path (normalized)")
                .contains(normalizedInstallationPath);
    }

    /**
     * Property: Configuration Clear and Reset
     * For any configured JMeter installation, clearing the configuration should
     * make the manager report as not configured, and setting a new path should work.
     */
    @Property(tries = 100)
    void configurationClear_resetsState(
            @ForAll("validInstallationPaths") String path1,
            @ForAll("validInstallationPaths") String path2) {

        Assume.that(!path1.equals(path2));
        // Skip cases where one path is a substring of another to avoid false failures
        Assume.that(!normalizePath(path1).contains(normalizePath(path2)));
        Assume.that(!normalizePath(path2).contains(normalizePath(path1)));

        JMeterConfig config = new JMeterConfig();
        JMeterManager manager = createManager(config);

        // Set first configuration
        manager.setInstallationPath(path1);
        assertThat(manager.isConfigured()).isTrue();
        String binaryPath1 = manager.getJMeterBinaryPath();
        assertThat(normalizePath(binaryPath1)).contains(normalizePath(path1));

        // Clear configuration
        manager.clearConfiguration();
        assertThat(manager.isConfigured())
                .as("Manager should report as not configured after clearing")
                .isFalse();
        assertThat(manager.getJMeterBinaryPath())
                .as("Binary path should be null after clearing")
                .isNull();

        // Set second configuration
        manager.setInstallationPath(path2);
        assertThat(manager.isConfigured()).isTrue();
        String binaryPath2 = manager.getJMeterBinaryPath();
        String normalizedPath2 = normalizePath(binaryPath2);
        assertThat(normalizedPath2)
                .as("New configuration should be retrievable")
                .contains(normalizePath(path2));
        assertThat(normalizedPath2)
                .as("New binary path should be different from old binary path")
                .isNotEqualTo(normalizePath(binaryPath1));
    }

    /**
     * Property: Path Replacement
     * For any system with an existing JMeter installation path, setting a new path
     * should replace the previous one.
     */
    @Property(tries = 100)
    void pathReplacement_updatesConfiguration(
            @ForAll("validInstallationPaths") String oldPath,
            @ForAll("validInstallationPaths") String newPath) {

        Assume.that(!oldPath.equals(newPath));
        // Also skip cases where one path is a substring of the other
        // (e.g., "jmeter-A" and "/opt/jmeter-A" would cause false failures)
        Assume.that(!normalizePath(newPath).contains(normalizePath(oldPath)));
        Assume.that(!normalizePath(oldPath).contains(normalizePath(newPath)));

        JMeterConfig config = new JMeterConfig();
        JMeterManager manager = createManager(config);

        // Set initial path
        manager.setInstallationPath(oldPath);
        String oldBinaryPath = manager.getJMeterBinaryPath();
        assertThat(normalizePath(oldBinaryPath)).contains(normalizePath(oldPath));

        // Replace with new path
        manager.setInstallationPath(newPath);
        String newBinaryPath = manager.getJMeterBinaryPath();

        // Verify replacement
        String normalizedNewBinaryPath = normalizePath(newBinaryPath);
        assertThat(normalizedNewBinaryPath)
                .as("New binary path should contain new installation path")
                .contains(normalizePath(newPath));
        assertThat(normalizedNewBinaryPath)
                .as("New binary path should not be the same as old binary path")
                .isNotEqualTo(normalizePath(oldBinaryPath));
    }

    /**
     * Property: Binary Path Construction
     * For any valid installation path, the binary path should be constructed correctly
     * based on the operating system (jmeter.bat for Windows, jmeter for Unix).
     */
    @Property(tries = 100)
    void binaryPathConstruction_includesBinDirectory(@ForAll("validInstallationPaths") String installationPath) {
        JMeterConfig config = new JMeterConfig();
        JMeterManager manager = createManager(config);

        manager.setInstallationPath(installationPath);
        String binaryPath = manager.getJMeterBinaryPath();

        // Verify binary path structure (normalize for cross-platform comparison)
        String normalizedBinaryPath = normalizePath(binaryPath);
        String normalizedInstallationPath = normalizePath(installationPath);

        assertThat(normalizedBinaryPath)
                .as("Binary path should contain the installation path")
                .contains(normalizedInstallationPath);

        assertThat(binaryPath)
                .as("Binary path should include 'bin' directory")
                .contains("bin");

        // Verify OS-specific binary name
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            assertThat(binaryPath)
                    .as("Binary path on Windows should end with jmeter.bat")
                    .endsWith("jmeter.bat");
        } else {
            assertThat(binaryPath)
                    .as("Binary path on Unix should end with jmeter")
                    .endsWith("jmeter");
        }
    }

    /**
     * Property: Verification with Non-Existent Path
     * For any non-existent installation path, verification should report the installation
     * as not available with an appropriate error message.
     */
    @Property(tries = 100)
    void verification_reportsUnavailableForNonExistentPath(@ForAll("validInstallationPaths") String installationPath) {
        JMeterConfig config = new JMeterConfig();
        JMeterManager manager = createManager(config);

        manager.setInstallationPath(installationPath);
        JMeterInfo info = manager.verifyInstallation();

        // Since the path doesn't actually exist, verification should report unavailable
        assertThat(info.isAvailable())
                .as("Installation should be reported as unavailable for non-existent path")
                .isFalse();

        assertThat(info.getError())
                .as("Error message should be provided for unavailable installation")
                .isNotNull()
                .isNotEmpty();

        assertThat(info.getPath())
                .as("Path should still be included in verification result")
                .isEqualTo(installationPath);
    }

    /**
     * Property: Verification with Actual JMeter Installation
     * For any valid JMeter installation that exists on the filesystem, verification
     * should report it as available and detect the version.
     */
    @Property(tries = 10)
    void verification_reportsAvailableForValidInstallation() throws IOException {
        // Create a temporary JMeter-like installation structure
        Path tempDir = Files.createTempDirectory("jmeter-test");
        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);

        // Create a mock jmeter executable
        String os = System.getProperty("os.name").toLowerCase();
        String binaryName = os.contains("win") ? "jmeter.bat" : "jmeter";
        Path binaryPath = binDir.resolve(binaryName);

        // Create the file with executable content
        if (os.contains("win")) {
            // Windows batch file
            Files.writeString(binaryPath, "@echo off\necho Version 5.6.3\n");
        } else {
            // Unix shell script
            Files.writeString(binaryPath, "#!/bin/sh\necho Version 5.6.3\n");
            // Set executable permission on Unix
            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(binaryPath);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(binaryPath, perms);
            } catch (UnsupportedOperationException e) {
                // Not a POSIX filesystem, skip permission setting
            }
        }

        try {
            JMeterConfig config = new JMeterConfig();
            JMeterManager manager = createManager(config);

            manager.setInstallationPath(tempDir.toString());
            JMeterInfo info = manager.verifyInstallation();

            // Verify the installation is reported as available
            assertThat(info.isAvailable())
                    .as("Installation should be reported as available when binary exists")
                    .isTrue();

            assertThat(info.getPath())
                    .as("Path should match the configured installation path")
                    .isEqualTo(tempDir.toString());

            assertThat(info.getError())
                    .as("No error should be reported for valid installation")
                    .isNull();

        } finally {
            // Cleanup
            Files.deleteIfExists(binaryPath);
            Files.deleteIfExists(binDir);
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Provides valid installation path strings for testing.
     * These are syntactically valid paths but may not exist on the filesystem.
     */
    @Provide
    Arbitrary<String> validInstallationPaths() {
        // Generate various path formats
        Arbitrary<String> unixPaths = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(name -> "/opt/jmeter-" + name);

        Arbitrary<String> windowsPaths = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(name -> "C:\\jmeter-" + name);

        Arbitrary<String> relativePaths = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(name -> "jmeter-" + name);

        return Arbitraries.oneOf(unixPaths, windowsPaths, relativePaths);
    }

    /**
     * Normalize path separators for cross-platform comparison.
     * Converts all path separators to forward slashes.
     */
    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        return path.replace('\\', '/');
    }
}

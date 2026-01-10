package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.exception.JMeterNotAvailableException;
import io.github.colinzhu.jmeter.webrunner.exception.JMeterNotConfiguredException;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for JMeterEngine.
 * Feature: jmeter-setup-upload
 */
class JMeterEnginePropertyTest {

    /**
     * Property 22: Execution Uses Configured Path
     * For any test execution that is triggered, the JMeter Engine should use the JMeter binary path
     * retrieved from the JMeter Manager rather than relying on system PATH.
     * <p>
     * Validates: Requirements 7.1, 7.3
     * Feature: jmeter-setup-upload, Property 22: Execution Uses Configured Path
     */
    @Property(tries = 100)
    void executionUsesConfiguredPath(
            @ForAll("alphanumericStrings") String executionId,
            @ForAll("alphanumericStrings") String testFileName
    ) throws IOException {
        // Setup: Create a mock JMeter binary and test file
        Path tempDir = Files.createTempDirectory("jmeter-engine-test");

        try {
            Path jmeterBin = tempDir.resolve("jmeter-" + System.nanoTime());
            Path testFile = tempDir.resolve(testFileName + ".jmx");
            Path reportDir = tempDir.resolve("reports");

            Files.createDirectories(reportDir);
            Files.createFile(jmeterBin);
            Files.createFile(testFile);

            // Make binary executable on Unix systems
            if (jmeterBin.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(jmeterBin, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                ));
            }

            // Create mock JMeterManager that returns our configured path
            JMeterManager mockManager = new JMeterManager() {
                @Override
                public void setInstallationPath(String path) {
                }

                @Override
                public String getJMeterBinaryPath() {
                    return jmeterBin.toString();
                }

                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public JMeterInfo verifyInstallation() {
                    return null;
                }

                @Override
                public void clearConfiguration() {
                }
            };

            // Create StorageConfig
            StorageConfig storageConfig = new StorageConfig();
            storageConfig.setReportDir(reportDir.toString());

            // Create JMeterEngine with mock manager
            JMeterEngine engine = new JMeterEngine(storageConfig, mockManager);

            // Execute test - this will fail because jmeter binary is not real, but we can verify
            // that it attempted to use the configured path
            JMeterEngine.JMeterExecutionResult result = engine.executeTest(testFile.toString(), executionId);

            // The execution should fail (because our mock binary doesn't actually work),
            // but it should have attempted to use the configured path from the manager
            // We verify this by checking that no exception was thrown for unconfigured JMeter
            assertThat(result).isNotNull();
            // The result will be a failure, but not due to configuration issues
            assertThat(result.getErrorMessage()).doesNotContain("not configured");
        } finally {
            // Cleanup - delete directory recursively
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * Property 23: Unconfigured JMeter Error
     * For any test execution attempt when JMeter is not configured, the system should return
     * an error indicating that setup is required.
     * <p>
     * Validates: Requirements 7.2
     * Feature: jmeter-setup-upload, Property 23: Unconfigured JMeter Error
     */
    @Property(tries = 100)
    void unconfiguredJMeterError(
            @ForAll("alphanumericStrings") String executionId,
            @ForAll("alphanumericStrings") String testFileName
    ) throws IOException {
        // Setup: Create test file
        Path tempDir = Files.createTempDirectory("jmeter-engine-test-unconfigured");

        try {
            Path testFile = tempDir.resolve(testFileName + "-unconfigured.jmx");
            Path reportDir = tempDir.resolve("reports-unconfigured");

            Files.createDirectories(reportDir);
            Files.createFile(testFile);

            // Create mock JMeterManager that returns null (not configured)
            JMeterManager mockManager = new JMeterManager() {
                @Override
                public void setInstallationPath(String path) {
                }

                @Override
                public String getJMeterBinaryPath() {
                    return null; // Not configured
                }

                @Override
                public boolean isConfigured() {
                    return false;
                }

                @Override
                public JMeterInfo verifyInstallation() {
                    return null;
                }

                @Override
                public void clearConfiguration() {
                }
            };

            // Create StorageConfig
            StorageConfig storageConfig = new StorageConfig();
            storageConfig.setReportDir(reportDir.toString());

            // Create JMeterEngine with mock manager
            JMeterEngine engine = new JMeterEngine(storageConfig, mockManager);

            // Execute test - should throw JMeterNotConfiguredException
            assertThatThrownBy(() -> engine.executeTest(testFile.toString(), executionId))
                    .isInstanceOf(JMeterNotConfiguredException.class)
                    .hasMessageContaining("not configured")
                    .hasMessageContaining("Setup page");
        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * Property 24: Pre-Execution Availability Verification
     * For any test execution attempt, the JMeter Engine should verify JMeter availability
     * before starting the execution.
     * <p>
     * Validates: Requirements 7.4
     * Feature: jmeter-setup-upload, Property 24: Pre-Execution Availability Verification
     */
    @Property(tries = 100)
    void preExecutionAvailabilityVerification(
            @ForAll("alphanumericStrings") String executionId,
            @ForAll("alphanumericStrings") String testFileName
    ) throws IOException {
        // Setup: Create test file but NOT the JMeter binary
        Path tempDir = Files.createTempDirectory("jmeter-engine-test-verify");

        try {
            Path nonExistentJMeter = tempDir.resolve("nonexistent-jmeter-" + System.nanoTime());
            Path testFile = tempDir.resolve(testFileName + "-verify.jmx");
            Path reportDir = tempDir.resolve("reports-verify");

            Files.createDirectories(reportDir);
            Files.createFile(testFile);

            // Create mock JMeterManager that returns a path to non-existent binary
            JMeterManager mockManager = new JMeterManager() {
                @Override
                public void setInstallationPath(String path) {
                }

                @Override
                public String getJMeterBinaryPath() {
                    return nonExistentJMeter.toString(); // Path doesn't exist
                }

                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public JMeterInfo verifyInstallation() {
                    return null;
                }

                @Override
                public void clearConfiguration() {
                }
            };

            // Create StorageConfig
            StorageConfig storageConfig = new StorageConfig();
            storageConfig.setReportDir(reportDir.toString());

            // Create JMeterEngine with mock manager
            JMeterEngine engine = new JMeterEngine(storageConfig, mockManager);

            // Execute test - should throw JMeterNotAvailableException
            assertThatThrownBy(() -> engine.executeTest(testFile.toString(), executionId))
                    .isInstanceOf(JMeterNotAvailableException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining(nonExistentJMeter.toString());
        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * Provides alphanumeric strings for testing.
     */
    @Provide
    Arbitrary<String> alphanumericStrings() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20);
    }

    /**
     * Helper method to delete a directory recursively.
     */
    private void deleteDirectoryRecursively(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }
}

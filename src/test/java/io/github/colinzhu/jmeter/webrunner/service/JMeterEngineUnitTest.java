package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.exception.JMeterNotAvailableException;
import io.github.colinzhu.jmeter.webrunner.exception.JMeterNotConfiguredException;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JMeterEngine
 * Requirements: 7.1, 7.2, 7.5
 */
class JMeterEngineUnitTest {

    @TempDir
    Path tempDir;

    @Test
    void testExecutionWithConfiguredJMeter() throws IOException {
        // Create mock JMeter binary and test file
        Path jmeterBin = tempDir.resolve("jmeter");
        Path testFile = tempDir.resolve("test.jmx");
        Path reportDir = tempDir.resolve("reports");

        Files.createDirectories(reportDir);
        Files.createFile(jmeterBin);
        Files.createFile(testFile);

        // Create mock JMeterManager
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

        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setReportDir(reportDir.toString());

        JMeterEngine engine = new JMeterEngine(storageConfig, mockManager);

        // Execute test
        JMeterEngine.JMeterExecutionResult result = engine.executeTest(testFile.toString(), "test-1");

        // Verify result is returned (will fail because binary is not real, but no configuration error)
        assertThat(result).isNotNull();
        assertThat(result.getErrorMessage()).doesNotContain("not configured");
    }

    @Test
    void testExecutionWithoutConfiguredJMeter() throws IOException {
        // Create test file
        Path testFile = tempDir.resolve("test.jmx");
        Path reportDir = tempDir.resolve("reports");

        Files.createDirectories(reportDir);
        Files.createFile(testFile);

        // Create mock JMeterManager that returns null
        JMeterManager mockManager = new JMeterManager() {
            @Override
            public void setInstallationPath(String path) {
            }

            @Override
            public String getJMeterBinaryPath() {
                return null;
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

        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setReportDir(reportDir.toString());

        JMeterEngine engine = new JMeterEngine(storageConfig, mockManager);

        // Execute test - should throw exception
        assertThatThrownBy(() -> engine.executeTest(testFile.toString(), "test-2"))
                .isInstanceOf(JMeterNotConfiguredException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void testErrorHandlingWhenJMeterBecomesUnavailable() throws IOException {
        // Create test file but NOT the JMeter binary
        Path nonExistentJMeter = tempDir.resolve("nonexistent-jmeter");
        Path testFile = tempDir.resolve("test.jmx");
        Path reportDir = tempDir.resolve("reports");

        Files.createDirectories(reportDir);
        Files.createFile(testFile);

        // Create mock JMeterManager that returns path to non-existent binary
        JMeterManager mockManager = new JMeterManager() {
            @Override
            public void setInstallationPath(String path) {
            }

            @Override
            public String getJMeterBinaryPath() {
                return nonExistentJMeter.toString();
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

        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setReportDir(reportDir.toString());

        JMeterEngine engine = new JMeterEngine(storageConfig, mockManager);

        // Execute test - should throw exception
        assertThatThrownBy(() -> engine.executeTest(testFile.toString(), "test-3"))
                .isInstanceOf(JMeterNotAvailableException.class)
                .hasMessageContaining("not found");
    }
}

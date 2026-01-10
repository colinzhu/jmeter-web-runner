package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.JMeterConfig;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for JMeterManager
 * Tests path configuration, version detection, and availability verification
 * Requirements: 3.1, 3.2, 3.3
 */
class JMeterManagerUnitTest {

    private JMeterConfig config;
    private JMeterManager manager;
    private PersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        config = new JMeterConfig();
        persistenceService = mock(PersistenceService.class);
        // Mock persistence service to do nothing (no actual file I/O in tests)
        doNothing().when(persistenceService).save(anyString(), any());
        doNothing().when(persistenceService).delete(anyString());

        manager = new JMeterManagerImpl(config, persistenceService);
    }

    @Test
    void setInstallationPath_setsPathCorrectly() {
        // Given
        String testPath = "/opt/jmeter";

        // When
        manager.setInstallationPath(testPath);

        // Then
        assertThat(manager.isConfigured()).isTrue();
        assertThat(manager.getJMeterBinaryPath()).isNotNull();
    }

    @Test
    void setInstallationPath_throwsExceptionForNullPath() {
        // When/Then
        assertThatThrownBy(() -> manager.setInstallationPath(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Installation path cannot be null or empty");
    }

    @Test
    void setInstallationPath_throwsExceptionForEmptyPath() {
        // When/Then
        assertThatThrownBy(() -> manager.setInstallationPath(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Installation path cannot be null or empty");

        assertThatThrownBy(() -> manager.setInstallationPath("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Installation path cannot be null or empty");
    }

    @Test
    void getJMeterBinaryPath_returnsNullWhenNotConfigured() {
        // When
        String binaryPath = manager.getJMeterBinaryPath();

        // Then
        assertThat(binaryPath).isNull();
    }

    @Test
    void getJMeterBinaryPath_includesBinDirectory() {
        // Given
        String installationPath = "/opt/jmeter";
        manager.setInstallationPath(installationPath);

        // When
        String binaryPath = manager.getJMeterBinaryPath();

        // Then
        assertThat(binaryPath).contains("bin");
    }

    @Test
    void getJMeterBinaryPath_usesCorrectBinaryForWindows() {
        // Given
        String installationPath = "C:\\jmeter";
        manager.setInstallationPath(installationPath);

        // When
        String binaryPath = manager.getJMeterBinaryPath();
        String os = System.getProperty("os.name").toLowerCase();

        // Then
        if (os.contains("win")) {
            assertThat(binaryPath).endsWith("jmeter.bat");
        } else {
            assertThat(binaryPath).endsWith("jmeter");
        }
    }

    @Test
    void isConfigured_returnsFalseInitially() {
        // When/Then
        assertThat(manager.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_returnsTrueAfterSettingPath() {
        // Given
        manager.setInstallationPath("/opt/jmeter");

        // When/Then
        assertThat(manager.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_returnsFalseAfterClearing() {
        // Given
        manager.setInstallationPath("/opt/jmeter");
        assertThat(manager.isConfigured()).isTrue();

        // When
        manager.clearConfiguration();

        // Then
        assertThat(manager.isConfigured()).isFalse();
    }

    @Test
    void verifyInstallation_returnsUnavailableWhenNotConfigured() {
        // When
        JMeterInfo info = manager.verifyInstallation();

        // Then
        assertThat(info.isAvailable()).isFalse();
        assertThat(info.getError()).isEqualTo("JMeter is not configured");
    }

    @Test
    void verifyInstallation_returnsUnavailableWhenBinaryDoesNotExist() {
        // Given
        manager.setInstallationPath("/nonexistent/path");

        // When
        JMeterInfo info = manager.verifyInstallation();

        // Then
        assertThat(info.isAvailable()).isFalse();
        assertThat(info.getError()).contains("JMeter binary not found");
        assertThat(info.getPath()).isEqualTo("/nonexistent/path");
    }

    @Test
    void verifyInstallation_returnsAvailableForValidInstallation(@TempDir Path tempDir) throws IOException {
        // Given - Create a mock JMeter installation
        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);

        String os = System.getProperty("os.name").toLowerCase();
        String binaryName = os.contains("win") ? "jmeter.bat" : "jmeter";
        Path binaryPath = binDir.resolve(binaryName);

        // Create the binary file with version output
        if (os.contains("win")) {
            Files.writeString(binaryPath, "@echo off\necho Version 5.6.3\n");
        } else {
            Files.writeString(binaryPath, "#!/bin/sh\necho Version 5.6.3\n");
            // Set executable permission on Unix
            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(binaryPath);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(binaryPath, perms);
            } catch (UnsupportedOperationException e) {
                // Not a POSIX filesystem, skip
            }
        }

        manager.setInstallationPath(tempDir.toString());

        // When
        JMeterInfo info = manager.verifyInstallation();

        // Then
        assertThat(info.isAvailable()).isTrue();
        assertThat(info.getPath()).isEqualTo(tempDir.toString());
        assertThat(info.getError()).isNull();
        assertThat(info.getVersion()).isNotNull();
    }

    @Test
    void verifyInstallation_detectsVersion(@TempDir Path tempDir) throws IOException {
        // Given - Create a mock JMeter installation
        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);

        String os = System.getProperty("os.name").toLowerCase();
        String binaryName = os.contains("win") ? "jmeter.bat" : "jmeter";
        Path binaryPath = binDir.resolve(binaryName);

        // Create the binary file with version output
        if (os.contains("win")) {
            Files.writeString(binaryPath, "@echo off\necho Version 5.6.3\n");
        } else {
            Files.writeString(binaryPath, "#!/bin/sh\necho Version 5.6.3\n");
            // Set executable permission on Unix
            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(binaryPath);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(binaryPath, perms);
            } catch (UnsupportedOperationException e) {
                // Not a POSIX filesystem, skip
            }
        }

        manager.setInstallationPath(tempDir.toString());

        // When
        JMeterInfo info = manager.verifyInstallation();

        // Then
        assertThat(info.getVersion()).isEqualTo("5.6.3");
    }

    @Test
    void clearConfiguration_removesPathAndVersion() {
        // Given
        manager.setInstallationPath("/opt/jmeter");
        assertThat(manager.isConfigured()).isTrue();

        // When
        manager.clearConfiguration();

        // Then
        assertThat(manager.isConfigured()).isFalse();
        assertThat(manager.getJMeterBinaryPath()).isNull();
    }

    @Test
    void clearConfiguration_allowsReconfiguration() {
        // Given
        manager.setInstallationPath("/opt/jmeter-old");
        manager.clearConfiguration();

        // When
        manager.setInstallationPath("/opt/jmeter-new");

        // Then
        assertThat(manager.isConfigured()).isTrue();
        String binaryPath = manager.getJMeterBinaryPath();
        assertThat(binaryPath).isNotNull();
    }

    @Test
    void pathReplacement_updatesConfiguration() {
        // Given
        manager.setInstallationPath("/opt/jmeter-old");
        String oldBinaryPath = manager.getJMeterBinaryPath();

        // When
        manager.setInstallationPath("/opt/jmeter-new");
        String newBinaryPath = manager.getJMeterBinaryPath();

        // Then
        assertThat(newBinaryPath).isNotEqualTo(oldBinaryPath);
    }
}

package io.github.colinzhu.jmeter.webrunner.integration;

import io.github.colinzhu.jmeter.webrunner.exception.ExtractionException;
import io.github.colinzhu.jmeter.webrunner.exception.JMeterNotConfiguredException;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import io.github.colinzhu.jmeter.webrunner.service.ExtractionService;
import io.github.colinzhu.jmeter.webrunner.service.JMeterEngine;
import io.github.colinzhu.jmeter.webrunner.service.JMeterManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for end-to-end JMeter setup flow.
 * Tests complete flow: extract → configure → verify
 * Tests replacement flow with existing installation
 * Tests error recovery and cleanup
 * <p>
 * Requirements: 1.1, 2.1, 3.1, 7.1
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.location=test-uploads",
        "app.jmeter.installation-path="
})
class SetupEndToEndIntegrationTest {

    @TempDir
    Path tempDir;
    @Autowired
    private JMeterManager jmeterManager;
    @Autowired
    private ExtractionService extractionService;
    @Autowired
    private JMeterEngine jmeterEngine;
    private Path testStorageDir;
    private Path zipFilePath;

    @BeforeEach
    void setUp() throws IOException {
        // Create test storage directory
        testStorageDir = tempDir.resolve("test-uploads");
        Files.createDirectories(testStorageDir);

        // Clear any existing configuration
        jmeterManager.clearConfiguration();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test files
        if (Files.exists(testStorageDir)) {
            deleteDirectory(testStorageDir);
        }

        // Clean up ZIP file
        if (zipFilePath != null && Files.exists(zipFilePath)) {
            Files.deleteIfExists(zipFilePath);
        }

        // Clear configuration
        jmeterManager.clearConfiguration();
    }

    /**
     * Test complete end-to-end flow: extract → configure → verify
     * Requirements: 1.1, 2.1, 3.1, 7.1
     */
    @Test
    void testCompleteEndToEndFlow() throws IOException {
        // Step 1: Verify initial status - JMeter not configured
        assertFalse(jmeterManager.isConfigured());
        assertNull(jmeterManager.getJMeterBinaryPath());

        // Step 2: Create a valid JMeter ZIP file
        zipFilePath = createValidJMeterZipFile();

        // Step 3: Extract JMeter distribution
        Path jmeterInstallPath = extractionService.extractJMeterDistribution(
                zipFilePath,
                testStorageDir
        );

        assertNotNull(jmeterInstallPath);
        assertTrue(Files.exists(jmeterInstallPath));
        assertTrue(Files.isDirectory(jmeterInstallPath));

        // Step 4: Configure JMeter Manager with the installation path
        jmeterManager.setInstallationPath(jmeterInstallPath.toString());

        // Step 5: Verify configuration
        assertTrue(jmeterManager.isConfigured());
        assertNotNull(jmeterManager.getJMeterBinaryPath());

        // Step 6: Verify installation
        JMeterInfo info = jmeterManager.verifyInstallation();
        assertNotNull(info);
        assertTrue(info.isAvailable());
        assertNotNull(info.getVersion());
        assertEquals(jmeterInstallPath.toString(), info.getPath());

        // Step 7: Verify JMeter binary exists
        String binaryPath = jmeterManager.getJMeterBinaryPath();
        assertTrue(Files.exists(Paths.get(binaryPath)));

        // Step 8: Verify JMeter Engine can detect unconfigured state
        jmeterManager.clearConfiguration();
        assertThrows(JMeterNotConfiguredException.class, () -> {
            jmeterEngine.executeTest("dummy.jmx", "test-exec-1");
        });
    }

    /**
     * Test replacement flow with existing installation
     * Requirements: 1.1, 2.1, 3.1
     */
    @Test
    void testReplacementFlow() throws IOException {
        // Step 1: Extract and configure first JMeter installation
        zipFilePath = createValidJMeterZipFile();
        Path firstInstallPath = extractionService.extractJMeterDistribution(
                zipFilePath,
                testStorageDir
        );
        jmeterManager.setInstallationPath(firstInstallPath.toString());

        assertTrue(jmeterManager.isConfigured());
        String firstBinaryPath = jmeterManager.getJMeterBinaryPath();
        assertNotNull(firstBinaryPath);

        // Step 2: Clean up first ZIP
        Files.deleteIfExists(zipFilePath);

        // Step 3: Extract and configure second JMeter installation (replacement)
        zipFilePath = createValidJMeterZipFile();
        Path secondInstallPath = extractionService.extractJMeterDistribution(
                zipFilePath,
                testStorageDir
        );
        jmeterManager.setInstallationPath(secondInstallPath.toString());

        // Step 4: Verify new installation replaced the old one
        assertTrue(jmeterManager.isConfigured());
        String secondBinaryPath = jmeterManager.getJMeterBinaryPath();
        assertNotNull(secondBinaryPath);
        assertTrue(Files.exists(Paths.get(secondBinaryPath)));

        // Step 5: Verify installation is available
        JMeterInfo info = jmeterManager.verifyInstallation();
        assertTrue(info.isAvailable());
        assertNotNull(info.getVersion());
    }

    /**
     * Test error recovery and cleanup on invalid ZIP
     * Requirements: 1.1, 2.1
     */
    @Test
    void testErrorRecoveryAndCleanup() throws IOException {
        // Step 1: Attempt to extract invalid ZIP (missing bin directory)
        zipFilePath = createInvalidJMeterZipFile();

        assertThrows(ExtractionException.class, () -> {
            extractionService.extractJMeterDistribution(zipFilePath, testStorageDir);
        });

        // Step 2: Verify JMeter is still not configured
        assertFalse(jmeterManager.isConfigured());
        assertNull(jmeterManager.getJMeterBinaryPath());

        // Step 3: Verify no partial installation exists in target directory
        Path jmeterDir = testStorageDir.resolve("jmeter");
        assertFalse(Files.exists(jmeterDir));

        // Step 4: Clean up invalid ZIP
        Files.deleteIfExists(zipFilePath);

        // Step 5: Extract valid ZIP after error to verify recovery
        zipFilePath = createValidJMeterZipFile();
        Path validInstallPath = extractionService.extractJMeterDistribution(
                zipFilePath,
                testStorageDir
        );

        assertNotNull(validInstallPath);
        assertTrue(Files.exists(validInstallPath));

        // Step 6: Configure and verify successful recovery
        jmeterManager.setInstallationPath(validInstallPath.toString());
        assertTrue(jmeterManager.isConfigured());
        assertNotNull(jmeterManager.getJMeterBinaryPath());

        JMeterInfo info = jmeterManager.verifyInstallation();
        assertTrue(info.isAvailable());
    }

    /**
     * Test ZIP structure validation
     * Requirements: 1.1, 2.1
     */
    @Test
    void testZipStructureValidation() throws IOException {
        // Test 1: Invalid ZIP - missing bin directory
        zipFilePath = createInvalidJMeterZipFile();

        ExtractionException exception1 = assertThrows(ExtractionException.class, () -> {
            extractionService.extractJMeterDistribution(zipFilePath, testStorageDir);
        });
        assertTrue(exception1.getMessage().contains("Invalid JMeter distribution"));

        Files.deleteIfExists(zipFilePath);

        // Test 2: Valid ZIP - should extract successfully
        zipFilePath = createValidJMeterZipFile();
        Path installPath = extractionService.extractJMeterDistribution(zipFilePath, testStorageDir);

        assertNotNull(installPath);
        assertTrue(Files.exists(installPath));
        assertTrue(Files.isDirectory(installPath.resolve("bin")));

        // Verify executable exists
        String os = System.getProperty("os.name").toLowerCase();
        String execName = os.contains("win") ? "jmeter.bat" : "jmeter";
        assertTrue(Files.exists(installPath.resolve("bin").resolve(execName)));
    }

    /**
     * Test configuration persistence
     * Requirements: 3.1
     */
    @Test
    void testConfigurationPersistence() throws IOException {
        // Step 1: Extract and configure JMeter
        zipFilePath = createValidJMeterZipFile();
        Path installPath = extractionService.extractJMeterDistribution(zipFilePath, testStorageDir);
        jmeterManager.setInstallationPath(installPath.toString());

        // Step 2: Verify configuration is set
        assertTrue(jmeterManager.isConfigured());
        String configuredPath = jmeterManager.getJMeterBinaryPath();
        assertNotNull(configuredPath);

        // Step 3: Verify installation info
        JMeterInfo info = jmeterManager.verifyInstallation();
        assertTrue(info.isAvailable());
        assertNotNull(info.getVersion());
        assertEquals(installPath.toString(), info.getPath());

        // Step 4: Clear and verify cleared
        jmeterManager.clearConfiguration();
        assertFalse(jmeterManager.isConfigured());
        assertNull(jmeterManager.getJMeterBinaryPath());
    }

    /**
     * Helper method to create a valid JMeter ZIP file for testing
     */
    private Path createValidJMeterZipFile() throws IOException {
        Path zipPath = tempDir.resolve("apache-jmeter-5.6.3.zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // Create apache-jmeter-5.6.3 directory structure
            String rootDir = "apache-jmeter-5.6.3/";

            // Create bin directory
            zos.putNextEntry(new ZipEntry(rootDir + "bin/"));
            zos.closeEntry();

            // Create jmeter executable (Unix)
            zos.putNextEntry(new ZipEntry(rootDir + "bin/jmeter"));
            zos.write("#!/bin/sh\necho Version 5.6.3\n".getBytes());
            zos.closeEntry();

            // Create jmeter.bat (Windows)
            zos.putNextEntry(new ZipEntry(rootDir + "bin/jmeter.bat"));
            zos.write("@echo off\necho Version 5.6.3\n".getBytes());
            zos.closeEntry();

            // Create lib directory
            zos.putNextEntry(new ZipEntry(rootDir + "lib/"));
            zos.closeEntry();

            // Create a dummy jar file
            zos.putNextEntry(new ZipEntry(rootDir + "lib/ApacheJMeter.jar"));
            zos.write("fake jar content".getBytes());
            zos.closeEntry();
        }

        return zipPath;
    }

    /**
     * Helper method to create an invalid JMeter ZIP file (missing bin directory)
     */
    private Path createInvalidJMeterZipFile() throws IOException {
        Path zipPath = tempDir.resolve("invalid-jmeter.zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // Create a ZIP without proper JMeter structure
            zos.putNextEntry(new ZipEntry("some-folder/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("some-folder/readme.txt"));
            zos.write("This is not a JMeter distribution".getBytes());
            zos.closeEntry();
        }

        return zipPath;
    }

    /**
     * Helper method to recursively delete a directory
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors in tests
                    }
                });
    }
}

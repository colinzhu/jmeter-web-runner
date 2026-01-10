package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.exception.ExtractionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ExtractionService.
 */
class ExtractionServiceUnitTest {

    @TempDir
    Path tempDir;
    private ExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new ExtractionService();
    }

    @AfterEach
    void tearDown() {
        // Cleanup is handled by @TempDir
    }

    @Test
    void extractValidJMeterZip_Success() throws IOException {
        // Create a valid JMeter ZIP
        Path zipFile = createValidJMeterZip("apache-jmeter-5.6");
        Path targetDir = tempDir.resolve("target");

        // Extract
        Path result = extractionService.extractJMeterDistribution(zipFile, targetDir);

        // Verify
        assertThat(result).isNotNull();
        assertThat(Files.exists(result)).isTrue();
        assertThat(Files.exists(result.resolve("bin"))).isTrue();
        assertThat(Files.exists(result.resolve("bin/jmeter")) ||
                Files.exists(result.resolve("bin/jmeter.bat"))).isTrue();
    }

    @Test
    void extractZipWithoutBinDirectory_ThrowsException() throws IOException {
        // Create ZIP without bin directory
        Path zipFile = tempDir.resolve("invalid.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            addZipEntry(zos, "lib/jmeter.jar");
            addZipEntry(zos, "docs/readme.txt");
        }

        Path targetDir = tempDir.resolve("target");

        // Verify exception is thrown
        assertThatThrownBy(() ->
                extractionService.extractJMeterDistribution(zipFile, targetDir)
        ).isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Invalid JMeter distribution");
    }

    @Test
    void extractZipWithoutExecutable_ThrowsException() throws IOException {
        // Create ZIP with bin directory but no executable
        Path zipFile = tempDir.resolve("invalid.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            addZipEntry(zos, "bin/");
            addZipEntry(zos, "bin/readme.txt");
            addZipEntry(zos, "lib/jmeter.jar");
        }

        Path targetDir = tempDir.resolve("target");

        // Verify exception is thrown
        assertThatThrownBy(() ->
                extractionService.extractJMeterDistribution(zipFile, targetDir)
        ).isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Invalid JMeter distribution");
    }

    @Test
    void extractNestedJMeterZip_Success() throws IOException {
        // Create a nested JMeter ZIP (common structure)
        Path zipFile = createValidJMeterZip("apache-jmeter-5.6");
        Path targetDir = tempDir.resolve("target");

        // Extract
        Path result = extractionService.extractJMeterDistribution(zipFile, targetDir);

        // Verify
        assertThat(result).isNotNull();
        assertThat(Files.exists(result)).isTrue();
        assertThat(result.getFileName().toString()).isEqualTo("jmeter");
    }

    @Test
    void extractToExistingTarget_ReplacesExisting() throws IOException {
        // Create first installation
        Path zipFile1 = createValidJMeterZip("apache-jmeter-5.5");
        Path targetDir = tempDir.resolve("target");
        Path result1 = extractionService.extractJMeterDistribution(zipFile1, targetDir);

        // Create marker file in first installation
        Path markerFile = result1.resolve("marker.txt");
        Files.writeString(markerFile, "old installation");

        // Create second installation
        Path zipFile2 = createValidJMeterZip("apache-jmeter-5.6");
        Path result2 = extractionService.extractJMeterDistribution(zipFile2, targetDir);

        // Verify old installation was replaced
        assertThat(Files.exists(result2)).isTrue();
        assertThat(Files.exists(markerFile)).isFalse();
    }

    @Test
    void extractFailure_CleansUpTemporaryFiles() throws IOException {
        // Create invalid ZIP
        Path zipFile = tempDir.resolve("invalid.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            addZipEntry(zos, "invalid/structure.txt");
        }

        Path targetDir = tempDir.resolve("target");

        // Attempt extraction
        try {
            extractionService.extractJMeterDistribution(zipFile, targetDir);
            fail("Should have thrown ExtractionException");
        } catch (ExtractionException e) {
            // Expected
        }

        // Verify no partial extraction remains in target
        assertThat(Files.exists(targetDir.resolve("jmeter"))).isFalse();
    }

    // Helper methods

    private Path createValidJMeterZip(String rootDirName) throws IOException {
        Path zipFile = tempDir.resolve(rootDirName + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Add JMeter structure
            addZipEntry(zos, rootDirName + "/bin/");
            addZipEntry(zos, rootDirName + "/bin/jmeter");
            addZipEntry(zos, rootDirName + "/bin/jmeter.bat");
            addZipEntry(zos, rootDirName + "/lib/");
            addZipEntry(zos, rootDirName + "/lib/jmeter.jar");
        }

        return zipFile;
    }

    private void addZipEntry(ZipOutputStream zos, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);

        // Write some dummy content for files (not directories)
        if (!entryName.endsWith("/")) {
            zos.write("dummy content".getBytes());
        }

        zos.closeEntry();
    }
}

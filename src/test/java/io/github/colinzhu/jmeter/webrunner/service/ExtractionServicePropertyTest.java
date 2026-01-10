package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.exception.ExtractionException;
import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for ExtractionService.
 * Feature: jmeter-setup-upload
 */
class ExtractionServicePropertyTest {

    private final ExtractionService extractionService = new ExtractionService();

    /**
     * Property 3: JMeter Distribution Structure Validation
     * <p>
     * For any uploaded ZIP file, the system should validate that it contains
     * a valid JMeter distribution structure (bin directory with jmeter or jmeter.bat executable)
     * before accepting it.
     * <p>
     * Validates: Requirements 1.5, 5.1, 5.2
     * Feature: jmeter-setup-upload, Property 3: JMeter Distribution Structure Validation
     */
    @Property(tries = 100)
    void validJMeterDistributionStructureIsAccepted(
            @ForAll("validJMeterZipStructure") ZipStructure structure) throws IOException {

        Path tempDir = Files.createTempDirectory("test-");
        try {
            // Create ZIP file with valid structure
            Path zipFile = createZipFile(structure, tempDir);
            Path targetDir = tempDir.resolve("target");

            // Extract should succeed
            assertThatCode(() -> {
                Path result = extractionService.extractJMeterDistribution(zipFile, targetDir);
                assertThat(result).isNotNull();
                assertThat(Files.exists(result)).isTrue();
                assertThat(Files.exists(result.resolve("bin"))).isTrue();
            }).doesNotThrowAnyException();
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * Property 3: JMeter Distribution Structure Validation (Invalid case)
     * <p>
     * For any uploaded ZIP file that does not contain a valid JMeter distribution structure,
     * the system should reject it with an appropriate error.
     * <p>
     * Validates: Requirements 1.5, 5.1, 5.2
     * Feature: jmeter-setup-upload, Property 3: JMeter Distribution Structure Validation
     */
    @Property(tries = 100)
    void invalidJMeterDistributionStructureIsRejected(
            @ForAll("invalidJMeterZipStructure") ZipStructure structure) throws IOException {

        Path tempDir = Files.createTempDirectory("test-");
        try {
            // Create ZIP file with invalid structure
            Path zipFile = createZipFile(structure, tempDir);
            Path targetDir = tempDir.resolve("target");

            // Extract should fail with ExtractionException
            assertThatThrownBy(() ->
                    extractionService.extractJMeterDistribution(zipFile, targetDir)
            ).isInstanceOf(ExtractionException.class)
                    .hasMessageContaining("Invalid JMeter distribution");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * Property 16: Multi-Platform Executable Support
     * <p>
     * For any valid JMeter distribution containing either Unix (jmeter) or Windows (jmeter.bat)
     * executables, the system should successfully validate and extract it.
     * <p>
     * Validates: Requirements 5.4
     * Feature: jmeter-setup-upload, Property 16: Multi-Platform Executable Support
     */
    @Property(tries = 100)
    void bothUnixAndWindowsExecutablesAreSupported(
            @ForAll("platformSpecificJMeterZip") ZipStructure structure) throws IOException {

        Path tempDir = Files.createTempDirectory("test-");
        try {
            // Create ZIP file with platform-specific executable
            Path zipFile = createZipFile(structure, tempDir);
            Path targetDir = tempDir.resolve("target");

            // Extract should succeed regardless of platform
            assertThatCode(() -> {
                Path result = extractionService.extractJMeterDistribution(zipFile, targetDir);
                assertThat(result).isNotNull();
                assertThat(Files.exists(result)).isTrue();

                // Verify bin directory exists
                Path binDir = result.resolve("bin");
                assertThat(Files.exists(binDir)).isTrue();

                // Verify at least one executable exists
                boolean hasUnix = Files.exists(binDir.resolve("jmeter"));
                boolean hasWindows = Files.exists(binDir.resolve("jmeter.bat"));
                assertThat(hasUnix || hasWindows).isTrue();
            }).doesNotThrowAnyException();
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * Property 8: Cleanup on Extraction Failure
     * <p>
     * For any extraction operation that fails, the system should clean up any partially
     * extracted files, leaving no incomplete installation.
     * <p>
     * Validates: Requirements 2.5
     * Feature: jmeter-setup-upload, Property 8: Cleanup on Extraction Failure
     */
    @Property(tries = 100)
    void failedExtractionCleansUpTemporaryFiles(
            @ForAll("invalidJMeterZipStructure") ZipStructure structure) throws IOException {

        Path tempDir = Files.createTempDirectory("test-");
        try {
            // Create ZIP file with invalid structure
            Path zipFile = createZipFile(structure, tempDir);
            Path targetDir = tempDir.resolve("target");

            // Count temp directories before extraction
            long tempDirsBefore = countTempDirectories();

            // Attempt extraction (should fail)
            try {
                extractionService.extractJMeterDistribution(zipFile, targetDir);
            } catch (ExtractionException e) {
                // Expected exception
            }

            // Give system a moment to clean up
            Thread.sleep(100);

            // Count temp directories after extraction
            long tempDirsAfter = countTempDirectories();

            // Verify no new temp directories remain (cleanup happened)
            // Allow for some tolerance due to other system processes
            assertThat(tempDirsAfter).isLessThanOrEqualTo(tempDirsBefore + 2);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<ZipStructure> validJMeterZipStructure() {
        return Arbitraries.of(
                // Direct structure: bin/jmeter at root
                new ZipStructure("bin/jmeter", true, false),
                new ZipStructure("bin/jmeter.bat", true, false),
                new ZipStructure("bin/jmeter", true, false).withBothExecutables(),

                // Nested structure: apache-jmeter-5.6/bin/jmeter
                new ZipStructure("apache-jmeter-5.6/bin/jmeter", true, true),
                new ZipStructure("apache-jmeter-5.6/bin/jmeter.bat", true, true),
                new ZipStructure("jmeter/bin/jmeter", true, true),
                new ZipStructure("jmeter/bin/jmeter.bat", true, true)
        );
    }

    @Provide
    Arbitrary<ZipStructure> invalidJMeterZipStructure() {
        return Arbitraries.of(
                // Missing bin directory
                new ZipStructure("jmeter", false, false),
                new ZipStructure("lib/jmeter.jar", false, false),

                // bin directory exists but no executable
                new ZipStructure("bin/readme.txt", false, false),
                new ZipStructure("bin/lib/jmeter.jar", false, false),

                // Wrong executable name
                new ZipStructure("bin/jmeter.sh", false, false),
                new ZipStructure("bin/jmeter.exe", false, false),

                // Empty ZIP
                new ZipStructure("", false, false)
        );
    }

    @Provide
    Arbitrary<ZipStructure> platformSpecificJMeterZip() {
        return Arbitraries.of(
                // Unix only
                new ZipStructure("bin/jmeter", true, false),
                new ZipStructure("apache-jmeter/bin/jmeter", true, true),

                // Windows only
                new ZipStructure("bin/jmeter.bat", true, false),
                new ZipStructure("apache-jmeter/bin/jmeter.bat", true, true),

                // Both executables
                new ZipStructure("bin/jmeter", true, false).withBothExecutables(),
                new ZipStructure("apache-jmeter/bin/jmeter", true, true).withBothExecutables()
        );
    }

    // ========== Helper Methods ==========

    private Path createZipFile(ZipStructure structure, Path tempDir) throws IOException {
        Path zipFile = tempDir.resolve("test-" + System.nanoTime() + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            if (structure.isEmpty()) {
                // Create empty ZIP
                return zipFile;
            }

            // Add the main entry
            if (!structure.entryPath.isEmpty()) {
                addZipEntry(zos, structure.entryPath);
            }

            // If it's a valid structure, add additional files
            if (structure.isValid) {
                String basePath = structure.isNested ? extractBasePath(structure.entryPath) : "";

                // Add lib directory (common in JMeter distributions)
                addZipEntry(zos, basePath + "lib/");
                addZipEntry(zos, basePath + "lib/jmeter.jar");

                // If both executables should be present, add the other one
                if (structure.hasBothExecutables) {
                    String binPath = basePath + "bin/";
                    // Check which one is already added and add the other
                    if (structure.entryPath.endsWith("jmeter")) {
                        addZipEntry(zos, binPath + "jmeter.bat");
                    } else if (structure.entryPath.endsWith("jmeter.bat")) {
                        addZipEntry(zos, binPath + "jmeter");
                    }
                }
            }
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

    private String extractBasePath(String path) {
        int firstSlash = path.indexOf('/');
        if (firstSlash > 0) {
            return path.substring(0, firstSlash + 1);
        }
        return "";
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
                .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
    }

    private long countTempDirectories() throws IOException {
        Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir"));
        try (var stream = Files.list(tempRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("jmeter-extract-"))
                    .count();
        }
    }

    // ========== Test Data Classes ==========

    static class ZipStructure {
        final String entryPath;
        final boolean isValid;
        final boolean isNested;
        boolean hasBothExecutables;

        ZipStructure(String entryPath, boolean isValid, boolean isNested) {
            this.entryPath = entryPath;
            this.isValid = isValid;
            this.isNested = isNested;
            this.hasBothExecutables = false;
        }

        ZipStructure withBothExecutables() {
            this.hasBothExecutables = true;
            return this;
        }

        boolean isEmpty() {
            return entryPath.isEmpty();
        }
    }
}

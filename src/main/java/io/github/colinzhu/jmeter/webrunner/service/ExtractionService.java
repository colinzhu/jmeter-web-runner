package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.exception.ExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Service for extracting and validating JMeter distribution ZIP files.
 */
@Service
@Slf4j
public class ExtractionService {

    private static final String BIN_DIR = "bin";
    private static final String JMETER_UNIX = "jmeter";
    private static final String JMETER_WINDOWS = "jmeter.bat";

    /**
     * Extract and validate a JMeter distribution ZIP file.
     *
     * @param zipFilePath     Path to the ZIP file
     * @param targetDirectory Target directory for extraction
     * @return Path to the extracted JMeter root directory
     * @throws ExtractionException if extraction or validation fails
     */
    public Path extractJMeterDistribution(Path zipFilePath, Path targetDirectory) {
        Path tempDir = null;

        try {
            // Create temporary extraction directory
            tempDir = Files.createTempDirectory("jmeter-extract-");
            log.info("Created temporary extraction directory: {}", tempDir);

            // Extract ZIP to temporary directory
            extractZipFile(zipFilePath, tempDir);

            // Detect JMeter root directory in extracted files
            Path jmeterRoot = detectJMeterRoot(tempDir);
            if (jmeterRoot == null) {
                throw new ExtractionException(
                        "Invalid JMeter distribution. The ZIP file must contain a bin directory with jmeter executable."
                );
            }

            // Validate JMeter distribution structure
            validateJMeterStructure(jmeterRoot);

            // Ensure target directory exists
            Files.createDirectories(targetDirectory);

            // Move extracted files to final location
            Path finalLocation = moveToFinalLocation(jmeterRoot, targetDirectory);

            // Set executable permissions for Unix systems
            setExecutablePermissions(finalLocation);

            // Clean up temporary directory
            deleteDirectory(tempDir);

            log.info("Successfully extracted JMeter distribution to: {}", finalLocation);
            return finalLocation;

        } catch (ExtractionException e) {
            // Clean up on failure
            if (tempDir != null) {
                try {
                    deleteDirectory(tempDir);
                } catch (IOException cleanupEx) {
                    log.error("Failed to clean up temporary directory: {}", tempDir, cleanupEx);
                }
            }
            throw e;
        } catch (IOException e) {
            // Clean up on failure
            if (tempDir != null) {
                try {
                    deleteDirectory(tempDir);
                } catch (IOException cleanupEx) {
                    log.error("Failed to clean up temporary directory: {}", tempDir, cleanupEx);
                }
            }
            throw new ExtractionException("Failed to extract JMeter distribution", e);
        }
    }

    /**
     * Extract a ZIP file to a target directory.
     */
    private void extractZipFile(Path zipFilePath, Path targetDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = targetDir.resolve(entry.getName());

                // Prevent zip slip vulnerability
                if (!entryPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("Invalid ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /**
     * Detect the JMeter root directory within extracted files.
     * The root directory is identified by the presence of a bin/ subdirectory
     * containing the jmeter executable.
     */
    private Path detectJMeterRoot(Path extractedDir) throws IOException {
        // Check if extractedDir itself is the root
        if (isJMeterRoot(extractedDir)) {
            return extractedDir;
        }

        // Search one level deep for JMeter root
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extractedDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && isJMeterRoot(entry)) {
                    return entry;
                }
            }
        }

        return null;
    }

    /**
     * Check if a directory is a JMeter root directory.
     */
    private boolean isJMeterRoot(Path dir) {
        Path binDir = dir.resolve(BIN_DIR);
        if (!Files.isDirectory(binDir)) {
            return false;
        }

        // Check for either Unix or Windows executable
        Path unixExec = binDir.resolve(JMETER_UNIX);
        Path windowsExec = binDir.resolve(JMETER_WINDOWS);

        return Files.exists(unixExec) || Files.exists(windowsExec);
    }

    /**
     * Validate that the JMeter distribution has the required structure.
     */
    private void validateJMeterStructure(Path jmeterRoot) {
        Path binDir = jmeterRoot.resolve(BIN_DIR);

        if (!Files.isDirectory(binDir)) {
            throw new ExtractionException(
                    "Invalid JMeter distribution. Missing required bin directory."
            );
        }

        Path unixExec = binDir.resolve(JMETER_UNIX);
        Path windowsExec = binDir.resolve(JMETER_WINDOWS);

        if (!Files.exists(unixExec) && !Files.exists(windowsExec)) {
            throw new ExtractionException(
                    "Invalid JMeter distribution. JMeter executable not found in bin directory."
            );
        }

        log.info("JMeter distribution structure validated successfully");
    }

    /**
     * Move extracted files to final location.
     * If target already exists, it will be replaced.
     */
    private Path moveToFinalLocation(Path source, Path targetParent) throws IOException {
        // Create a unique directory name based on "jmeter"
        Path target = targetParent.resolve("jmeter");

        // If target exists, remove it first
        if (Files.exists(target)) {
            log.info("Removing existing JMeter installation at: {}", target);
            deleteDirectory(target);
        }

        // On Windows, Files.move() can fail for directories
        // Use copy + delete approach instead for better cross-platform compatibility
        try {
            copyDirectory(source, target);
            deleteDirectory(source);
            log.info("Successfully moved JMeter installation to: {}", target);
        } catch (IOException e) {
            // Clean up partial copy on failure
            if (Files.exists(target)) {
                try {
                    deleteDirectory(target);
                } catch (IOException cleanupEx) {
                    log.error("Failed to clean up partial installation at: {}", target, cleanupEx);
                }
            }
            throw new IOException("Failed to move JMeter installation to final location", e);
        }

        return target;
    }

    /**
     * Recursively copy a directory and all its contents.
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy: " + sourcePath, e);
            }
        });
    }

    /**
     * Set executable permissions on JMeter binaries for Unix systems.
     */
    private void setExecutablePermissions(Path jmeterRoot) {
        String os = System.getProperty("os.name").toLowerCase();

        // Only set permissions on Unix-like systems
        if (os.contains("win")) {
            log.debug("Windows system detected, skipping permission setting");
            return;
        }

        Path binDir = jmeterRoot.resolve(BIN_DIR);
        Path jmeterExec = binDir.resolve(JMETER_UNIX);

        if (Files.exists(jmeterExec)) {
            try {
                Set<PosixFilePermission> perms = new HashSet<>();
                perms.add(PosixFilePermission.OWNER_READ);
                perms.add(PosixFilePermission.OWNER_WRITE);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_READ);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_READ);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);

                Files.setPosixFilePermissions(jmeterExec, perms);
                log.info("Set executable permissions on: {}", jmeterExec);
            } catch (IOException e) {
                log.warn("Failed to set executable permissions on: {}", jmeterExec, e);
                // Don't throw exception, as this is not critical
            } catch (UnsupportedOperationException e) {
                log.debug("POSIX permissions not supported on this file system");
            }
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

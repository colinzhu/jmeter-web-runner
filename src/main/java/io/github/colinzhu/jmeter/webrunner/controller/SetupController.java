package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.exception.ExtractionException;
import io.github.colinzhu.jmeter.webrunner.exception.InvalidFileException;
import io.github.colinzhu.jmeter.webrunner.model.ExecutionStatus;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import io.github.colinzhu.jmeter.webrunner.model.SetupStatus;
import io.github.colinzhu.jmeter.webrunner.model.UploadResponse;
import io.github.colinzhu.jmeter.webrunner.repository.ExecutionRepository;
import io.github.colinzhu.jmeter.webrunner.service.ExtractionService;
import io.github.colinzhu.jmeter.webrunner.service.JMeterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
@Slf4j
public class SetupController {

    private static final long MAX_FILE_SIZE = 200 * 1024 * 1024; // 200MB
    private final JMeterManager jmeterManager;
    private final ExtractionService extractionService;
    private final ExecutionRepository executionRepository;
    
    @Value("${app.storage.location:uploads}")
    private String storageLocation;

    /**
     * Get current JMeter setup status
     */
    @GetMapping("/status")
    public ResponseEntity<SetupStatus> getStatus() {
        log.info("Received request to get JMeter setup status");

        if (!jmeterManager.isConfigured()) {
            SetupStatus status = SetupStatus.builder()
                    .configured(false)
                    .available(false)
                    .build();
            return ResponseEntity.ok(status);
        }

        JMeterInfo info = jmeterManager.verifyInstallation();

        SetupStatus status = SetupStatus.builder()
                .configured(true)
                .version(info.getVersion())
                .path(info.getPath())
                .available(info.isAvailable())
                .error(info.getError())
                .build();

        log.info("JMeter status: configured={}, available={}", status.isConfigured(), status.isAvailable());

        return ResponseEntity.ok(status);
    }

    /**
     * Upload and configure JMeter distribution
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadJMeter(@RequestParam("file") MultipartFile file) {
        log.info("Received JMeter upload request: {}", file.getOriginalFilename());

        Path zipFilePath = null;

        try {
            // Validate file extension
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
                throw new InvalidFileException("Invalid file type. Only .zip files are accepted.");
            }

            // Validate file size
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new InvalidFileException("File size exceeds maximum limit of 200MB.");
            }

            // Store the uploaded ZIP file temporarily (directly, not using FileStorageService)
            Path uploadDir = Paths.get(storageLocation);
            Files.createDirectories(uploadDir);

            String tempFileName = "jmeter-upload-" + System.currentTimeMillis() + ".zip";
            zipFilePath = uploadDir.resolve(tempFileName);

            // Save the uploaded file
            Files.copy(file.getInputStream(), zipFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Extract and validate JMeter distribution
            Path jmeterInstallPath = extractionService.extractJMeterDistribution(
                    zipFilePath,
                    uploadDir
            );

            // Configure JMeter Manager with the installation path
            jmeterManager.setInstallationPath(jmeterInstallPath.toString());

            // Verify installation and get version
            JMeterInfo info = jmeterManager.verifyInstallation();

            // Clean up the uploaded ZIP file
            try {
                Files.deleteIfExists(zipFilePath);
            } catch (IOException e) {
                log.warn("Failed to clean up uploaded ZIP file: {}", zipFilePath, e);
            }

            UploadResponse response = UploadResponse.builder()
                    .success(true)
                    .version(info.getVersion())
                    .path(jmeterInstallPath.toString())
                    .message("JMeter distribution uploaded and configured successfully")
                    .build();

            log.info("JMeter uploaded successfully: version={}, path={}", info.getVersion(), jmeterInstallPath);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (InvalidFileException e) {
            log.error("Invalid file upload: {}", e.getMessage());
            UploadResponse response = UploadResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (ExtractionException e) {
            log.error("Extraction failed: {}", e.getMessage());

            // Clean up ZIP file on extraction failure
            if (zipFilePath != null) {
                try {
                    Files.deleteIfExists(zipFilePath);
                } catch (IOException cleanupEx) {
                    log.warn("Failed to clean up ZIP file after extraction failure", cleanupEx);
                }
            }

            UploadResponse response = UploadResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (Exception e) {
            log.error("Unexpected error during JMeter upload", e);

            // Clean up ZIP file on unexpected error
            if (zipFilePath != null) {
                try {
                    Files.deleteIfExists(zipFilePath);
                } catch (IOException cleanupEx) {
                    log.warn("Failed to clean up ZIP file after error", cleanupEx);
                }
            }

            UploadResponse response = UploadResponse.builder()
                    .success(false)
                    .message("Failed to upload and configure JMeter: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Remove current JMeter installation
     */
    @DeleteMapping("/installation")
    public ResponseEntity<Map<String, Object>> deleteInstallation() {
        log.info("Received request to delete JMeter installation");

        try {
            // Check for active executions
            boolean hasActiveExecutions = executionRepository.findAll().stream()
                    .anyMatch(execution ->
                            execution.getStatus() == ExecutionStatus.QUEUED ||
                                    execution.getStatus() == ExecutionStatus.RUNNING
                    );

            if (hasActiveExecutions) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Cannot replace JMeter while test executions are active. Please wait for executions to complete.");

                log.warn("Deletion blocked due to active executions");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            // Get current installation path
            String installPath = jmeterManager.isConfigured() ?
                    Paths.get(jmeterManager.getJMeterBinaryPath()).getParent().getParent().toString() : null;

            // Clear configuration
            jmeterManager.clearConfiguration();

            // Remove installation directory if it exists
            if (installPath != null) {
                Path installDir = Paths.get(installPath);
                if (Files.exists(installDir)) {
                    deleteDirectory(installDir);
                    log.info("Removed JMeter installation directory: {}", installDir);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "JMeter installation removed successfully");

            log.info("JMeter installation deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to delete JMeter installation", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to remove JMeter installation: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Verify current JMeter installation
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyInstallation() {
        log.info("Received request to verify JMeter installation");

        if (!jmeterManager.isConfigured()) {
            Map<String, Object> response = new HashMap<>();
            response.put("configured", false);
            response.put("error", "JMeter is not configured");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        JMeterInfo info = jmeterManager.verifyInstallation();

        Map<String, Object> response = new HashMap<>();
        response.put("configured", true);
        response.put("available", info.isAvailable());
        response.put("version", info.getVersion());
        response.put("path", info.getPath());

        if (!info.isAvailable()) {
            response.put("error", info.getError());
        }

        log.info("JMeter verification result: available={}, version={}", info.isAvailable(), info.getVersion());

        return ResponseEntity.ok(response);
    }

    /**
     * Recursively delete a directory
     */
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
                        log.warn("Failed to delete: {}", path, e);
                    }
                });
    }
}

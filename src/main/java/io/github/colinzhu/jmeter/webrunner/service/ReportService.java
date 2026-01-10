package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.exception.ResourceNotFoundException;
import io.github.colinzhu.jmeter.webrunner.model.Report;
import io.github.colinzhu.jmeter.webrunner.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
    private final ReportRepository reportRepository;
    private final StorageConfig storageConfig;

    /**
     * Retrieve the HTML report for viewing in browser
     */
    public Resource getReportHtml(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        Path reportPath = Paths.get(report.getPath());
        Path indexPath = reportPath.resolve("index.html");

        if (!Files.exists(indexPath)) {
            throw new ResourceNotFoundException("Report HTML not found");
        }

        try {
            Resource resource = new UrlResource(indexPath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Report HTML not readable");
            }
        } catch (IOException e) {
            log.error("Error reading report HTML: {}", e.getMessage());
            throw new ResourceNotFoundException("Failed to read report HTML");
        }
    }

    /**
     * Package the report directory as a ZIP archive for download
     * Reads directly from storage/reports/{id} folder
     */
    public File packageReportAsZip(String reportId) {
        Path reportDir = Paths.get(storageConfig.getReportDir());
        Path reportPath = reportDir.resolve(reportId);
        
        if (!Files.exists(reportPath) || !Files.isDirectory(reportPath)) {
            throw new ResourceNotFoundException("Report not found: " + reportId);
        }

        try {
            // Create temp ZIP file
            Path zipPath = Files.createTempFile("report-" + reportId, ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                // Walk through all files in report directory
                Files.walk(reportPath)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            try {
                                String zipEntryName = reportPath.relativize(path).toString();
                                ZipEntry zipEntry = new ZipEntry(zipEntryName);
                                zos.putNextEntry(zipEntry);
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                log.error("Error adding file to ZIP: {}", e.getMessage());
                            }
                        });
            }

            return zipPath.toFile();
        } catch (IOException e) {
            log.error("Error creating ZIP archive: {}", e.getMessage());
            throw new RuntimeException("Failed to create report archive", e);
        }
    }

    /**
     * Get report by ID
     */
    public Report getReport(String reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    /**
     * Get report by execution ID
     */
    public Report getReportByExecutionId(String executionId) {
        return reportRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found for execution"));
    }

    /**
     * Get all reports by listing directories from storage/reports folder
     */
    public List<Report> getAllReports() {
        Path reportDir = Paths.get(storageConfig.getReportDir());
        List<Report> reports = new ArrayList<>();
        
        if (!Files.exists(reportDir) || !Files.isDirectory(reportDir)) {
            log.warn("Report directory does not exist: {}", reportDir);
            return reports;
        }
        
        try (Stream<Path> paths = Files.list(reportDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(dir -> {
                        try {
                            String reportId = dir.getFileName().toString();
                            long size = calculateDirectorySize(dir.toString());
                            Instant createdAt = Files.getLastModifiedTime(dir).toInstant();
                            
                            Report report = Report.builder()
                                    .id(reportId)
                                    .executionId(reportId) // Use folder name as both id and executionId
                                    .path(dir.toString())
                                    .createdAt(createdAt)
                                    .size(size)
                                    .build();
                            
                            reports.add(report);
                        } catch (IOException e) {
                            log.error("Error reading report directory: {}", dir, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Error listing report directories: {}", e.getMessage(), e);
        }
        
        // Sort by creation date (newest first)
        reports.sort(Comparator.comparing(Report::getCreatedAt).reversed());
        
        return reports;
    }

    /**
     * Retrieve a specific resource file from the report directory
     * Reads directly from storage/reports/{id} folder
     */
    public Resource getReportResource(String reportId, String resourcePath) {
        Path reportDir = Paths.get(storageConfig.getReportDir());
        Path reportPath = reportDir.resolve(reportId);
        
        if (!Files.exists(reportPath) || !Files.isDirectory(reportPath)) {
            throw new ResourceNotFoundException("Report not found: " + reportId);
        }

        // Handle empty or root path - default to index.html
        if (resourcePath == null || resourcePath.isEmpty() || resourcePath.equals("/")) {
            resourcePath = "index.html";
        }

        Path resourceFilePath = reportPath.resolve(resourcePath).normalize();

        // Security check: ensure the resolved path is still within the report directory
        if (!resourceFilePath.startsWith(reportPath)) {
            throw new ResourceNotFoundException("Invalid resource path");
        }

        if (!Files.exists(resourceFilePath)) {
            throw new ResourceNotFoundException("Resource not found: " + resourcePath);
        }

        try {
            Resource resource = new UrlResource(resourceFilePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Resource not readable: " + resourcePath);
            }
        } catch (IOException e) {
            log.error("Error reading resource: {}", e.getMessage());
            throw new ResourceNotFoundException("Failed to read resource: " + resourcePath);
        }
    }

    /**
     * Delete a report by removing its directory from storage/reports folder
     */
    public void deleteReport(String reportId) {
        Path reportDir = Paths.get(storageConfig.getReportDir());
        Path reportPath = reportDir.resolve(reportId);
        
        if (!Files.exists(reportPath) || !Files.isDirectory(reportPath)) {
            throw new ResourceNotFoundException("Report not found: " + reportId);
        }
        
        try {
            deleteDirectory(reportPath);
            log.info("Report deleted successfully: {}", reportId);
        } catch (IOException e) {
            log.error("Failed to delete report directory: {}", reportPath, e);
            throw new RuntimeException("Failed to delete report. Please try again.", e);
        }
    }

    /**
     * Delete a directory and all its contents
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

    /**
     * Calculate the total size of a directory
     */
    private long calculateDirectorySize(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                return Files.walk(path)
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .sum();
            }
        } catch (IOException e) {
            log.warn("Failed to calculate directory size for: {}", directoryPath, e);
        }
        return 0L;
    }
}

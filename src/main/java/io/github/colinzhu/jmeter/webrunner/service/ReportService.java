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
     */
    public File packageReportAsZip(String reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        Path reportPath = Paths.get(report.getPath());
        if (!Files.exists(reportPath)) {
            throw new ResourceNotFoundException("Report directory not found");
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
     * Retrieve a specific resource file from the report directory
     */
    public Resource getReportResource(String reportId, String resourcePath) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        Path reportPath = Paths.get(report.getPath());

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
}

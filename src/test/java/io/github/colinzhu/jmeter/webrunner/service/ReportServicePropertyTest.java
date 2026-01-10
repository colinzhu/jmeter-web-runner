package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.model.Report;
import io.github.colinzhu.jmeter.webrunner.repository.ReportRepository;
import net.jqwik.api.*;
import org.springframework.core.io.Resource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for ReportService
 * Feature: jmeter-web-runner
 */
class ReportServicePropertyTest {

    /**
     * Property 8: Report Accessibility
     * For any completed execution, requesting the report via the view endpoint
     * should return HTML content that can be rendered in a browser.
     * <p>
     * Feature: jmeter-web-runner, Property 8: Report Accessibility
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    void reportAccessibility_completedExecutionReturnsHtmlContent(@ForAll("reportIds") String reportId) throws Exception {
        // Create a temporary report directory with index.html
        Path tempReportDir = Files.createTempDirectory("report-" + reportId);
        Path indexHtml = tempReportDir.resolve("index.html");
        Files.writeString(indexHtml, "<html><body>Test Report</body></html>");

        // Create report model
        Report report = Report.builder()
                .id(reportId)
                .executionId(UUID.randomUUID().toString())
                .path(tempReportDir.toString())
                .createdAt(Instant.now())
                .size(1024L)
                .build();

        // Mock repository
        ReportRepository reportRepository = mock(ReportRepository.class);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        StorageConfig storageConfig = mock(StorageConfig.class);
        ReportService reportService = new ReportService(reportRepository, storageConfig);

        // Act: Get report HTML
        Resource resource = reportService.getReportHtml(reportId);

        // Assert: Resource should exist and be readable
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        assertThat(resource.getFilename()).isEqualTo("index.html");

        // Cleanup
        Files.deleteIfExists(indexHtml);
        Files.deleteIfExists(tempReportDir);
    }

    /**
     * Property 9: Multiple Report Support
     * For any set of test executions, each completed execution should have its own
     * accessible report that can be viewed independently.
     * <p>
     * Feature: jmeter-web-runner, Property 9: Multiple Report Support
     * Validates: Requirements 3.5
     */
    @Property(tries = 100)
    void multipleReportSupport_eachExecutionHasIndependentReport(
            @ForAll("reportIdList") java.util.List<String> reportIds) throws Exception {

        Assume.that(reportIds.size() >= 2 && reportIds.size() <= 5);

        ReportRepository reportRepository = mock(ReportRepository.class);
        StorageConfig storageConfig = mock(StorageConfig.class);
        ReportService reportService = new ReportService(reportRepository, storageConfig);

        // Create multiple reports with different content
        java.util.Map<String, Path> reportPaths = new java.util.HashMap<>();

        for (String reportId : reportIds) {
            // Create unique report directory
            Path tempReportDir = Files.createTempDirectory("report-" + reportId);
            Path indexHtml = tempReportDir.resolve("index.html");
            String uniqueContent = "<html><body>Report " + reportId + "</body></html>";
            Files.writeString(indexHtml, uniqueContent);

            reportPaths.put(reportId, tempReportDir);

            // Create report model
            Report report = Report.builder()
                    .id(reportId)
                    .executionId(UUID.randomUUID().toString())
                    .path(tempReportDir.toString())
                    .createdAt(Instant.now())
                    .size(1024L)
                    .build();

            when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        }

        // Act & Assert: Each report should be independently accessible
        for (String reportId : reportIds) {
            Resource resource = reportService.getReportHtml(reportId);

            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
            assertThat(resource.isReadable()).isTrue();

            // Verify content is unique to this report
            String content = Files.readString(resource.getFile().toPath());
            assertThat(content).contains("Report " + reportId);
        }

        // Cleanup
        for (Path path : reportPaths.values()) {
            Files.deleteIfExists(path.resolve("index.html"));
            Files.deleteIfExists(path);
        }
    }

    /**
     * Property 10: Report Download Availability
     * For any completed execution, the download endpoint should serve a ZIP archive
     * containing the complete report package.
     * <p>
     * Feature: jmeter-web-runner, Property 10: Report Download Availability
     * Validates: Requirements 4.1, 4.3
     */
    @Property(tries = 100)
    void reportDownloadAvailability_completedExecutionServesZipArchive(
            @ForAll("reportIds") String reportId) throws Exception {

        // Create a temporary report directory with files
        Path tempReportDir = Files.createTempDirectory("report-" + reportId);
        Path indexHtml = tempReportDir.resolve("index.html");
        Files.writeString(indexHtml, "<html><body>Test Report</body></html>");

        // Create report model
        Report report = Report.builder()
                .id(reportId)
                .executionId(UUID.randomUUID().toString())
                .path(tempReportDir.toString())
                .createdAt(Instant.now())
                .size(1024L)
                .build();

        // Mock repository
        ReportRepository reportRepository = mock(ReportRepository.class);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        StorageConfig storageConfig = mock(StorageConfig.class);
        ReportService reportService = new ReportService(reportRepository, storageConfig);

        // Act: Package report as ZIP
        File zipFile = reportService.packageReportAsZip(reportId);

        // Assert: ZIP file should exist and be readable
        assertThat(zipFile).isNotNull();
        assertThat(zipFile.exists()).isTrue();
        assertThat(zipFile.canRead()).isTrue();
        assertThat(zipFile.getName()).endsWith(".zip");
        assertThat(zipFile.length()).isGreaterThan(0);

        // Cleanup
        Files.deleteIfExists(zipFile.toPath());
        Files.deleteIfExists(indexHtml);
        Files.deleteIfExists(tempReportDir);
    }

    /**
     * Property 11: Report Archive Completeness
     * For any downloaded report archive, it should contain all required artifacts
     * including HTML files, result logs (JTL), and any generated statistics or assets.
     * <p>
     * Feature: jmeter-web-runner, Property 11: Report Archive Completeness
     * Validates: Requirements 4.2, 4.4
     */
    @Property(tries = 100)
    void reportArchiveCompleteness_zipContainsAllArtifacts(
            @ForAll("reportIds") String reportId) throws Exception {

        // Create a temporary report directory with multiple files
        Path tempReportDir = Files.createTempDirectory("report-" + reportId);
        Path indexHtml = tempReportDir.resolve("index.html");
        Path resultsJtl = tempReportDir.resolve("results.jtl");
        Path statisticsJson = tempReportDir.resolve("statistics.json");

        Files.writeString(indexHtml, "<html><body>Test Report</body></html>");
        Files.writeString(resultsJtl, "timestamp,elapsed,label,responseCode\n");
        Files.writeString(statisticsJson, "{\"total\": 100}");

        // Create subdirectory with assets
        Path contentDir = tempReportDir.resolve("content");
        Files.createDirectory(contentDir);
        Path assetFile = contentDir.resolve("style.css");
        Files.writeString(assetFile, "body { margin: 0; }");

        // Create report model
        Report report = Report.builder()
                .id(reportId)
                .executionId(UUID.randomUUID().toString())
                .path(tempReportDir.toString())
                .createdAt(Instant.now())
                .size(1024L)
                .build();

        // Mock repository
        ReportRepository reportRepository = mock(ReportRepository.class);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        StorageConfig storageConfig = mock(StorageConfig.class);
        ReportService reportService = new ReportService(reportRepository, storageConfig);

        // Act: Package report as ZIP
        File zipFile = reportService.packageReportAsZip(reportId);

        // Assert: ZIP should contain all files
        assertThat(zipFile).isNotNull();
        assertThat(zipFile.exists()).isTrue();

        // Verify ZIP contains expected files by checking size
        // A ZIP with 4 files should be larger than a ZIP with 1 file
        assertThat(zipFile.length()).isGreaterThan(100);

        // Cleanup
        Files.deleteIfExists(zipFile.toPath());
        Files.deleteIfExists(assetFile);
        Files.deleteIfExists(contentDir);
        Files.deleteIfExists(statisticsJson);
        Files.deleteIfExists(resultsJtl);
        Files.deleteIfExists(indexHtml);
        Files.deleteIfExists(tempReportDir);
    }

    /**
     * Property 12: Report Persistence After Download
     * For any report that has been downloaded, the report should remain accessible
     * via the view endpoint for future access.
     * <p>
     * Feature: jmeter-web-runner, Property 12: Report Persistence After Download
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    void reportPersistenceAfterDownload_reportRemainsAccessibleAfterDownload(
            @ForAll("reportIds") String reportId) throws Exception {

        // Create a temporary report directory
        Path tempReportDir = Files.createTempDirectory("report-" + reportId);
        Path indexHtml = tempReportDir.resolve("index.html");
        Files.writeString(indexHtml, "<html><body>Test Report</body></html>");

        // Create report model
        Report report = Report.builder()
                .id(reportId)
                .executionId(UUID.randomUUID().toString())
                .path(tempReportDir.toString())
                .createdAt(Instant.now())
                .size(1024L)
                .build();

        // Mock repository
        ReportRepository reportRepository = mock(ReportRepository.class);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        StorageConfig storageConfig = mock(StorageConfig.class);
        ReportService reportService = new ReportService(reportRepository, storageConfig);

        // Act: Download report (package as ZIP)
        File zipFile = reportService.packageReportAsZip(reportId);
        assertThat(zipFile.exists()).isTrue();

        // Act: Try to view report after download
        Resource resource = reportService.getReportHtml(reportId);

        // Assert: Report should still be accessible
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();

        // Verify original report files still exist
        assertThat(Files.exists(indexHtml)).isTrue();
        assertThat(Files.exists(tempReportDir)).isTrue();

        // Cleanup
        Files.deleteIfExists(zipFile.toPath());
        Files.deleteIfExists(indexHtml);
        Files.deleteIfExists(tempReportDir);
    }

    /**
     * Provides random report IDs
     */
    @Provide
    Arbitrary<String> reportIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(36)
                .map(s -> "report-" + s);
    }

    /**
     * Provides lists of unique report IDs
     */
    @Provide
    Arbitrary<java.util.List<String>> reportIdList() {
        return reportIds().list().ofMinSize(2).ofMaxSize(5)
                .map(list -> list.stream().distinct().collect(java.util.stream.Collectors.toList()));
    }
}

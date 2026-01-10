package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.model.*;
import io.github.colinzhu.jmeter.webrunner.repository.ExecutionRepository;
import io.github.colinzhu.jmeter.webrunner.service.ExtractionService;
import io.github.colinzhu.jmeter.webrunner.service.JMeterManager;
import net.jqwik.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for SetupController
 * Feature: jmeter-setup-upload
 */
class SetupControllerPropertyTest {

    private SetupController createController() {
        JMeterManager jmeterManager = mock(JMeterManager.class);
        ExtractionService extractionService = mock(ExtractionService.class);
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        return new SetupController(jmeterManager, extractionService, executionRepository);
    }

    /**
     * Property 1: File Extension Validation
     * Validates: Requirements 1.1, 1.2
     * <p>
     * For any file upload request, the system should accept files with .zip extension
     * and reject files without .zip extension, providing appropriate feedback in both cases.
     */
    @Property(tries = 100)
    @Label("Feature: jmeter-setup-upload, Property 1: File Extension Validation")
    void fileExtensionValidation(@ForAll("filenames") String filename) {
        // Create controller for this test
        SetupController setupController = createController();

        // Create a mock multipart file with the given filename
        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "application/octet-stream",
                "test content".getBytes()
        );

        // Upload the file
        ResponseEntity<UploadResponse> response = setupController.uploadJMeter(file);

        // Verify behavior based on file extension
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            // ZIP files should not be rejected due to extension
            // (they may fail for other reasons like extraction, but not extension validation)
            assertThat(response.getBody()).isNotNull();
            if (!response.getBody().isSuccess()) {
                // If it failed, it should not be due to file extension
                assertThat(response.getBody().getMessage())
                        .doesNotContain("Only .zip files are accepted");
            }
        } else {
            // Non-ZIP files should be rejected with BAD_REQUEST
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage())
                    .contains("Only .zip files are accepted");
        }
    }

    /**
     * Property 2: Upload Response Completeness
     * Validates: Requirements 1.3, 1.4
     * <p>
     * For any file upload attempt (successful or failed), the API response should include
     * appropriate feedback - either confirmation with filename and size for success, or a
     * descriptive error message for failure.
     */
    @Property(tries = 100)
    @Label("Feature: jmeter-setup-upload, Property 2: Upload Response Completeness")
    void uploadResponseCompleteness(@ForAll("filenames") String filename) {
        SetupController setupController = createController();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "application/octet-stream",
                "test content".getBytes()
        );

        ResponseEntity<UploadResponse> response = setupController.uploadJMeter(file);

        // Every response must have a body
        assertThat(response.getBody()).isNotNull();

        // Response must have a success flag
        assertThat(response.getBody().isSuccess()).isNotNull();

        // Response must have a message
        assertThat(response.getBody().getMessage()).isNotNull();
        assertThat(response.getBody().getMessage()).isNotEmpty();
    }

    /**
     * Property 14: Status Information Completeness
     * Validates: Requirements 4.1, 4.2, 4.3
     * <p>
     * For any status request, the system should return whether JMeter is configured,
     * and include version and path when configured, or a prompt message when not configured.
     */
    @Property(tries = 100)
    @Label("Feature: jmeter-setup-upload, Property 14: Status Information Completeness")
    void statusInformationCompleteness(@ForAll boolean isConfigured) {
        JMeterManager jmeterManager = mock(JMeterManager.class);
        when(jmeterManager.isConfigured()).thenReturn(isConfigured);

        if (isConfigured) {
            JMeterInfo info = JMeterInfo.builder()
                    .version("5.6.3")
                    .path("/path/to/jmeter")
                    .available(true)
                    .build();
            when(jmeterManager.verifyInstallation()).thenReturn(info);
        }

        SetupController setupController = new SetupController(
                jmeterManager,
                mock(ExtractionService.class),
                mock(ExecutionRepository.class)
        );

        ResponseEntity<SetupStatus> response = setupController.getStatus();

        // Response must have a body
        assertThat(response.getBody()).isNotNull();

        // Response must indicate configuration status
        assertThat(response.getBody().isConfigured()).isEqualTo(isConfigured);

        if (isConfigured) {
            // When configured, should have version and path
            assertThat(response.getBody().getVersion()).isNotNull();
            assertThat(response.getBody().getPath()).isNotNull();
        }
    }

    /**
     * Property 20: Active Execution Check Before Removal
     * Validates: Requirements 6.3, 6.4
     * <p>
     * For any replacement attempt, the system should check for active test executions
     * and prevent removal if any are found.
     */
    @Property(tries = 100)
    @Label("Feature: jmeter-setup-upload, Property 20: Active Execution Check Before Removal")
    void activeExecutionCheckBeforeRemoval(@ForAll boolean hasActiveExecutions) {
        JMeterManager jmeterManager = mock(JMeterManager.class);
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);

        // Setup mock executions
        java.util.List<Execution> executions = new java.util.ArrayList<>();
        if (hasActiveExecutions) {
            Execution activeExecution = Execution.builder()
                    .id("test-id")
                    .status(ExecutionStatus.RUNNING)
                    .build();
            executions.add(activeExecution);
        }
        when(executionRepository.findAll()).thenReturn(executions);

        SetupController setupController = new SetupController(
                jmeterManager,
                mock(ExtractionService.class),
                executionRepository
        );

        ResponseEntity<Map<String, Object>> response = setupController.deleteInstallation();

        if (hasActiveExecutions) {
            // Should be blocked with CONFLICT status
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("success")).isEqualTo(false);
            assertThat(response.getBody().get("message").toString())
                    .contains("active");
        } else {
            // Should succeed
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("success")).isEqualTo(true);
        }
    }

    @Provide
    Arbitrary<String> filenames() {
        // Generate various filenames with different extensions
        Arbitrary<String> zipFiles = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20)
                .map(name -> name + ".zip");

        Arbitrary<String> zipFilesUpperCase = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20)
                .map(name -> name + ".ZIP");

        Arbitrary<String> zipFilesMixedCase = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20)
                .map(name -> name + ".ZiP");

        Arbitrary<String> nonZipFiles = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20)
                .flatMap(name -> Arbitraries.of(".txt", ".jar", ".tar", ".gz", ".exe", ".pdf", "")
                        .map(ext -> name + ext));

        Arbitrary<String> nullFilename = Arbitraries.just(null);

        return Arbitraries.oneOf(zipFiles, zipFilesUpperCase, zipFilesMixedCase, nonZipFiles, nullFilename);
    }
}

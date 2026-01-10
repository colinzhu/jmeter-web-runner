package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.exception.ExtractionException;
import io.github.colinzhu.jmeter.webrunner.exception.GlobalExceptionHandler;
import io.github.colinzhu.jmeter.webrunner.model.Execution;
import io.github.colinzhu.jmeter.webrunner.model.ExecutionStatus;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import io.github.colinzhu.jmeter.webrunner.repository.ExecutionRepository;
import io.github.colinzhu.jmeter.webrunner.service.ExtractionService;
import io.github.colinzhu.jmeter.webrunner.service.JMeterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for SetupController
 * Tests status endpoint, upload with valid/invalid files, and deletion with/without active executions
 * Requirements: 1.1, 1.2, 4.1, 6.3, 6.4
 */
class SetupControllerUnitTest {

    private MockMvc mockMvc;
    private JMeterManager jmeterManager;
    private ExtractionService extractionService;
    private ExecutionRepository executionRepository;

    @BeforeEach
    void setUp() throws Exception {
        jmeterManager = mock(JMeterManager.class);
        extractionService = mock(ExtractionService.class);
        executionRepository = mock(ExecutionRepository.class);

        SetupController setupController = new SetupController(
                jmeterManager,
                extractionService,
                executionRepository
        );

        // Set the storageLocation field using reflection
        java.lang.reflect.Field storageLocationField = SetupController.class.getDeclaredField("storageLocation");
        storageLocationField.setAccessible(true);
        storageLocationField.set(setupController, "uploads");

        mockMvc = MockMvcBuilders.standaloneSetup(setupController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Test status endpoint when JMeter is not configured
     * Requirements: 4.1
     */
    @Test
    void getStatus_whenNotConfigured_shouldReturnNotConfiguredStatus() throws Exception {
        // Arrange
        when(jmeterManager.isConfigured()).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.available").value(false));

        verify(jmeterManager, times(1)).isConfigured();
        verify(jmeterManager, never()).verifyInstallation();
    }

    /**
     * Test status endpoint when JMeter is configured and available
     * Requirements: 4.1
     */
    @Test
    void getStatus_whenConfiguredAndAvailable_shouldReturnFullStatus() throws Exception {
        // Arrange
        when(jmeterManager.isConfigured()).thenReturn(true);

        JMeterInfo info = JMeterInfo.builder()
                .version("5.6.3")
                .path("/opt/jmeter")
                .available(true)
                .build();
        when(jmeterManager.verifyInstallation()).thenReturn(info);

        // Act & Assert
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.version").value("5.6.3"))
                .andExpect(jsonPath("$.path").value("/opt/jmeter"));

        verify(jmeterManager, times(1)).isConfigured();
        verify(jmeterManager, times(1)).verifyInstallation();
    }

    /**
     * Test status endpoint when JMeter is configured but not available
     * Requirements: 4.1
     */
    @Test
    void getStatus_whenConfiguredButNotAvailable_shouldReturnErrorStatus() throws Exception {
        // Arrange
        when(jmeterManager.isConfigured()).thenReturn(true);

        JMeterInfo info = JMeterInfo.builder()
                .version(null)
                .path("/opt/jmeter")
                .available(false)
                .error("JMeter binary not found")
                .build();
        when(jmeterManager.verifyInstallation()).thenReturn(info);

        // Act & Assert
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.error").value("JMeter binary not found"));

        verify(jmeterManager, times(1)).isConfigured();
        verify(jmeterManager, times(1)).verifyInstallation();
    }

    /**
     * Test upload with non-ZIP file
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadJMeter_withNonZipFile_shouldReturnBadRequest() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "jmeter.tar.gz",
                "application/gzip",
                "test content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/setup/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid file type. Only .zip files are accepted."));
    }

    /**
     * Test upload with file exceeding size limit
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadJMeter_withOversizedFile_shouldReturnBadRequest() throws Exception {
        // Arrange - Create a file larger than 200MB
        long oversizedLength = 201L * 1024 * 1024; // 201MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "jmeter.zip",
                "application/zip",
                new byte[0] // We'll mock the size
        ) {
            @Override
            public long getSize() {
                return oversizedLength;
            }
        };

        // Act & Assert
        mockMvc.perform(multipart("/api/setup/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("File size exceeds maximum limit of 200MB."));
    }

    /**
     * Test upload with invalid JMeter distribution
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadJMeter_withInvalidDistribution_shouldReturnBadRequest() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.zip",
                "application/zip",
                "test content".getBytes()
        );

        when(extractionService.extractJMeterDistribution(any(), any()))
                .thenThrow(new ExtractionException("Invalid JMeter distribution structure"));

        // Act & Assert
        mockMvc.perform(multipart("/api/setup/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid JMeter distribution structure"));

        verify(extractionService, times(1)).extractJMeterDistribution(any(), any());
    }

    /**
     * Test successful upload with valid ZIP file
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadJMeter_withValidZipFile_shouldSucceed() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "apache-jmeter-5.6.3.zip",
                "application/zip",
                "valid zip content".getBytes()
        );

        Path jmeterPath = Paths.get("opt", "jmeter");
        when(extractionService.extractJMeterDistribution(any(), any())).thenReturn(jmeterPath);

        JMeterInfo info = JMeterInfo.builder()
                .version("5.6.3")
                .path(jmeterPath.toString())
                .available(true)
                .build();
        when(jmeterManager.verifyInstallation()).thenReturn(info);

        // Act & Assert
        mockMvc.perform(multipart("/api/setup/upload")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.version").value("5.6.3"))
                .andExpect(jsonPath("$.path").value(jmeterPath.toString()))
                .andExpect(jsonPath("$.message").value("JMeter distribution uploaded and configured successfully"));

        verify(extractionService, times(1)).extractJMeterDistribution(any(), any());
        verify(jmeterManager, times(1)).setInstallationPath(jmeterPath.toString());
        verify(jmeterManager, times(1)).verifyInstallation();
    }

    /**
     * Test deletion when active executions exist
     * Requirements: 6.3, 6.4
     */
    @Test
    void deleteInstallation_withActiveExecutions_shouldReturnConflict() throws Exception {
        // Arrange
        List<Execution> executions = new ArrayList<>();
        Execution activeExecution = Execution.builder()
                .id("exec-1")
                .status(ExecutionStatus.RUNNING)
                .build();
        executions.add(activeExecution);

        when(executionRepository.findAll()).thenReturn(executions);

        // Act & Assert
        mockMvc.perform(delete("/api/setup/installation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot replace JMeter while test executions are active. Please wait for executions to complete."));

        verify(executionRepository, times(1)).findAll();
        verify(jmeterManager, never()).clearConfiguration();
    }

    /**
     * Test deletion when no active executions exist
     * Requirements: 6.3, 6.4
     */
    @Test
    void deleteInstallation_withoutActiveExecutions_shouldSucceed() throws Exception {
        // Arrange
        List<Execution> executions = new ArrayList<>();
        Execution completedExecution = Execution.builder()
                .id("exec-1")
                .status(ExecutionStatus.COMPLETED)
                .build();
        executions.add(completedExecution);

        when(executionRepository.findAll()).thenReturn(executions);
        when(jmeterManager.isConfigured()).thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/setup/installation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("JMeter installation removed successfully"));

        verify(executionRepository, times(1)).findAll();
        verify(jmeterManager, times(1)).clearConfiguration();
    }

    /**
     * Test deletion with queued executions
     * Requirements: 6.3, 6.4
     */
    @Test
    void deleteInstallation_withQueuedExecutions_shouldReturnConflict() throws Exception {
        // Arrange
        List<Execution> executions = new ArrayList<>();
        Execution queuedExecution = Execution.builder()
                .id("exec-1")
                .status(ExecutionStatus.QUEUED)
                .build();
        executions.add(queuedExecution);

        when(executionRepository.findAll()).thenReturn(executions);

        // Act & Assert
        mockMvc.perform(delete("/api/setup/installation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot replace JMeter while test executions are active. Please wait for executions to complete."));

        verify(executionRepository, times(1)).findAll();
        verify(jmeterManager, never()).clearConfiguration();
    }

    /**
     * Test verify endpoint when JMeter is not configured
     * Requirements: 4.1
     */
    @Test
    void verifyInstallation_whenNotConfigured_shouldReturnNotFound() throws Exception {
        // Arrange
        when(jmeterManager.isConfigured()).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/setup/verify"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.error").value("JMeter is not configured"));

        verify(jmeterManager, times(1)).isConfigured();
        verify(jmeterManager, never()).verifyInstallation();
    }

    /**
     * Test verify endpoint when JMeter is configured and available
     * Requirements: 4.1
     */
    @Test
    void verifyInstallation_whenConfiguredAndAvailable_shouldReturnSuccess() throws Exception {
        // Arrange
        when(jmeterManager.isConfigured()).thenReturn(true);

        JMeterInfo info = JMeterInfo.builder()
                .version("5.6.3")
                .path("/opt/jmeter")
                .available(true)
                .build();
        when(jmeterManager.verifyInstallation()).thenReturn(info);

        // Act & Assert
        mockMvc.perform(post("/api/setup/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.version").value("5.6.3"))
                .andExpect(jsonPath("$.path").value("/opt/jmeter"));

        verify(jmeterManager, times(1)).isConfigured();
        verify(jmeterManager, times(1)).verifyInstallation();
    }
}

package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.exception.GlobalExceptionHandler;
import io.github.colinzhu.jmeter.webrunner.exception.InvalidFileException;
import io.github.colinzhu.jmeter.webrunner.exception.ResourceNotFoundException;
import io.github.colinzhu.jmeter.webrunner.model.File;
import io.github.colinzhu.jmeter.webrunner.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for FileController edge cases
 * Tests: empty files, files at size limits, special characters in filenames
 * Requirements: 1.1, 1.2, 1.5
 */
class FileControllerUnitTest {

    private MockMvc mockMvc;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        FileController fileController = new FileController(fileStorageService);

        mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Test empty file upload
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_withEmptyFile_shouldReturnBadRequest() throws Exception {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "test.jmx",
                MediaType.APPLICATION_XML_VALUE,
                new byte[0]
        );

        when(fileStorageService.storeFile(any()))
                .thenThrow(new InvalidFileException("File is empty or null."));

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("File is empty or null."));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test file at maximum size limit (50MB)
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_atMaxSizeLimit_shouldSucceed() throws Exception {
        // Arrange
        long maxSize = 50 * 1024 * 1024; // 50MB in bytes
        byte[] largeContent = new byte[(int) maxSize];

        // Fill with valid XML content at the beginning
        String xmlHeader = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";
        byte[] xmlBytes = xmlHeader.getBytes();
        System.arraycopy(xmlBytes, 0, largeContent, 0, xmlBytes.length);

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large-test.jmx",
                MediaType.APPLICATION_XML_VALUE,
                largeContent
        );

        File savedFile = File.builder()
                .id(UUID.randomUUID().toString())
                .filename("large-test.jmx")
                .size(maxSize)
                .uploadedAt(Instant.now())
                .path("/storage/uploads/test.jmx")
                .build();

        when(fileStorageService.storeFile(any())).thenReturn(savedFile);

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(largeFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value("large-test.jmx"))
                .andExpect(jsonPath("$.size").value(maxSize));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test file just below maximum size limit
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_justBelowMaxSize_shouldSucceed() throws Exception {
        // Arrange
        long nearMaxSize = (50 * 1024 * 1024) - 1024; // 50MB - 1KB
        byte[] content = new byte[(int) nearMaxSize];

        // Fill with valid XML content at the beginning
        String xmlHeader = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";
        byte[] xmlBytes = xmlHeader.getBytes();
        System.arraycopy(xmlBytes, 0, content, 0, xmlBytes.length);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "near-max.jmx",
                MediaType.APPLICATION_XML_VALUE,
                content
        );

        File savedFile = File.builder()
                .id(UUID.randomUUID().toString())
                .filename("near-max.jmx")
                .size(nearMaxSize)
                .uploadedAt(Instant.now())
                .path("/storage/uploads/test.jmx")
                .build();

        when(fileStorageService.storeFile(any())).thenReturn(savedFile);

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value("near-max.jmx"))
                .andExpect(jsonPath("$.size").value(nearMaxSize));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test filename with special characters (spaces)
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_withSpacesInFilename_shouldSucceed() throws Exception {
        // Arrange
        String filename = "my test file.jmx";
        String validXml = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_XML_VALUE,
                validXml.getBytes()
        );

        File savedFile = File.builder()
                .id(UUID.randomUUID().toString())
                .filename(filename)
                .size(validXml.getBytes().length)
                .uploadedAt(Instant.now())
                .path("/storage/uploads/test.jmx")
                .build();

        when(fileStorageService.storeFile(any())).thenReturn(savedFile);

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value(filename));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test filename with special characters (hyphens and underscores)
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_withHyphensAndUnderscores_shouldSucceed() throws Exception {
        // Arrange
        String filename = "my-test_file-2024.jmx";
        String validXml = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_XML_VALUE,
                validXml.getBytes()
        );

        File savedFile = File.builder()
                .id(UUID.randomUUID().toString())
                .filename(filename)
                .size(validXml.getBytes().length)
                .uploadedAt(Instant.now())
                .path("/storage/uploads/test.jmx")
                .build();

        when(fileStorageService.storeFile(any())).thenReturn(savedFile);

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value(filename));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test filename with parentheses
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_withParenthesesInFilename_shouldSucceed() throws Exception {
        // Arrange
        String filename = "test(1).jmx";
        String validXml = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_XML_VALUE,
                validXml.getBytes()
        );

        File savedFile = File.builder()
                .id(UUID.randomUUID().toString())
                .filename(filename)
                .size(validXml.getBytes().length)
                .uploadedAt(Instant.now())
                .path("/storage/uploads/test.jmx")
                .build();

        when(fileStorageService.storeFile(any())).thenReturn(savedFile);

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value(filename));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test filename with Unicode characters
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_withUnicodeCharacters_shouldSucceed() throws Exception {
        // Arrange
        String filename = "测试文件.jmx"; // Chinese characters
        String validXml = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_XML_VALUE,
                validXml.getBytes()
        );

        File savedFile = File.builder()
                .id(UUID.randomUUID().toString())
                .filename(filename)
                .size(validXml.getBytes().length)
                .uploadedAt(Instant.now())
                .path("/storage/uploads/test.jmx")
                .build();

        when(fileStorageService.storeFile(any())).thenReturn(savedFile);

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value(filename));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test filename with dots (multiple extensions)
     * Requirements: 1.1, 1.2
     */
    @Test
    void uploadFile_withMultipleDots_shouldSucceed() throws Exception {
        // Arrange
        String filename = "test.backup.jmx";
        String validXml = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_XML_VALUE,
                validXml.getBytes()
        );

        File savedFile = File.builder()
                .id(UUID.randomUUID().toString())
                .filename(filename)
                .size(validXml.getBytes().length)
                .uploadedAt(Instant.now())
                .path("/storage/uploads/test.jmx")
                .build();

        when(fileStorageService.storeFile(any())).thenReturn(savedFile);

        // Act & Assert
        mockMvc.perform(multipart("/api/files")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.filename").value(filename));

        verify(fileStorageService, times(1)).storeFile(any());
    }

    /**
     * Test successful file download
     * Requirements: Download functionality
     */
    @Test
    void downloadFile_withValidId_shouldReturnFile() throws Exception {
        // Arrange
        String fileId = UUID.randomUUID().toString();
        String filename = "test-download.jmx";
        String fileContent = "<?xml version=\"1.0\"?><jmeterTestPlan><test></test></jmeterTestPlan>";

        // Create a temporary file for testing
        Path tempFile = Files.createTempFile("test-", ".jmx");
        Files.write(tempFile, fileContent.getBytes());

        File fileEntity = File.builder()
                .id(fileId)
                .filename(filename)
                .size(fileContent.length())
                .uploadedAt(Instant.now())
                .path(tempFile.toString())
                .build();

        when(fileStorageService.getFile(fileId)).thenReturn(fileEntity);

        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileStorageService))
                .build();

        // Act & Assert
        mockMvc.perform(get("/api/files/{id}/download", fileId))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + filename + "\""))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE));

        verify(fileStorageService, times(1)).getFile(fileId);

        // Cleanup
        Files.deleteIfExists(tempFile);
    }

    /**
     * Test file download with non-existent file
     * Requirements: Download functionality error handling
     */
    @Test
    void downloadFile_withNonExistentFileId_shouldReturnNotFound() throws Exception {
        // Arrange
        String fileId = UUID.randomUUID().toString();

        // Use a path that definitely doesn't exist
        String nonExistentPath = "/tmp/" + UUID.randomUUID() + ".jmx";

        File fileEntity = File.builder()
                .id(fileId)
                .filename("non-existent.jmx")
                .size(100)
                .uploadedAt(Instant.now())
                .path(nonExistentPath)
                .build();

        when(fileStorageService.getFile(fileId)).thenReturn(fileEntity);

        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileStorageService))
                .build();

        // Act & Assert
        mockMvc.perform(get("/api/files/{id}/download", fileId))
                .andExpect(status().isNotFound());

        verify(fileStorageService, times(1)).getFile(fileId);
    }

    /**
     * Test file download with invalid file ID
     * Requirements: Download functionality error handling
     */
    @Test
    void downloadFile_withInvalidId_shouldReturnNotFound() throws Exception {
        // Arrange
        String invalidFileId = "non-existent-id";

        when(fileStorageService.getFile(eq(invalidFileId)))
                .thenThrow(new ResourceNotFoundException("File not found with id: " + invalidFileId));

        // Act & Assert
        mockMvc.perform(get("/api/files/{id}/download", invalidFileId))
                .andExpect(status().isNotFound());

        verify(fileStorageService, times(1)).getFile(invalidFileId);
    }

    /**
     * Test file download with special characters in filename
     * Requirements: Download functionality with edge cases
     */
    @Test
    void downloadFile_withSpecialCharactersInFilename_shouldReturnFile() throws Exception {
        // Arrange
        String fileId = UUID.randomUUID().toString();
        String filename = "test file (1) [2024].jmx";
        String fileContent = "<?xml version=\"1.0\"?><jmeterTestPlan><test></test></jmeterTestPlan>";

        // Create a temporary file for testing
        Path tempFile = Files.createTempFile("test-", ".jmx");
        Files.write(tempFile, fileContent.getBytes());

        File fileEntity = File.builder()
                .id(fileId)
                .filename(filename)
                .size(fileContent.length())
                .uploadedAt(Instant.now())
                .path(tempFile.toString())
                .build();

        when(fileStorageService.getFile(fileId)).thenReturn(fileEntity);

        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileStorageService))
                .build();

        // Act & Assert
        mockMvc.perform(get("/api/files/{id}/download", fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + filename + "\""));

        verify(fileStorageService, times(1)).getFile(fileId);

        // Cleanup
        Files.deleteIfExists(tempFile);
    }
}

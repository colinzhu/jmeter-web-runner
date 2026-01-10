package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.exception.GlobalExceptionHandler;
import io.github.colinzhu.jmeter.webrunner.model.File;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import io.github.colinzhu.jmeter.webrunner.service.FileStorageService;
import io.github.colinzhu.jmeter.webrunner.service.PersistenceService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Property-based tests for FileController
 * Feature: jmeter-web-runner
 */
class FileControllerPropertyTest {

    private MockMvc mockMvc;
    private FileStorageService fileStorageService;
    private FileRepository fileRepository;
    private StorageConfig storageConfig;

    /**
     * Helper method to create a FileRepository with mocked persistence
     */
    private FileRepository createFileRepository() {
        PersistenceService persistenceService = mock(PersistenceService.class);
        doNothing().when(persistenceService).save(anyString(), any());
        return new FileRepository(persistenceService);
    }

    private void setupMockMvc() throws Exception {
        // Create a temporary directory for testing
        Path tempDir = Files.createTempDirectory("jmeter-test");

        storageConfig = mock(StorageConfig.class);
        when(storageConfig.getUploadDir()).thenReturn(tempDir.toString());

        fileRepository = mock(FileRepository.class);
        when(fileRepository.save(any(File.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fileStorageService = new FileStorageService(storageConfig, fileRepository);

        FileController fileController = new FileController(fileStorageService);

        mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Property 2: Upload Response Completeness
     * For any file upload (successful or failed), the API response should include
     * appropriate feedback - either confirmation with filename for success, or a
     * descriptive error message for failure.
     * <p>
     * Feature: jmeter-web-runner, Property 2: Upload Response Completeness
     * Validates: Requirements 1.3, 1.4
     */
    @Property(tries = 100)
    void uploadResponseCompleteness_providesAppropriateResponseForAllUploads(
            @ForAll("validUploadScenarios") UploadScenario validScenario,
            @ForAll("invalidUploadScenarios") UploadScenario invalidScenario) throws Exception {

        setupMockMvc();

        // Test 1: Valid uploads should return confirmation with filename
        MvcResult validResult = mockMvc.perform(
                multipart("/api/files")
                        .file(validScenario.file)
        ).andReturn();

        int validStatus = validResult.getResponse().getStatus();
        String validResponseBody = validResult.getResponse().getContentAsString();

        if (validStatus == 201) {
            // Success response should contain filename
            assertThat(validResponseBody)
                    .as("Successful upload response should contain filename")
                    .contains("filename")
                    .contains(validScenario.file.getOriginalFilename());

            // Success response should contain id
            assertThat(validResponseBody)
                    .as("Successful upload response should contain file ID")
                    .contains("id");

            // Success response should contain size
            assertThat(validResponseBody)
                    .as("Successful upload response should contain file size")
                    .contains("size");

            // Success response should contain uploadedAt timestamp
            assertThat(validResponseBody)
                    .as("Successful upload response should contain upload timestamp")
                    .contains("uploadedAt");
        }

        // Test 2: Invalid uploads should return descriptive error message
        MvcResult invalidResult = mockMvc.perform(
                multipart("/api/files")
                        .file(invalidScenario.file)
        ).andReturn();

        int invalidStatus = invalidResult.getResponse().getStatus();
        String invalidResponseBody = invalidResult.getResponse().getContentAsString();

        // Invalid uploads should return error status (4xx or 5xx)
        assertThat(invalidStatus)
                .as("Invalid upload should return error status code")
                .isGreaterThanOrEqualTo(400);

        // Error response should contain "error" field
        assertThat(invalidResponseBody)
                .as("Failed upload response should contain error field")
                .contains("error");

        // Error message should be descriptive (not empty)
        assertThat(invalidResponseBody)
                .as("Failed upload response should contain descriptive error message")
                .hasSizeGreaterThan(20); // Ensure it's not just {"error":""}
    }

    /**
     * Provides valid upload scenarios with proper JMX files
     */
    @Provide
    Arbitrary<UploadScenario> validUploadScenarios() {
        Arbitrary<String> validFilenames = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(name -> name + ".jmx");

        Arbitrary<String> validXmlContent = Arbitraries.of(
                "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><jmeterTestPlan version=\"1.2\"></jmeterTestPlan>",
                "<?xml version=\"1.0\"?><jmeterTestPlan><ThreadGroup></ThreadGroup></jmeterTestPlan>"
        );

        return Combinators.combine(validFilenames, validXmlContent)
                .as((filename, content) -> {
                    MockMultipartFile file = new MockMultipartFile(
                            "file",
                            filename,
                            MediaType.APPLICATION_XML_VALUE,
                            content.getBytes()
                    );
                    return new UploadScenario(file, true);
                });
    }

    /**
     * Provides invalid upload scenarios with various error conditions
     */
    @Provide
    Arbitrary<UploadScenario> invalidUploadScenarios() {
        // Invalid extension scenarios
        Arbitrary<UploadScenario> invalidExtension = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(name -> {
                    String filename = name + ".txt"; // Wrong extension
                    String validXml = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";
                    MockMultipartFile file = new MockMultipartFile(
                            "file",
                            filename,
                            MediaType.TEXT_PLAIN_VALUE,
                            validXml.getBytes()
                    );
                    return new UploadScenario(file, false);
                });

        // Invalid XML scenarios
        Arbitrary<UploadScenario> invalidXml = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(name -> {
                    String filename = name + ".jmx";
                    String invalidXmlContent = "This is not XML at all!";
                    MockMultipartFile file = new MockMultipartFile(
                            "file",
                            filename,
                            MediaType.APPLICATION_XML_VALUE,
                            invalidXmlContent.getBytes()
                    );
                    return new UploadScenario(file, false);
                });

        // Empty file scenarios
        Arbitrary<UploadScenario> emptyFile = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(name -> {
                    String filename = name + ".jmx";
                    MockMultipartFile file = new MockMultipartFile(
                            "file",
                            filename,
                            MediaType.APPLICATION_XML_VALUE,
                            new byte[0]
                    );
                    return new UploadScenario(file, false);
                });

        return Arbitraries.oneOf(invalidExtension, invalidXml, emptyFile);
    }

    /**
     * Property 13: File List Completeness
     * For any set of uploaded files, the list endpoint should return all files
     * with complete metadata including filename, upload date, and size.
     * <p>
     * Feature: jmeter-web-runner, Property 13: File List Completeness
     * Validates: Requirements 5.1, 5.2
     */
    @Property(tries = 100)
    void fileListCompleteness_returnsAllFilesWithCompleteMetadata(
            @ForAll @Size(min = 1, max = 5) List<@From("validJmxFiles") MockMultipartFile> filesToUpload) throws Exception {

        // Create a fresh repository for this test iteration
        FileRepository fileRepository = createFileRepository();

        // Create a temporary directory for testing
        Path tempDir = Files.createTempDirectory("jmeter-test");

        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getUploadDir()).thenReturn(tempDir.toString());

        FileStorageService fileStorageService = new FileStorageService(storageConfig, fileRepository);
        FileController fileController = new FileController(fileStorageService);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Upload all files
        for (MockMultipartFile file : filesToUpload) {
            mockMvc.perform(
                    multipart("/api/files")
                            .file(file)
            ).andReturn();
        }

        // Get the list of files
        MvcResult listResult = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files")
        ).andReturn();

        String responseBody = listResult.getResponse().getContentAsString();
        int statusCode = listResult.getResponse().getStatus();

        // Verify successful response
        assertThat(statusCode)
                .as("List files endpoint should return 200 OK")
                .isEqualTo(200);

        // Verify response is a JSON array
        assertThat(responseBody)
                .as("Response should be a JSON array")
                .startsWith("[")
                .endsWith("]");

        // Verify all uploaded files are in the list
        for (MockMultipartFile uploadedFile : filesToUpload) {
            assertThat(responseBody)
                    .as("List should contain filename: " + uploadedFile.getOriginalFilename())
                    .contains(uploadedFile.getOriginalFilename());
        }

        // Verify each file entry has complete metadata
        // Count the number of file entries (each should have an "id" field)
        int idCount = responseBody.split("\"id\"").length - 1;
        assertThat(idCount)
                .as("Number of files in list should match number of uploaded files")
                .isEqualTo(filesToUpload.size());

        // Verify all required fields are present for each file
        for (MockMultipartFile uploadedFile : filesToUpload) {
            // Each file should have: id, filename, size, uploadedAt
            assertThat(responseBody)
                    .as("Each file should have complete metadata")
                    .contains("\"filename\"")
                    .contains("\"size\"")
                    .contains("\"uploadedAt\"");
        }

        // Verify size values are present and match uploaded files
        for (MockMultipartFile uploadedFile : filesToUpload) {
            String sizeStr = String.valueOf(uploadedFile.getSize());
            assertThat(responseBody)
                    .as("List should contain size for file: " + uploadedFile.getOriginalFilename())
                    .contains(sizeStr);
        }

        // Clean up temp directory
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Provides valid JMX files for testing
     */
    @Provide
    Arbitrary<MockMultipartFile> validJmxFiles() {
        Arbitrary<String> validFilenames = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(name -> name + ".jmx");

        Arbitrary<String> validXmlContent = Arbitraries.of(
                "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><jmeterTestPlan version=\"1.2\"></jmeterTestPlan>",
                "<?xml version=\"1.0\"?><jmeterTestPlan><ThreadGroup></ThreadGroup></jmeterTestPlan>"
        );

        return Combinators.combine(validFilenames, validXmlContent)
                .as((filename, content) -> new MockMultipartFile(
                        "file",
                        filename,
                        MediaType.APPLICATION_XML_VALUE,
                        content.getBytes()
                ));
    }

    /**
     * Property 15: File Deletion Consistency
     * For any file that is deleted, subsequent list requests should not include
     * that file, confirming removal from storage.
     * <p>
     * Feature: jmeter-web-runner, Property 15: File Deletion Consistency
     * Validates: Requirements 5.4
     */
    @Property(tries = 100)
    void fileDeletionConsistency_deletedFilesNotInSubsequentLists(
            @ForAll @Size(min = 2, max = 5) List<@From("validJmxFiles") MockMultipartFile> filesToUpload) throws Exception {

        // Create a fresh repository for this test iteration
        FileRepository fileRepository = createFileRepository();

        // Create a temporary directory for testing
        Path tempDir = Files.createTempDirectory("jmeter-test");

        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getUploadDir()).thenReturn(tempDir.toString());

        FileStorageService fileStorageService = new FileStorageService(storageConfig, fileRepository);
        FileController fileController = new FileController(fileStorageService);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Upload all files and collect their IDs
        List<String> uploadedFileIds = new ArrayList<>();
        for (MockMultipartFile file : filesToUpload) {
            MvcResult uploadResult = mockMvc.perform(
                    multipart("/api/files")
                            .file(file)
            ).andReturn();

            String responseBody = uploadResult.getResponse().getContentAsString();
            // Extract the ID from the response (simple JSON parsing)
            String id = responseBody.split("\"id\":\"")[1].split("\"")[0];
            uploadedFileIds.add(id);
        }

        // Verify all files are in the initial list
        MvcResult initialListResult = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files")
        ).andReturn();

        String initialListBody = initialListResult.getResponse().getContentAsString();
        for (String fileId : uploadedFileIds) {
            assertThat(initialListBody)
                    .as("Initial list should contain file ID: " + fileId)
                    .contains(fileId);
        }

        // Delete the first file
        String deletedFileId = uploadedFileIds.get(0);
        MvcResult deleteResult = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/files/" + deletedFileId)
        ).andReturn();

        // Verify deletion was successful
        assertThat(deleteResult.getResponse().getStatus())
                .as("Delete operation should return 200 OK")
                .isEqualTo(200);

        // Get the list of files after deletion
        MvcResult afterDeleteListResult = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files")
        ).andReturn();

        String afterDeleteListBody = afterDeleteListResult.getResponse().getContentAsString();

        // Verify the deleted file is NOT in the list
        assertThat(afterDeleteListBody)
                .as("List after deletion should NOT contain deleted file ID: " + deletedFileId)
                .doesNotContain(deletedFileId);

        // Verify all other files are still in the list
        for (int i = 1; i < uploadedFileIds.size(); i++) {
            String remainingFileId = uploadedFileIds.get(i);
            assertThat(afterDeleteListBody)
                    .as("List after deletion should still contain non-deleted file ID: " + remainingFileId)
                    .contains(remainingFileId);
        }

        // Verify the count is correct (original count - 1)
        int originalCount = uploadedFileIds.size();
        int expectedCountAfterDelete = originalCount - 1;

        // Count the number of file entries in the response
        int actualCountAfterDelete = afterDeleteListBody.split("\"id\"").length - 1;
        assertThat(actualCountAfterDelete)
                .as("Number of files after deletion should be one less than original")
                .isEqualTo(expectedCountAfterDelete);

        // Clean up temp directory
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Property 14: File Operations Availability
     * For any uploaded file in the system, both execute and delete operations
     * should be available and functional.
     * <p>
     * Feature: jmeter-web-runner, Property 14: File Operations Availability
     * Validates: Requirements 5.3
     */
    @Property(tries = 100)
    void fileOperationsAvailability_deleteOperationFunctionalForAllFiles(
            @ForAll @Size(min = 1, max = 3) List<@From("validJmxFiles") MockMultipartFile> filesToUpload) throws Exception {

        // Create a fresh repository for this test iteration
        FileRepository fileRepository = createFileRepository();

        // Create a temporary directory for testing
        Path tempDir = Files.createTempDirectory("jmeter-test");

        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getUploadDir()).thenReturn(tempDir.toString());

        FileStorageService fileStorageService = new FileStorageService(storageConfig, fileRepository);
        FileController fileController = new FileController(fileStorageService);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Upload all files and collect their IDs
        List<String> uploadedFileIds = new ArrayList<>();
        for (MockMultipartFile file : filesToUpload) {
            MvcResult uploadResult = mockMvc.perform(
                    multipart("/api/files")
                            .file(file)
            ).andReturn();

            String responseBody = uploadResult.getResponse().getContentAsString();
            // Extract the ID from the response
            String id = responseBody.split("\"id\":\"")[1].split("\"")[0];
            uploadedFileIds.add(id);
        }

        // For each uploaded file, verify delete operation is available and functional
        for (String fileId : uploadedFileIds) {
            // Verify the file exists before deletion
            MvcResult listBeforeDelete = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files")
            ).andReturn();

            String listBodyBefore = listBeforeDelete.getResponse().getContentAsString();
            assertThat(listBodyBefore)
                    .as("File should exist before deletion: " + fileId)
                    .contains(fileId);

            // Attempt to delete the file
            MvcResult deleteResult = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/files/" + fileId)
            ).andReturn();

            // Verify delete operation is available (returns success status)
            int deleteStatus = deleteResult.getResponse().getStatus();
            assertThat(deleteStatus)
                    .as("Delete operation should be available and return 200 OK for file: " + fileId)
                    .isEqualTo(200);

            // Verify delete operation is functional (file is actually removed)
            MvcResult listAfterDelete = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files")
            ).andReturn();

            String listBodyAfter = listAfterDelete.getResponse().getContentAsString();
            assertThat(listBodyAfter)
                    .as("File should be removed after deletion: " + fileId)
                    .doesNotContain(fileId);

            // Verify the delete response contains a success message
            String deleteResponseBody = deleteResult.getResponse().getContentAsString();
            assertThat(deleteResponseBody)
                    .as("Delete response should contain success message")
                    .contains("message");
        }

        // Verify all files have been deleted
        MvcResult finalListResult = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files")
        ).andReturn();

        String finalListBody = finalListResult.getResponse().getContentAsString();

        // The list should be empty or contain no files
        int finalFileCount = finalListBody.split("\"id\"").length - 1;
        assertThat(finalFileCount)
                .as("All files should be deleted, list should be empty")
                .isEqualTo(0);

        // Clean up temp directory
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /**
         * Helper class to represent an upload scenario
         */
        record UploadScenario(MockMultipartFile file, boolean shouldSucceed) {
    }
}

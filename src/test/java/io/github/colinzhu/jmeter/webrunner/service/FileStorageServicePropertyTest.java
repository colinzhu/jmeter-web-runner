package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.exception.InvalidFileException;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import net.jqwik.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for FileStorageService
 * Feature: jmeter-web-runner
 */
class FileStorageServicePropertyTest {

    private FileStorageService createFileStorageService() throws Exception {
        // Create a temporary directory for testing
        Path tempDir = Files.createTempDirectory("jmeter-test");

        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getUploadDir()).thenReturn(tempDir.toString());

        FileRepository fileRepository = mock(FileRepository.class);
        // Mock the save method to return the file as-is
        when(fileRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        return new FileStorageService(storageConfig, fileRepository);
    }

    /**
     * Property 1: File Extension Validation
     * For any file upload request, the system should accept files with .jmx extension
     * and reject files without .jmx extension.
     * <p>
     * Feature: jmeter-web-runner, Property 1: File Extension Validation
     * Validates: Requirements 1.1, 1.2
     */
    @Property(tries = 100)
    void fileExtensionValidation_acceptsJmxFiles_rejectsNonJmxFiles(
            @ForAll("validJmxFilenames") String jmxFilename,
            @ForAll("invalidJmxFilenames") String nonJmxFilename) throws Exception {

        FileStorageService fileStorageService = createFileStorageService();

        // Create valid XML content for testing
        String validXmlContent = "<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>";
        byte[] xmlBytes = validXmlContent.getBytes();

        // Test 1: Files with .jmx extension should be accepted (not throw InvalidFileException)
        MultipartFile validFile = new MockMultipartFile(
                "file",
                jmxFilename,
                "application/xml",
                xmlBytes
        );

        // The validation should not throw InvalidFileException for valid .jmx files
        try {
            fileStorageService.storeFile(validFile);
            // If we get here, the file was accepted (validation passed)
        } catch (InvalidFileException e) {
            // If InvalidFileException is thrown, it should NOT be about file extension
            assertThat(e.getMessage())
                    .as("Valid .jmx file '%s' should not fail extension validation", jmxFilename)
                    .doesNotContain("Invalid file type", "Only .jmx files are accepted");
        } catch (Exception e) {
            // Other exceptions (like StorageException) are acceptable for this test
            // We're only testing validation logic, not storage logic
        }

        // Test 2: Files without .jmx extension should be rejected
        MultipartFile invalidFile = new MockMultipartFile(
                "file",
                nonJmxFilename,
                "application/xml",
                xmlBytes
        );

        // Should throw InvalidFileException with appropriate message
        assertThatThrownBy(() -> fileStorageService.storeFile(invalidFile))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Invalid file type")
                .hasMessageContaining("Only .jmx files are accepted");
    }

    /**
     * Provides valid .jmx filenames with various formats
     */
    @Provide
    Arbitrary<String> validJmxFilenames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(50)
                .map(name -> name + ".jmx")
                .filter(name -> !name.isEmpty());
    }

    /**
     * Provides invalid filenames (without .jmx extension or with wrong extensions)
     * Note: Case variations like .JMX, .Jmx are NOT included because the validation
     * uses toLowerCase(), so they are actually valid
     */
    @Provide
    Arbitrary<String> invalidJmxFilenames() {
        Arbitrary<String> baseNames = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(50);

        Arbitrary<String> wrongExtensions = Arbitraries.of(
                ".xml", ".txt", ".json", ".yaml", ".yml",
                ".jmeter", // Similar but not .jmx
                "", ".jm", ".mx", ".jmxx", ".jmx.txt", ".jmx.bak"
        );

        return Combinators.combine(baseNames, wrongExtensions)
                .as((name, ext) -> name + ext);
    }

    /**
     * Property 3: XML Format Validation
     * For any uploaded file with .jmx extension, the system should validate that
     * the file content is valid XML format before accepting it.
     * <p>
     * Feature: jmeter-web-runner, Property 3: XML Format Validation
     * Validates: Requirements 1.5
     */
    @Property(tries = 100)
    void xmlFormatValidation_acceptsValidXml_rejectsInvalidXml(
            @ForAll("validXmlContent") String validXml,
            @ForAll("invalidXmlContent") String invalidXml) throws Exception {

        FileStorageService fileStorageService = createFileStorageService();
        String filename = "test.jmx";

        // Test 1: Valid XML content should be accepted (not throw InvalidFileException for XML validation)
        MultipartFile validFile = new MockMultipartFile(
                "file",
                filename,
                "application/xml",
                validXml.getBytes()
        );

        try {
            fileStorageService.storeFile(validFile);
            // If we get here, the file was accepted (XML validation passed)
        } catch (InvalidFileException e) {
            // If InvalidFileException is thrown, it should NOT be about XML validation
            assertThat(e.getMessage())
                    .as("Valid XML should not fail XML validation")
                    .doesNotContain("Invalid JMX file", "valid XML format");
        } catch (Exception e) {
            // Other exceptions (like StorageException) are acceptable for this test
            // We're only testing XML validation logic, not storage logic
        }

        // Test 2: Invalid XML content should be rejected
        MultipartFile invalidFile = new MockMultipartFile(
                "file",
                filename,
                "application/xml",
                invalidXml.getBytes()
        );

        // Should throw InvalidFileException with appropriate message
        assertThatThrownBy(() -> fileStorageService.storeFile(invalidFile))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Invalid JMX file")
                .hasMessageContaining("valid XML format");
    }

    /**
     * Provides valid XML content for testing
     */
    @Provide
    Arbitrary<String> validXmlContent() {
        // Generate various valid XML structures
        Arbitrary<String> simpleXml = Arbitraries.just("<?xml version=\"1.0\"?><jmeterTestPlan></jmeterTestPlan>");

        Arbitrary<String> xmlWithAttributes = Arbitraries.just(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><jmeterTestPlan version=\"1.2\"></jmeterTestPlan>"
        );

        Arbitrary<String> xmlWithNestedElements = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(tag -> String.format(
                        "<?xml version=\"1.0\"?><jmeterTestPlan><%s></%s></jmeterTestPlan>",
                        tag, tag
                ));

        Arbitrary<String> xmlWithContent = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ')
                .ofMinLength(0)
                .ofMaxLength(50)
                .map(content -> String.format(
                        "<?xml version=\"1.0\"?><jmeterTestPlan><element>%s</element></jmeterTestPlan>",
                        content
                ));

        return Arbitraries.oneOf(simpleXml, xmlWithAttributes, xmlWithNestedElements, xmlWithContent);
    }

    /**
     * Provides invalid XML content for testing
     */
    @Provide
    Arbitrary<String> invalidXmlContent() {
        // Generate various invalid XML structures
        Arbitrary<String> notXml = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '!', '@', '#')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.trim().startsWith("<?xml")); // Ensure it doesn't accidentally look like XML

        Arbitrary<String> unclosedTag = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(tag -> String.format("<?xml version=\"1.0\"?><%s>", tag));

        Arbitrary<String> mismatchedTags = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(tag -> String.format("<?xml version=\"1.0\"?><%s></%s_different>", tag, tag));

        Arbitrary<String> invalidCharacters = Arbitraries.just(
                "<?xml version=\"1.0\"?><test>\u0000</test>" // Null character is invalid in XML
        );

        Arbitrary<String> malformedDeclaration = Arbitraries.just(
                "<?xml version=\"1.0\" <root></root>" // Missing closing ?>
        );

        return Arbitraries.oneOf(notXml, unclosedTag, mismatchedTags, invalidCharacters, malformedDeclaration);
    }
}

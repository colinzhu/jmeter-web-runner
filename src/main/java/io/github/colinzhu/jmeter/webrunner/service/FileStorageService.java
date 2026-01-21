package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.exception.InvalidFileException;
import io.github.colinzhu.jmeter.webrunner.exception.ResourceNotFoundException;
import io.github.colinzhu.jmeter.webrunner.exception.StorageException;
import io.github.colinzhu.jmeter.webrunner.model.File;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {
    private final StorageConfig storageConfig;
    private final FileRepository fileRepository;

    public File storeFile(MultipartFile file) {
        validateFile(file);

        String fileId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(storageConfig.getUploadDir());
            Files.createDirectories(uploadPath);

            // Generate storage path
            String storagePath = fileId + ".jmx";
            Path destinationPath = uploadPath.resolve(storagePath);

            // Save file to disk
            Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

            // Create and save file metadata
            File fileEntity = File.builder()
                    .id(fileId)
                    .filename(filename)
                    .size(file.getSize())
                    .uploadedAt(Instant.now())
                    .path(destinationPath.toString())
                    .build();

            return fileRepository.save(fileEntity);

        } catch (IOException e) {
            log.error("Failed to store file: {}", filename, e);
            throw new StorageException("Failed to store file. Please try again.", e);
        }
    }

    public List<File> getAllFiles() {
        return fileRepository.findAll();
    }

    public void deleteFile(String fileId) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

        try {
            // Delete physical file from disk
            Path filePath = Paths.get(file.getPath());
            Files.deleteIfExists(filePath);

            // Delete metadata from repository
            fileRepository.deleteById(fileId);

            log.info("File deleted successfully: {} with ID: {}", file.getFilename(), fileId);

        } catch (IOException e) {
            log.error("Failed to delete file: {}", file.getFilename(), e);
            throw new StorageException("Failed to delete file. Please try again.", e);
        }
    }

    public File getFile(String fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty or null.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".jmx")) {
            throw new InvalidFileException("Invalid file type. Only .jmx files are accepted.");
        }

        validateXmlFormat(file);
    }

    private void validateXmlFormat(MultipartFile file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new ByteArrayInputStream(file.getBytes()));

        } catch (Exception e) {
            log.error("Invalid XML format in file: {}", file.getOriginalFilename(), e);
            throw new InvalidFileException("Invalid JMX file. File must be valid XML format.");
        }
    }
}

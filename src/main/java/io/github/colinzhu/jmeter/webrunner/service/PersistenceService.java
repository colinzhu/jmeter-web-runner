package io.github.colinzhu.jmeter.webrunner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for persisting application state to disk as JSON files.
 * Handles serialization and deserialization of configuration and metadata.
 */
@Service
@Slf4j
public class PersistenceService {

    private final ObjectMapper objectMapper;
    private final Path storageDirectory;

    public PersistenceService(@Value("${app.storage.location:storage}") String storageLocation) {
        this.storageDirectory = Paths.get(storageLocation);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Ensure storage directory exists
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", storageDirectory, e);
        }
    }

    /**
     * Save an object to a JSON file
     */
    public <T> void save(String filename, T data) {
        Path filePath = storageDirectory.resolve(filename);
        try {
            objectMapper.writeValue(filePath.toFile(), data);
            log.debug("Saved data to: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save data to: {}", filePath, e);
            throw new RuntimeException("Failed to persist data", e);
        }
    }

    /**
     * Load an object from a JSON file
     */
    public <T> T load(String filename, Class<T> type) {
        Path filePath = storageDirectory.resolve(filename);
        if (!Files.exists(filePath)) {
            log.debug("File does not exist: {}", filePath);
            return null;
        }

        try {
            T data = objectMapper.readValue(filePath.toFile(), type);
            log.debug("Loaded data from: {}", filePath);
            return data;
        } catch (IOException e) {
            log.error("Failed to load data from: {}", filePath, e);
            return null;
        }
    }

    /**
     * Delete a JSON file
     */
    public void delete(String filename) {
        Path filePath = storageDirectory.resolve(filename);
        try {
            Files.deleteIfExists(filePath);
            log.debug("Deleted file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
        }
    }

    /**
     * Check if a file exists
     */
    public boolean exists(String filename) {
        Path filePath = storageDirectory.resolve(filename);
        return Files.exists(filePath);
    }
}

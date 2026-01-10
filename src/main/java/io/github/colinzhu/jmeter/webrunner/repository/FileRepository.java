package io.github.colinzhu.jmeter.webrunner.repository;

import io.github.colinzhu.jmeter.webrunner.model.File;
import io.github.colinzhu.jmeter.webrunner.model.FileMetadataState;
import io.github.colinzhu.jmeter.webrunner.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class FileRepository {
    private static final String FILE_METADATA_FILE = "file-metadata.json";
    private final Map<String, File> files = new ConcurrentHashMap<>();
    private final PersistenceService persistenceService;

    public FileRepository(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Load persisted file metadata on application startup
     */
    @PostConstruct
    public void init() {
        FileMetadataState state = persistenceService.load(FILE_METADATA_FILE, FileMetadataState.class);
        if (state != null && state.getFiles() != null) {
            // Only load files that still exist on disk
            for (File file : state.getFiles()) {
                if (Files.exists(Paths.get(file.getPath()))) {
                    files.put(file.getId(), file);
                    log.debug("Loaded file metadata: {} ({})", file.getFilename(), file.getId());
                } else {
                    log.warn("Skipping file metadata for missing file: {} ({})", file.getFilename(), file.getId());
                }
            }
            log.info("Loaded {} file metadata entries from disk", files.size());
        }
    }

    public File save(File file) {
        files.put(file.getId(), file);
        saveMetadata();
        return file;
    }

    public Optional<File> findById(String id) {
        return Optional.ofNullable(files.get(id));
    }

    public List<File> findAll() {
        return new ArrayList<>(files.values());
    }

    public void deleteById(String id) {
        files.remove(id);
        saveMetadata();
    }

    public boolean existsById(String id) {
        return files.containsKey(id);
    }

    /**
     * Save current file metadata to disk
     */
    private void saveMetadata() {
        FileMetadataState state = FileMetadataState.builder()
                .files(new ArrayList<>(files.values()))
                .build();

        persistenceService.save(FILE_METADATA_FILE, state);
        log.debug("Persisted file metadata ({} files)", files.size());
    }
}

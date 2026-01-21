package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.model.File;
import io.github.colinzhu.jmeter.webrunner.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("Received file upload request: {}", file.getOriginalFilename());

        File savedFile = fileStorageService.storeFile(file);

        Map<String, Object> response = new HashMap<>();
        response.put("id", savedFile.getId());
        response.put("filename", savedFile.getFilename());
        response.put("size", savedFile.getSize());
        response.put("uploadedAt", savedFile.getUploadedAt().toString());

        log.info("File uploaded successfully: {} with ID: {}", savedFile.getFilename(), savedFile.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listFiles() {
        log.info("Received request to list all files");

        List<File> files = fileStorageService.getAllFiles();

        List<Map<String, Object>> response = files.stream()
                .map(file -> {
                    Map<String, Object> fileData = new HashMap<>();
                    fileData.put("id", file.getId());
                    fileData.put("filename", file.getFilename());
                    fileData.put("size", file.getSize());
                    fileData.put("uploadedAt", file.getUploadedAt().toString());
                    return fileData;
                })
                .toList();

        log.info("Returning {} files", response.size());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable String id) {
        log.info("Received request to delete file with ID: {}", id);

        fileStorageService.deleteFile(id);

        Map<String, String> response = new HashMap<>();
        response.put("message", "File deleted successfully");

        log.info("File deleted successfully with ID: {}", id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String id) {
        log.info("Received request to download file with ID: {}", id);

        File file = fileStorageService.getFile(id);
        Path filePath = Paths.get(file.getPath());
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists()) {
            log.error("File not found at path: {}", file.getPath());
            return ResponseEntity.notFound().build();
        }

        log.info("File download initiated: {} with ID: {}", file.getFilename(), id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}

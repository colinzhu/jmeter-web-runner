package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.exception.ResourceNotFoundException;
import io.github.colinzhu.jmeter.webrunner.model.Execution;
import io.github.colinzhu.jmeter.webrunner.repository.FileRepository;
import io.github.colinzhu.jmeter.webrunner.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
@Slf4j
public class ExecutionController {
    private final ExecutionService executionService;
    private final FileRepository fileRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> startExecution(@RequestBody Map<String, String> request) {
        String fileId = request.get("fileId");

        if (fileId == null || fileId.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "fileId is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        log.info("Received execution request for file: {}", fileId);

        Execution execution = executionService.createExecution(fileId);

        Map<String, Object> response = buildExecutionResponse(execution);

        log.info("Execution created with ID: {}", execution.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getExecutionStatus(@PathVariable String id) {
        log.info("Received request for execution status: {}", id);

        Execution execution = executionService.getExecution(id);

        Map<String, Object> response = buildExecutionResponse(execution);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listExecutions() {
        log.debug("Received request to list all executions");

        List<Execution> executions = executionService.getAllExecutions();

        List<Map<String, Object>> response = executions.stream()
                .map(this::buildExecutionResponse)
                .collect(Collectors.toList());

        log.debug("Returning {} executions", response.size());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelExecution(@PathVariable String id) {
        log.info("Received cancellation request for execution: {}", id);

        try {
            Execution execution = executionService.cancelExecution(id);
            Map<String, Object> response = buildExecutionResponse(execution);
            log.info("Execution {} cancelled successfully", id);
            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Execution not found: " + id);
            log.warn("Cancellation failed - execution not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);

        } catch (IllegalStateException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            log.warn("Cancellation failed for execution {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    private Map<String, Object> buildExecutionResponse(Execution execution) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", execution.getId());
        response.put("fileId", execution.getFileId());
        response.put("status", execution.getStatus().toString().toLowerCase());
        response.put("queuePosition", execution.getQueuePosition());
        response.put("createdAt", execution.getCreatedAt().toString());

        // Add filename by looking up the file
        fileRepository.findById(execution.getFileId()).ifPresent(file -> {
            response.put("filename", file.getFilename());
        });

        if (execution.getStartedAt() != null) {
            response.put("startedAt", execution.getStartedAt().toString());
        }

        if (execution.getCompletedAt() != null) {
            response.put("completedAt", execution.getCompletedAt().toString());
        }

        if (execution.getDuration() != null) {
            response.put("duration", execution.getDuration());
        }

        if (execution.getReportId() != null) {
            response.put("reportId", execution.getReportId());
        }

        if (execution.getError() != null) {
            response.put("error", execution.getError());
        }

        return response;
    }
}

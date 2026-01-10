# Implementation Plan: JMeter Web Runner

## Overview

This implementation plan breaks down the JMeter Web Runner into discrete coding tasks using Java and Spring Boot. The plan follows an incremental approach, building core functionality first, then adding execution management, report handling, and finally queue management for concurrent executions.

## Tasks

- [x] 1. Set up Spring Boot project structure and dependencies
  - Create Spring Boot application with Maven/Gradle
  - Add dependencies: Spring Web, Spring Boot DevTools, Lombok
  - Configure application properties (server port, file storage paths, max file size)
  - Create package structure: controller, service, model, repository, config
  - Set up basic error handling with @ControllerAdvice
  - _Requirements: All_

- [x] 2. Implement file upload and storage
  - [x] 2.1 Create File model and FileRepository
    - Define File entity with id, filename, size, uploadedAt, path
    - Create in-memory or file-based repository for storing file metadata
    - _Requirements: 5.1, 5.2_

  - [x] 2.2 Implement FileStorageService
    - Create service for saving files to disk
    - Implement file validation (extension check, XML validation)
    - Generate unique file IDs and storage paths
    - _Requirements: 1.1, 1.2, 1.5_

  - [x] 2.3 Write property test for file extension validation

    - **Property 1: File Extension Validation**
    - **Validates: Requirements 1.1, 1.2**

  - [x] 2.4 Write property test for XML validation

    - **Property 3: XML Format Validation**
    - **Validates: Requirements 1.5**

  - [x] 2.5 Create FileController with upload endpoint
    - Implement POST /api/files endpoint
    - Handle multipart file upload
    - Return file metadata on success
    - Return appropriate error responses
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 2.6 Write property test for upload response completeness

    - **Property 2: Upload Response Completeness**
    - **Validates: Requirements 1.3, 1.4**

  - [x] 2.7 Write unit tests for file upload edge cases

    - Test empty files
    - Test files at size limits
    - Test special characters in filenames
    - _Requirements: 1.1, 1.2, 1.5_

- [x] 3. Implement file management endpoints
  - [x] 3.1 Add list files endpoint to FileController
    - Implement GET /api/files endpoint
    - Return list of all uploaded files with metadata
    - _Requirements: 5.1, 5.2_

  - [x] 3.2 Write property test for file list completeness

    - **Property 13: File List Completeness**
    - **Validates: Requirements 5.1, 5.2**

  - [x] 3.3 Add delete file endpoint to FileController
    - Implement DELETE /api/files/{id} endpoint
    - Remove file from storage and metadata
    - _Requirements: 5.3, 5.4_

  - [x] 3.4 Write property test for file deletion consistency

    - **Property 15: File Deletion Consistency**
    - **Validates: Requirements 5.4**

  - [x] 3.5 Write property test for file operations availability

    - **Property 14: File Operations Availability**
    - **Validates: Requirements 5.3**

- [x] 4. Checkpoint - Ensure file management works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement execution models and basic execution service
  - [x] 5.1 Create Execution model and ExecutionRepository
    - Define Execution entity with id, fileId, status, timestamps, reportId, error
    - Create repository for storing execution metadata
    - Define ExecutionStatus enum (QUEUED, RUNNING, COMPLETED, FAILED)
    - _Requirements: 2.1, 2.2_

  - [x] 5.2 Create ExecutionService with basic execution creation
    - Implement method to create execution record
    - Set initial status to QUEUED
    - _Requirements: 2.1_

  - [x] 5.3 Write property test for execution creation

    - **Property 4: Execution Creation**
    - **Validates: Requirements 2.1**

  - [x] 5.4 Create ExecutionController with start and status endpoints
    - Implement POST /api/executions endpoint
    - Implement GET /api/executions/{id} endpoint
    - Implement GET /api/executions endpoint (list all)
    - _Requirements: 2.1, 2.2, 6.5_

  - [x] 5.5 Write property test for execution status tracking

    - **Property 5: Execution Status Tracking**
    - **Validates: Requirements 2.2, 3.3**

  - [x] 5.6 Write property test for execution list completeness

    - **Property 19: Execution List Completeness**
    - **Validates: Requirements 6.5**

- [x] 6. Implement JMeter execution engine
  - [x] 6.1 Create JMeterEngine service
    - Implement method to execute JMeter via ProcessBuilder
    - Build JMeter command: `jmeter -n -t {test} -l {results} -e -o {report}`
    - Capture stdout and stderr
    - Parse exit codes
    - _Requirements: 2.1, 2.5_

  - [x] 6.2 Implement report storage in JMeterEngine
    - Create report directory structure
    - Store generated reports with execution ID
    - Handle report generation failures
    - _Requirements: 2.5_

  - [x] 6.3 Update ExecutionService to use JMeterEngine
    - Call JMeterEngine when execution starts
    - Update execution status based on results
    - Store error messages on failure
    - Create report record on success
    - _Requirements: 2.1, 2.3, 2.4, 2.5_

  - [x] 6.4 Write property test for successful execution completion
    - **Property 6: Successful Execution Completion**
    - **Validates: Requirements 2.3, 2.5, 3.1**

  - [x] 6.5 Write property test for failed execution error capture
    - **Property 7: Failed Execution Error Capture**
    - **Validates: Requirements 2.4**

  - [x] 6.6 Write unit tests for JMeter execution scenarios
    - Test with valid JMX file
    - Test with invalid JMX file
    - Test JMeter not found scenario
    - _Requirements: 2.1, 2.4_

- [x] 7. Implement report viewing and downloading
  - [x] 7.1 Create Report model and ReportRepository
    - Define Report entity with id, executionId, path, createdAt, size
    - Create repository for report metadata
    - _Requirements: 3.1, 4.1_

  - [x] 7.2 Create ReportService
    - Implement method to retrieve report HTML
    - Implement method to package report as ZIP
    - Handle missing reports
    - _Requirements: 3.2, 4.1, 4.2, 4.3, 4.4_

  - [x] 7.3 Create ReportController with view and download endpoints
    - Implement GET /api/reports/{id} endpoint (serve HTML)
    - Implement GET /api/reports/{id}/download endpoint (serve ZIP)
    - Set appropriate content types and headers
    - _Requirements: 3.2, 4.1, 4.2, 4.3_

  - [x] 7.4 Write property test for report accessibility
    - **Property 8: Report Accessibility**
    - **Validates: Requirements 3.2**

  - [x] 7.5 Write property test for multiple report support
    - **Property 9: Multiple Report Support**
    - **Validates: Requirements 3.5**

  - [x] 7.6 Write property test for report download availability
    - **Property 10: Report Download Availability**
    - **Validates: Requirements 4.1, 4.3**

  - [x] 7.7 Write property test for report archive completeness
    - **Property 11: Report Archive Completeness**
    - **Validates: Requirements 4.2, 4.4**

  - [x] 7.8 Write property test for report persistence after download
    - **Property 12: Report Persistence After Download**
    - **Validates: Requirements 4.5**

- [x] 8. Checkpoint - Ensure execution and reporting works
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Implement execution queue and concurrency management
  - [x] 9.1 Create ExecutionQueue service
    - Implement thread-safe queue for pending executions
    - Add configuration for MAX_CONCURRENT_EXECUTIONS
    - Track currently running executions
    - Implement queue position calculation
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 9.2 Integrate ExecutionQueue with ExecutionService
    - Enqueue new executions instead of running immediately
    - Update execution with queue position
    - Implement queue processor to start executions when slots available
    - _Requirements: 6.1, 6.4_

  - [x] 9.3 Implement async execution with @Async
    - Configure async executor in Spring
    - Make JMeter execution asynchronous
    - Ensure queue processor triggers next execution on completion
    - _Requirements: 6.4_

  - [x] 9.4 Write property test for execution queueing
    - **Property 16: Execution Queueing**
    - **Validates: Requirements 6.1, 6.3**

  - [x] 9.5 Write property test for concurrency limit enforcement
    - **Property 17: Concurrency Limit Enforcement**
    - **Validates: Requirements 6.2**

  - [x] 9.6 Write property test for automatic queue processing
    - **Property 18: Automatic Queue Processing**
    - **Validates: Requirements 6.4**

  - [x] 9.7 Write integration tests for concurrent executions
    - Test multiple simultaneous execution requests
    - Verify queue ordering
    - Verify automatic processing
    - _Requirements: 6.1, 6.2, 6.4_

- [x] 10. Create web frontend
  - [x] 10.1 Create HTML page with file upload form
    - Add file input with .jmx filter
    - Add upload button
    - Display upload status and errors
    - _Requirements: 1.1, 1.3, 1.4_

  - [x] 10.2 Add file list display
    - Fetch and display uploaded files
    - Show filename, size, upload date
    - Add execute and delete buttons for each file
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 10.3 Add execution status panel
    - Display active and queued executions
    - Show execution status, queue position
    - Auto-refresh status periodically
    - _Requirements: 2.2, 6.3, 6.5_

  - [x] 10.4 Add report viewing and download
    - Display report links for completed executions
    - Implement view report (open in new tab or iframe)
    - Implement download report button
    - _Requirements: 3.1, 3.2, 4.1_

  - [x] 10.5 Add basic CSS styling
    - Style upload form and file list
    - Style execution status panel
    - Add loading indicators
    - Ensure responsive layout
    - _Requirements: All UI requirements_

- [x] 11. Final integration and testing
  - [x] 11.1 Wire all components together
    - Verify all endpoints work end-to-end
    - Test complete flow: upload → execute → view → download
    - _Requirements: All_

  - [x] 11.2 Write end-to-end integration tests
    - Test complete user workflows
    - Test error scenarios
    - Test concurrent execution scenarios
    - _Requirements: All_

  - [x] 11.3 Add application configuration documentation
    - Document configuration properties
    - Document JMeter installation requirements
    - Document storage directory setup
    - _Requirements: All_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property tests should run minimum 100 iterations using jqwik library
- JMeter must be installed and available in system PATH
- File storage directories will be created automatically by the application
- Consider using H2 in-memory database for development, or file-based storage for simplicity

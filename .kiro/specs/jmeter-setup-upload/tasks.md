# Implementation Plan: JMeter Setup Upload

## Overview

This implementation plan breaks down the JMeter Setup Upload feature into discrete coding tasks. The feature will be built incrementally, starting with core backend services, then API endpoints, frontend integration, and finally integration with the existing JMeter execution engine.

## Tasks

- [x] 1. Create JMeter Manager service for configuration management
  - Create `JMeterManager` interface and implementation class
  - Implement methods for storing and retrieving JMeter installation path
  - Implement configuration persistence using application properties
  - Implement JMeter version detection by executing `jmeter -v`
  - Implement availability verification
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 7.1_

- [x] 1.1 Write property test for JMeter Manager configuration persistence
  - **Property 12: Configuration Persistence Round-Trip**
  - **Validates: Requirements 3.4**

- [x] 1.2 Write unit tests for JMeter Manager
  - Test path configuration and retrieval
  - Test version detection
  - Test availability verification
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 2. Create Extraction Service for ZIP file handling
  - Create `ExtractionService` class
  - Implement ZIP file extraction to temporary directory
  - Implement JMeter distribution structure validation (check for bin/ and executable)
  - Implement detection of JMeter root directory in ZIP
  - Implement moving extracted files to final location
  - Implement cleanup on failure
  - Implement executable permission setting for Unix systems
  - _Requirements: 1.5, 2.1, 2.3, 2.5, 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 2.1 Write property test for ZIP structure validation
  - **Property 3: JMeter Distribution Structure Validation**
  - **Validates: Requirements 1.5, 5.1, 5.2**

- [x] 2.2 Write property test for extraction cleanup on failure
  - **Property 8: Cleanup on Extraction Failure**
  - **Validates: Requirements 2.5**

- [x] 2.3 Write property test for multi-platform executable support
  - **Property 16: Multi-Platform Executable Support**
  - **Validates: Requirements 5.4**

- [x] 2.4 Write unit tests for Extraction Service
  - Test extraction with valid JMeter ZIP
  - Test validation with invalid ZIP structures
  - Test cleanup behavior
  - _Requirements: 2.1, 2.3, 2.5, 5.3_

- [x] 3. Create Setup API controller and endpoints
  - Create `SetupController` class with REST endpoints
  - Implement `GET /api/setup/status` endpoint
  - Implement `POST /api/setup/upload` endpoint with multipart file handling
  - Implement `DELETE /api/setup/installation` endpoint
  - Implement `POST /api/setup/verify` endpoint
  - Add file extension validation (.zip only)
  - Add file size validation (max 200MB)
  - Integrate with ExtractionService and JMeterManager
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 4.1, 4.2, 4.3, 6.1, 6.2, 6.3, 6.4_

- [x] 3.1 Write property test for file extension validation
  - **Property 1: File Extension Validation**
  - **Validates: Requirements 1.1, 1.2**

- [x] 3.2 Write property test for upload response completeness
  - **Property 2: Upload Response Completeness**
  - **Validates: Requirements 1.3, 1.4**

- [x] 3.3 Write property test for status information completeness
  - **Property 14: Status Information Completeness**
  - **Validates: Requirements 4.1, 4.2, 4.3**

- [x] 3.4 Write property test for active execution check before removal
  - **Property 20: Active Execution Check Before Removal**
  - **Validates: Requirements 6.3, 6.4**

- [x] 3.5 Write unit tests for Setup Controller
  - Test status endpoint with different states
  - Test upload with valid and invalid files
  - Test deletion with and without active executions
  - _Requirements: 1.1, 1.2, 4.1, 6.3, 6.4_

- [x] 4. Checkpoint - Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Modify JMeter Engine to use configured path
  - Update `JMeterEngine` class to inject `JMeterManager` dependency
  - Replace system PATH lookup with `JMeterManager.getJMeterBinaryPath()`
  - Add pre-execution JMeter availability check
  - Create `JMeterNotConfiguredException` exception class
  - Create `JMeterNotAvailableException` exception class
  - Update error messages to indicate setup requirement
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 5.1 Write property test for execution using configured path
  - **Property 22: Execution Uses Configured Path**
  - **Validates: Requirements 7.1, 7.3**

- [x] 5.2 Write property test for unconfigured JMeter error
  - **Property 23: Unconfigured JMeter Error**
  - **Validates: Requirements 7.2**

- [x] 5.3 Write property test for pre-execution availability verification
  - **Property 24: Pre-Execution Availability Verification**
  - **Validates: Requirements 7.4**

- [x] 5.4 Write unit tests for modified JMeter Engine
  - Test execution with configured JMeter
  - Test execution without configured JMeter
  - Test error handling when JMeter becomes unavailable
  - _Requirements: 7.1, 7.2, 7.5_

- [x] 6. Create Setup Page frontend
  - Create `setup.html` page with navigation link
  - Implement status display section showing current JMeter configuration
  - Implement file upload form with drag-and-drop support
  - Implement progress indicator for upload and extraction
  - Implement success message display with version and path
  - Implement error message display
  - Implement replacement confirmation dialog
  - Add JavaScript for API interactions (`/api/setup/status`, `/api/setup/upload`)
  - Add CSS styling consistent with existing pages
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.2, 3.3, 4.1, 4.2, 4.3, 6.1_

- [x] 7. Add navigation and startup checks
  - Add "Setup" link to main navigation in `index.html`
  - Update `app.js` to check JMeter status on application load
  - Display setup prompt if JMeter is not configured
  - Add link to setup page from main page when JMeter is not configured
  - _Requirements: 4.3, 4.5_

- [x] 8. Update application configuration
  - Add JMeter configuration properties to `application.properties`
  - Add property for JMeter installation directory path
  - Add property for maximum upload file size (200MB)
  - Update README.md with setup page documentation
  - _Requirements: 3.4_

- [x] 9. Write integration tests for end-to-end flow
  - Test complete flow: upload → extract → configure → execute test
  - Test replacement flow with existing installation
  - Test error recovery and cleanup
  - _Requirements: 1.1, 2.1, 3.1, 7.1_

- [x] 10. Final checkpoint - Ensure all tests pass and feature is complete
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Each task references specific requirements for traceability
- The implementation uses Java 17 with Spring Boot framework
- Property tests use jqwik library (already in project dependencies)
- Frontend uses vanilla JavaScript consistent with existing pages
- Checkpoints ensure incremental validation

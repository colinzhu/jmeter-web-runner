package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.StorageConfig;
import io.github.colinzhu.jmeter.webrunner.exception.JMeterNotAvailableException;
import io.github.colinzhu.jmeter.webrunner.exception.JMeterNotConfiguredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class JMeterEngine {
    private final StorageConfig storageConfig;
    private final JMeterManager jmeterManager;
    
    // Track active processes by execution ID
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    public JMeterExecutionResult executeTest(String testFilePath, String executionId) {
        try {
            // Get JMeter binary path from manager
            String jmeterPath = jmeterManager.getJMeterBinaryPath();
            if (jmeterPath == null) {
                throw new JMeterNotConfiguredException(
                        "JMeter is not configured. Please upload a JMeter distribution in the Setup page."
                );
            }

            // Verify JMeter binary exists
            if (!Files.exists(Paths.get(jmeterPath))) {
                throw new JMeterNotAvailableException(
                        "JMeter binary not found at configured path: " + jmeterPath
                );
            }

            // Create report directory structure
            Path reportDir = createReportDirectory(executionId);
            Path resultsFile = reportDir.resolve("results.jtl");

            // Build JMeter command with configured path
            List<String> command = buildJMeterCommand(jmeterPath, testFilePath, resultsFile.toString(), reportDir.toString());

            log.info("Executing JMeter command: {}", String.join(" ", command));

            // Execute JMeter process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            
            // Track the process
            activeProcesses.put(executionId, process);

            try {
                // Capture output
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        log.debug("JMeter output: {}", line);
                    }
                }

                // Wait for process to complete
                int exitCode = process.waitFor();

                log.info("JMeter execution completed with exit code: {}", exitCode);

                // Parse exit code and return result
                if (exitCode == 0) {
                    return JMeterExecutionResult.success(reportDir.toString(), output.toString());
                } else {
                    return JMeterExecutionResult.failure("JMeter execution failed with exit code: " + exitCode, output.toString());
                }
            } finally {
                // Remove from tracking when complete
                activeProcesses.remove(executionId);
            }

        } catch (IOException e) {
            log.error("Failed to execute JMeter", e);
            return JMeterExecutionResult.failure("Failed to execute JMeter: " + e.getMessage(), "");
        } catch (InterruptedException e) {
            log.error("JMeter execution interrupted", e);
            Thread.currentThread().interrupt();
            return JMeterExecutionResult.failure("JMeter execution interrupted: " + e.getMessage(), "");
        }
    }

    public boolean terminateExecution(String executionId) {
        try {
            Process process = activeProcesses.get(executionId);
            if (process != null && process.isAlive()) {
                log.info("Terminating JMeter process for execution: {}", executionId);
                process.destroyForcibly();  // Forcefully terminate process and children
                activeProcesses.remove(executionId);
                log.info("Successfully terminated JMeter process for execution: {}", executionId);
                return true;
            }
            log.warn("No active process found for execution: {}", executionId);
            return false;
        } catch (Exception e) {
            log.error("Error terminating JMeter process for execution: {}", executionId, e);
            // Don't throw exception - log error and return false
            return false;
        }
    }

    private Path createReportDirectory(String executionId) throws IOException {
        Path reportDir = Paths.get(storageConfig.getReportDir(), executionId);
        Files.createDirectories(reportDir);
        log.info("Created report directory: {}", reportDir);
        return reportDir;
    }

    private List<String> buildJMeterCommand(String jmeterPath, String testFilePath, String resultsFilePath, String reportDirPath) {
        List<String> command = new ArrayList<>();
        command.add(jmeterPath);
        command.add("-n");
        command.add("-t");
        command.add(testFilePath);
        command.add("-l");
        command.add(resultsFilePath);
        command.add("-e");
        command.add("-o");
        command.add(reportDirPath);
        return command;
    }

    public static class JMeterExecutionResult {
        private final boolean success;
        private final String reportPath;
        private final String errorMessage;
        private final String output;

        private JMeterExecutionResult(boolean success, String reportPath, String errorMessage, String output) {
            this.success = success;
            this.reportPath = reportPath;
            this.errorMessage = errorMessage;
            this.output = output;
        }

        public static JMeterExecutionResult success(String reportPath, String output) {
            return new JMeterExecutionResult(true, reportPath, null, output);
        }

        public static JMeterExecutionResult failure(String errorMessage, String output) {
            return new JMeterExecutionResult(false, null, errorMessage, output);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getReportPath() {
            return reportPath;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getOutput() {
            return output;
        }
    }
}

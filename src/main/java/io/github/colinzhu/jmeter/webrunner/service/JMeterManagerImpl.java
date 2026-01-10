package io.github.colinzhu.jmeter.webrunner.service;

import io.github.colinzhu.jmeter.webrunner.config.JMeterConfig;
import io.github.colinzhu.jmeter.webrunner.model.JMeterInfo;
import io.github.colinzhu.jmeter.webrunner.model.JMeterSetupState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class JMeterManagerImpl implements JMeterManager {

    private static final Pattern VERSION_PATTERN = Pattern.compile("Version\\s+(\\d+\\.\\d+(?:\\.\\d+)?)");
    private static final String SETUP_STATE_FILE = "setup-state.json";
    private final JMeterConfig jmeterConfig;
    private final PersistenceService persistenceService;

    /**
     * Load persisted setup state on application startup
     */
    @PostConstruct
    public void init() {
        JMeterSetupState state = persistenceService.load(SETUP_STATE_FILE, JMeterSetupState.class);
        if (state != null && state.getInstallationPath() != null) {
            jmeterConfig.setInstallationPath(state.getInstallationPath());
            jmeterConfig.setVersion(state.getVersion());
            log.info("Loaded persisted JMeter setup: path={}, version={}",
                    state.getInstallationPath(), state.getVersion());
        }
    }

    @Override
    public void setInstallationPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Installation path cannot be null or empty");
        }

        jmeterConfig.setInstallationPath(path);
        log.info("JMeter installation path set to: {}", path);

        // Detect and store version
        JMeterInfo info = verifyInstallation();
        if (info.isAvailable() && info.getVersion() != null) {
            jmeterConfig.setVersion(info.getVersion());
            log.info("JMeter version detected: {}", info.getVersion());
        }

        // Persist setup state
        saveSetupState();
    }

    @Override
    public String getJMeterBinaryPath() {
        String installationPath = jmeterConfig.getInstallationPath();
        if (installationPath == null || installationPath.trim().isEmpty()) {
            return null;
        }

        // Determine OS and return appropriate binary path
        String os = System.getProperty("os.name").toLowerCase();
        String binaryName = os.contains("win") ? "jmeter.bat" : "jmeter";

        Path binaryPath = Paths.get(installationPath, "bin", binaryName);
        return binaryPath.toString();
    }

    @Override
    public boolean isConfigured() {
        String installationPath = jmeterConfig.getInstallationPath();
        return installationPath != null && !installationPath.trim().isEmpty();
    }

    @Override
    public JMeterInfo verifyInstallation() {
        if (!isConfigured()) {
            return JMeterInfo.builder()
                    .available(false)
                    .error("JMeter is not configured")
                    .build();
        }

        String binaryPath = getJMeterBinaryPath();
        Path binary = Paths.get(binaryPath);

        // Check if binary exists
        if (!Files.exists(binary)) {
            return JMeterInfo.builder()
                    .path(jmeterConfig.getInstallationPath())
                    .available(false)
                    .error("JMeter binary not found at: " + binaryPath)
                    .build();
        }

        // Check if binary is executable
        if (!Files.isExecutable(binary)) {
            return JMeterInfo.builder()
                    .path(jmeterConfig.getInstallationPath())
                    .available(false)
                    .error("JMeter binary is not executable: " + binaryPath)
                    .build();
        }

        // Detect version by executing jmeter -v
        String version = detectVersion(binaryPath);

        return JMeterInfo.builder()
                .version(version)
                .path(jmeterConfig.getInstallationPath())
                .available(true)
                .build();
    }

    @Override
    public void clearConfiguration() {
        jmeterConfig.setInstallationPath(null);
        jmeterConfig.setVersion(null);

        // Delete persisted state
        persistenceService.delete(SETUP_STATE_FILE);

        log.info("JMeter configuration cleared");
    }

    /**
     * Save current setup state to disk
     */
    private void saveSetupState() {
        JMeterSetupState state = JMeterSetupState.builder()
                .installationPath(jmeterConfig.getInstallationPath())
                .version(jmeterConfig.getVersion())
                .build();

        persistenceService.save(SETUP_STATE_FILE, state);
        log.debug("Persisted JMeter setup state");
    }

    /**
     * Detect JMeter version by executing 'jmeter -v' command.
     *
     * @param binaryPath The full path to the JMeter binary
     * @return The detected version string, or null if detection fails
     */
    private String detectVersion(String binaryPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(binaryPath, "-v");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("JMeter version detection failed with exit code: {}", exitCode);
                return null;
            }

            // Parse version from output
            String outputStr = output.toString();
            Matcher matcher = VERSION_PATTERN.matcher(outputStr);
            if (matcher.find()) {
                return matcher.group(1);
            }

            log.warn("Could not parse JMeter version from output: {}", outputStr);
            return null;

        } catch (IOException e) {
            log.error("Failed to execute JMeter for version detection", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Version detection interrupted", e);
            return null;
        }
    }
}

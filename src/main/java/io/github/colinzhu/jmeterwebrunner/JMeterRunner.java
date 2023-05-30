package io.github.colinzhu.jmeterwebrunner;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.JMeter;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
public class JMeterRunner {

    public static void main(String[] args) {
        init();

        String jmxFile = args != null && args.length > 0 && args[0].trim().length() > 0 ? args[0] : null;
        if (null == jmxFile) {
            log.error("Please provide a JMX test file name.");
            return;
        }

        HashTree testPlan = loadTestPlan(jmxFile);

        // Run Test Plan
        StandardJMeterEngine jmeter = new StandardJMeterEngine();
        jmeter.configure(testPlan);
        jmeter.run();

        log.info("Test completed.");
    }

    @SneakyThrows
    private static void init() {
        String jmeterHome = loadConfig().getProperty("jmeter-home");
        log.info("jmeter home: " + jmeterHome);

        JMeterUtils.loadJMeterProperties( jmeterHome + "/bin/jmeter.properties");
        JMeterUtils.setJMeterHome(jmeterHome);
        JMeterUtils.initLocale();

        SaveService.loadProperties();
    }

    @SneakyThrows
    private static HashTree loadTestPlan(String jmxFile) {
        HashTree testPlanTree = SaveService.loadTree(new File(jmxFile));
        JMeter.convertSubTree(testPlanTree, false); // Remove disabled test elements
        return testPlanTree;
    }

    private static Properties loadConfig() {
        String filename = "config.properties";
        Properties prop = new Properties();

        // Load the properties file using getResourceAsStream
        try (InputStream input = Files.newInputStream(Paths.get(filename))) {
            prop.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load config file", ex);
        }
        return prop;
    }
}
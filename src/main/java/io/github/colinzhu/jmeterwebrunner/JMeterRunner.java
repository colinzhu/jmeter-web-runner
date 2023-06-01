package io.github.colinzhu.jmeterwebrunner;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.JMeter;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.util.Objects;

/**
 * This class invokes the StandardJMeterEngine to run the JMX file when receives an event from the browser
 */
@Slf4j
public class JMeterRunner {
    private static final String PARAM_JMETER_HOME = "jmeterHome";

    /**
     * This method invokes StandardJMeterEngine to run the JMX file
     *
     * @param args JMX file name
     */
    public static void main(String[] args) {
        setupJMeter();

        String jmxFile = getJmxFileName(args);
        HashTree testPlan = loadJmxAsTestPlan(jmxFile);

        StandardJMeterEngine jmeter = new StandardJMeterEngine();
        jmeter.configure(testPlan);
        jmeter.run();

        log.info("Test completed.");
    }

    private static String getJmxFileName(String[] args) {
        String jmxFile = args != null && args.length > 0 && args[0].trim().length() > 0 ? args[0] : null;
        Objects.requireNonNull(jmxFile, "Please provide a JMX test file name.");
        return jmxFile;
    }

    @SneakyThrows
    private static void setupJMeter() {
        String jmeterHome = System.getProperty(PARAM_JMETER_HOME);
        Objects.requireNonNull(jmeterHome, "VM option -DjmeterHome is required. e.g. -DjmeterHome=/test/apache-jmeter-5.5");

        log.info("JMeter home: " + jmeterHome);

        JMeterUtils.loadJMeterProperties( jmeterHome + "/bin/jmeter.properties");
        JMeterUtils.setJMeterHome(jmeterHome);
        JMeterUtils.initLocale();

        SaveService.loadProperties();
    }

    @SneakyThrows
    private static HashTree loadJmxAsTestPlan(String jmxFile) {
        HashTree testPlanTree = SaveService.loadTree(new File(jmxFile));
        JMeter.convertSubTree(testPlanTree, false); // Remove disabled test elements
        return testPlanTree;
    }

}
package org.example;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Logger root = (Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);

        // JMeter Engine
        StandardJMeterEngine jmeter = new StandardJMeterEngine();

        // Initialize Properties, logging, locale, etc.
        JMeterUtils.loadJMeterProperties("/home/colin/dev/apache-jmeter-5.5/bin/jmeter.properties");
        JMeterUtils.setJMeterHome("/home/colin/dev/apache-jmeter-5.5");
        JMeterUtils.initLocale();

        // Initialize JMeter SaveService
        SaveService.loadProperties();

        // Load existing .jmx Test Plan
        File f = new File("test.jmx");
        HashTree testPlanTree = SaveService.loadTree(f);

        //add Summarizer output to get test progress in stdout like:
        // summary =      2 in   1.3s =    1.5/s Avg:   631 Min:   290 Max:   973 Err:     0 (0.00%)
        Summariser summer = null;
        String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
        if (summariserName.length() > 0) {
            summer = new Summariser(summariserName);
        }


        // Store execution results into a .jtl file
        String logFile = "test.jtl";
        ResultCollector logger = new ResultCollector(summer);
        logger.setFilename(logFile);
        testPlanTree.add(testPlanTree.getArray()[0], logger);

        // Run Test Plan
        jmeter.configure(testPlanTree);
        jmeter.run();

        System.out.println("Test completed. See " + "example.jtl file for results");
        System.out.println("JMeter .jmx script is available at " + "example.jmx");
        System.exit(0);
    }
}
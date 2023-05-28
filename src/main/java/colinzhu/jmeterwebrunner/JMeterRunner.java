package colinzhu.jmeterwebrunner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.colinzhu.webconsole.WebConsole;
import org.apache.jmeter.JMeter;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Properties;

public class JMeterRunner {

    public static void main(String[] args) {
//                HttpServerOptions options = new HttpServerOptions()
//                .setPort(8080)
//                .setSsl(true)
//                .setHost("example.com")
//                .setKeyCertOptions(new PemKeyCertOptions().setCertPath("chain.pem").setKeyPath("key.pem"));
//        WebConsole.start(JMeterRunner::main, options);

        WebConsole.start(JMeterRunner::run, 8080);
    }

    public static void run(String[] args) {
//        Logger root = (Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
//        root.setLevel(Level.INFO);

        // JMeter Engine
        StandardJMeterEngine jmeter = new StandardJMeterEngine();

        Properties config = loadConfig();
        String jmeterHome = config.getProperty("jmeter-home");
        System.out.println("jmeter home: " + jmeterHome);

        // Initialize Properties, logging, locale, etc.
//        JMeterUtils.loadJMeterProperties( jmeterHome + "/bin/jmeter.properties.bak2");
//        JMeterUtils.setJMeterHome(jmeterHome);
        JMeterUtils.loadJMeterProperties("/home/colin/dev/apache-jmeter-5.5/bin/jmeter.properties");
        JMeterUtils.setJMeterHome("/home/colin/dev/apache-jmeter-5.5");
        JMeterUtils.initLocale();

        // Initialize JMeter SaveService
        try {
            SaveService.loadProperties();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Load existing .jmx Test Plan
        File f = new File("test.jmx");
        HashTree testPlanTree;
        try {
            testPlanTree = SaveService.loadTree(f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Remove disabled test elements
        JMeter.convertSubTree(testPlanTree, false);

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

        System.out.println("Test completed. See " + "test.jtl file for results");
    }

    private static Properties loadConfig() {
        String filename = "config.properties";
        Properties prop = new Properties();

        // Load the properties file using getResourceAsStream
        try (InputStream input = new FileInputStream(filename)) {
            prop.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load config file", ex);
        }
        return prop;
    }
//
//    public class RealtimeResultCollector extends ResultCollector{
//
//        LinkedList<SampleEvent> collectedEvents = new LinkedList<>();
//
//        /**
//         * When a test result is received, store it internally
//         *
//         * @param event
//         *            the sample event that was received
//         */
//        @Override
//        public void sampleOccurred(SampleEvent event) {
//            collectedEvents.add(event);
//        }
//
//        /**
//         * When the test ends print the response code for all the events collected
//         *
//         * @param host
//         *            the host where the test was running from
//         */
//        @Override
//        public void testEnded(String host) {
//            for(SampleEvent e: collectedEvents){
//                System.out.println("TEST_RESULT: Response code = " + e.getResult().getResponseCode()); // or do whatever you want ...
//            }
//        }
//    }
}
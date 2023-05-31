package io.github.colinzhu.jmeterwebrunner;

import io.github.colinzhu.webconsole.WebConsole;
import io.vertx.core.http.HttpServerOptions;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.JMeter;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.File;

@Slf4j
@RequiredArgsConstructor
public class JMeterRunner {

    private final String jmeterHome;

    public void start(int port) {
        WebConsole.start(this::triggerJMeter, port);
    }

    public void start(Runnable preTask, int port) {
        WebConsole.start(preTask, this::triggerJMeter, port);
    }

    public void start(HttpServerOptions options ) {
        WebConsole.start(null, this::triggerJMeter, options);
    }

    public void start(Runnable preTask, HttpServerOptions options ) {
        WebConsole.start(preTask, this::triggerJMeter, options);
    }

    private void triggerJMeter(String[] args) {
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
    private void init() {
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

}
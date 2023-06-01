package io.github.colinzhu.jmeterwebrunner;

import io.github.colinzhu.webconsole.WebConsole;

import java.util.Objects;

/**
 * This is the default starter which starts a WebConsole for JMeterRunner
 */
public class DefaultStarter {
    private static final String PARAM_PORT = "port";
    public static void main(String[] args) {
        String portStr = System.getProperty(PARAM_PORT);
        Objects.requireNonNull(portStr, "VM option -Dport is required. e.g. -Dport=8080");

        WebConsole.start(null, JMeterRunner::main, Integer.parseInt(portStr));
    }
}

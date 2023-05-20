package colinzhu.jmeterwebrunner;

import colinzhu.webconsole.WebConsole;

public class App {
    public static void main(String[] args) {
        WebConsole.start(JMeterRunner::main);
    }
}

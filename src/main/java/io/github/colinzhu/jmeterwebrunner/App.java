package io.github.colinzhu.jmeterwebrunner;

import io.github.colinzhu.webconsole.WebConsole;

public class App {
    public static void main(String[] args) {
//                HttpServerOptions options = new HttpServerOptions()
//                .setPort(8080)
//                .setSsl(true)
//                .setHost("example.com")
//                .setKeyCertOptions(new PemKeyCertOptions().setCertPath("chain.pem").setKeyPath("key.pem"));
//        WebConsole.start(null, JMeterRunner::main, options);

        WebConsole.start(()-> System.out.println("hello"), JMeterRunner::main, 8080);
    }
}

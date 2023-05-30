package io.github.colinzhu.jmeterwebrunner;

public class Example {
    public static void main(String[] args) {
//        HttpServerOptions options = new HttpServerOptions()
//                .setPort(8080)
//                .setSsl(true)
//                .setHost("example.com")
//                .setKeyCertOptions(new PemKeyCertOptions().setCertPath("chain.pem").setKeyPath("key.pem"));
//        JMeterRunner.start(options);

        JMeterRunner.start(8080);
    }
}

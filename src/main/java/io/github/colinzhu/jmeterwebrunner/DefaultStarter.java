package io.github.colinzhu.jmeterwebrunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class DefaultStarter {
    public static void main(String[] args) {
        Properties prop = loadConfig();
        JMeterRunner runner = new JMeterRunner(prop.getProperty("jmeter-home"));

//        HttpServerOptions options = new HttpServerOptions()
//                .setPort(8080)
//                .setSsl(true)
//                .setHost("example.com")
//                .setKeyCertOptions(new PemKeyCertOptions().setCertPath("chain.pem").setKeyPath("key.pem"));
//        runner.start(options);

        runner.start(Integer.parseInt(prop.getProperty("port")));
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

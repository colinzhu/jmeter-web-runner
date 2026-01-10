package io.github.colinzhu.jmeter.webrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JMeterWebRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JMeterWebRunnerApplication.class, args);
    }
}

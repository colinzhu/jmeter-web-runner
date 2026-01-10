package io.github.colinzhu.jmeter.webrunner.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jmeter")
@Getter
@Setter
public class JMeterConfig {
    private String installationPath;
    private String version;
    private int maxConcurrentExecutions;
}

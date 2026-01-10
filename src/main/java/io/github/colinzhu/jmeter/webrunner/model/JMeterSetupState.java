package io.github.colinzhu.jmeter.webrunner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the persisted state of JMeter setup configuration.
 * This is saved to disk to survive application restarts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JMeterSetupState {
    private String installationPath;
    private String version;
}

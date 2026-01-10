package io.github.colinzhu.jmeter.webrunner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupStatus {
    private boolean configured;
    private String version;
    private String path;
    private boolean available;
    private String error;
}

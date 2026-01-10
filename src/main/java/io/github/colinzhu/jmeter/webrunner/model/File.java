package io.github.colinzhu.jmeter.webrunner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class File {
    private String id;
    private String filename;
    private long size;
    private Instant uploadedAt;
    private String path;
}

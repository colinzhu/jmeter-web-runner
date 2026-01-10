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
public class Execution {
    private String id;
    private String fileId;
    private ExecutionStatus status;
    private Integer queuePosition;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Long duration;
    private String reportId;
    private String error;
}

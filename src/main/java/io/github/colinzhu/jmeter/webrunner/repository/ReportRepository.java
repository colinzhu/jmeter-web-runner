package io.github.colinzhu.jmeter.webrunner.repository;

import io.github.colinzhu.jmeter.webrunner.model.Report;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ReportRepository {
    private final Map<String, Report> reports = new ConcurrentHashMap<>();

    public Report save(Report report) {
        reports.put(report.getId(), report);
        return report;
    }

    public Optional<Report> findById(String id) {
        return Optional.ofNullable(reports.get(id));
    }

    public Optional<Report> findByExecutionId(String executionId) {
        return reports.values().stream()
                .filter(report -> report.getExecutionId().equals(executionId))
                .findFirst();
    }

    public List<Report> findAll() {
        return new ArrayList<>(reports.values());
    }

    public void deleteById(String id) {
        reports.remove(id);
    }

    public boolean existsById(String id) {
        return reports.containsKey(id);
    }
}

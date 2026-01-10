package io.github.colinzhu.jmeter.webrunner.repository;

import io.github.colinzhu.jmeter.webrunner.model.Execution;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ExecutionRepository {
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();

    public Execution save(Execution execution) {
        executions.put(execution.getId(), execution);
        return execution;
    }

    public Optional<Execution> findById(String id) {
        return Optional.ofNullable(executions.get(id));
    }

    public List<Execution> findAll() {
        return new ArrayList<>(executions.values());
    }

    public void deleteById(String id) {
        executions.remove(id);
    }

    public boolean existsById(String id) {
        return executions.containsKey(id);
    }
}

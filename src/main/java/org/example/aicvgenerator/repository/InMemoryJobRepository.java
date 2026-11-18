package org.example.aicvgenerator.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.example.aicvgenerator.repository.entity.JobEntity;
import org.springframework.stereotype.Repository;

/** Assigns timestamps. Reads and writes copy rows, so callers never hold a stored row. */
@Repository
public class InMemoryJobRepository implements JobRepository {

    private final List<JobEntity> rows = new ArrayList<>();

    @Override
    public synchronized Optional<JobEntity> findById(UUID id) {
        return rows.stream().filter(row -> row.getId().equals(id)).findFirst().map(this::copy);
    }

    @Override
    public synchronized void insert(JobEntity entity) {
        Instant now = Instant.now();
        JobEntity row = copy(entity);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        rows.add(row);
    }

    @Override
    public synchronized void update(JobEntity entity) {
        rows.stream().filter(row -> row.getId().equals(entity.getId())).findFirst().ifPresent(row -> {
            row.setStatus(entity.getStatus());
            row.setLabel(entity.getLabel());
            row.setEntityType(entity.getEntityType());
            row.setEntityId(entity.getEntityId());
            row.setErrorCode(entity.getErrorCode());
            row.setErrorMessage(entity.getErrorMessage());
            row.setUpdatedAt(Instant.now());
        });
    }

    @Override
    public synchronized void delete(UUID id) {
        rows.removeIf(row -> row.getId().equals(id));
    }

    private JobEntity copy(JobEntity entity) {
        return entity.toBuilder().build();
    }
}

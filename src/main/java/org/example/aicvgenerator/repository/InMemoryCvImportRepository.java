package org.example.aicvgenerator.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.example.aicvgenerator.repository.entity.CvImportEntity;
import org.springframework.stereotype.Repository;

/** Keeps rows in an {@link ArrayList}; timestamps are assigned here. */
@Repository
public class InMemoryCvImportRepository implements CvImportRepository {

    private final List<CvImportEntity> rows = new ArrayList<>();

    @Override
    public synchronized Optional<CvImportEntity> findById(UUID id) {
        return rows.stream().filter(row -> row.getId().equals(id)).findFirst().map(this::copy);
    }

    @Override
    public synchronized void insert(CvImportEntity entity) {
        Instant now = Instant.now();
        CvImportEntity row = copy(entity);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        rows.add(row);
    }

    @Override
    public synchronized void update(CvImportEntity entity) {
        rows.stream().filter(row -> row.getId().equals(entity.getId())).findFirst().ifPresent(row -> {
            row.setStatus(entity.getStatus());
            row.setFileName(entity.getFileName());
            row.setCvId(entity.getCvId());
            row.setErrorCode(entity.getErrorCode());
            row.setErrorMessage(entity.getErrorMessage());
            row.setUpdatedAt(Instant.now());
        });
    }

    private CvImportEntity copy(CvImportEntity entity) {
        return entity.toBuilder().build();
    }
}

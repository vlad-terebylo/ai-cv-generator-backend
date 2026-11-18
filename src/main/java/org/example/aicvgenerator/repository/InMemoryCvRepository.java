package org.example.aicvgenerator.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.aicvgenerator.exception.DuplicateEmailException;
import org.example.aicvgenerator.repository.entity.CvEntity;
import org.springframework.stereotype.Repository;

/** Assigns ids and timestamps and enforces unique email. Reads and writes copy rows, so callers never hold a stored row. */
@Repository
public class InMemoryCvRepository implements CvRepository {

    private final List<CvEntity> rows = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized Optional<CvEntity> findById(long id) {
        return rows.stream().filter(row -> row.getId() == id).findFirst().map(this::copy);
    }

    @Override
    public synchronized Optional<CvEntity> findByEmail(String email) {
        return rows.stream().filter(row -> row.getEmail().equals(email)).findFirst().map(this::copy);
    }

    @Override
    public synchronized long insert(CvEntity entity) {
        requireEmailFree(entity.getEmail(), null);
        Instant now = Instant.now();
        CvEntity row = copy(entity);
        row.setId(nextId++);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        rows.add(row);
        return row.getId();
    }

    @Override
    public synchronized void update(CvEntity entity) {
        rows.stream().filter(row -> row.getId().equals(entity.getId())).findFirst().ifPresent(row -> {
            requireEmailFree(entity.getEmail(), row.getId());
            row.setFirstName(entity.getFirstName());
            row.setLastName(entity.getLastName());
            row.setEmail(entity.getEmail());
            row.setSummary(entity.getSummary());
            row.setSourceFileName(entity.getSourceFileName());
            row.setUpdatedAt(Instant.now());
        });
    }

    @Override
    public synchronized UpsertResult upsertByEmail(CvEntity entity) {
        Optional<CvEntity> existing = findByEmail(entity.getEmail());
        if (existing.isEmpty()) {
            return new UpsertResult(insert(entity), true);
        }
        CvEntity replacement = copy(entity);
        replacement.setId(existing.get().getId());
        update(replacement);
        return new UpsertResult(replacement.getId(), false);
    }

    private void requireEmailFree(String email, Long ownId) {
        boolean taken = rows.stream().anyMatch(row -> row.getEmail().equals(email) && !row.getId().equals(ownId));
        if (taken) {
            throw new DuplicateEmailException(email);
        }
    }

    private CvEntity copy(CvEntity entity) {
        return entity.toBuilder().build();
    }
}

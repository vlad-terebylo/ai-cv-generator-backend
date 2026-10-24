package org.example.aicvgenerator.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.example.aicvgenerator.repository.entity.ExperienceEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Keeps rows in an {@link ArrayList}; mirrors {@link JdbcExperienceRepository}. */
@Repository
@Profile("!jdbc")
public class InMemoryExperienceRepository implements ExperienceRepository {

    private static final Comparator<ExperienceEntity> NEWEST_FIRST = Comparator
            .comparing(ExperienceEntity::getStartYear, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ExperienceEntity::getId);

    private final List<ExperienceEntity> rows = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized List<ExperienceEntity> findByCvId(long cvId) {
        return rows.stream()
                .filter(row -> row.getCvId() == cvId)
                .sorted(NEWEST_FIRST)
                .map(row -> row.toBuilder().build())
                .toList();
    }

    @Override
    public synchronized void insertAll(List<ExperienceEntity> entities) {
        for (ExperienceEntity entity : entities) {
            rows.add(entity.toBuilder().id(nextId++).build());
        }
    }

    @Override
    public synchronized void deleteByCvId(long cvId) {
        rows.removeIf(row -> row.getCvId() == cvId);
    }
}

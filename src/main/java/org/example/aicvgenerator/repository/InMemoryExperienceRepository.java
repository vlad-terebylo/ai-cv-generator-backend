package org.example.aicvgenerator.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.example.aicvgenerator.repository.entity.ExperienceEntity;
import org.springframework.stereotype.Repository;

@Repository
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
    public synchronized void replaceByCvId(long cvId, List<ExperienceEntity> entities) {
        rows.removeIf(row -> row.getCvId() == cvId);
        for (ExperienceEntity entity : entities) {
            rows.add(entity.toBuilder().id(nextId++).cvId(cvId).build());
        }
    }
}

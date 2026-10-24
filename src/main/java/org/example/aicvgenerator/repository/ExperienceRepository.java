package org.example.aicvgenerator.repository;

import java.util.List;

import org.example.aicvgenerator.repository.entity.ExperienceEntity;

public interface ExperienceRepository {

    /** Newest first (start year descending, unknown last), then by insertion order. */
    List<ExperienceEntity> findByCvId(long cvId);

    void insertAll(List<ExperienceEntity> entities);

    void deleteByCvId(long cvId);
}

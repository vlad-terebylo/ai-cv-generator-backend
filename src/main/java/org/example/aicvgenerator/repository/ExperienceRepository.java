package org.example.aicvgenerator.repository;

import java.util.List;

import org.example.aicvgenerator.repository.entity.ExperienceEntity;

public interface ExperienceRepository {

    /** Newest first (start year descending, unknown last), then by insertion order. */
    List<ExperienceEntity> findByCvId(long cvId);

    /** Deletes the CV's rows and stores these in their place as one atomic step; stored rows get {@code cvId}. */
    void replaceByCvId(long cvId, List<ExperienceEntity> entities);
}

package org.example.aicvgenerator.repository;

import java.util.Optional;
import java.util.UUID;

import org.example.aicvgenerator.repository.entity.JobEntity;

public interface JobRepository {

    Optional<JobEntity> findById(UUID id);

    /** Stores the row under the id the caller generated and assigns its timestamps. */
    void insert(JobEntity entity);

    /** Replaces the mutable fields of the row with the same id; an unknown id is ignored. */
    void update(JobEntity entity);

    /** Removes the row; an unknown id is ignored. */
    void delete(UUID id);
}

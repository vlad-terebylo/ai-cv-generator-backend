package org.example.aicvgenerator.repository;

import java.util.Optional;
import java.util.UUID;

import org.example.aicvgenerator.repository.entity.CvImportEntity;

public interface CvImportRepository {

    Optional<CvImportEntity> findById(UUID id);

    /** Stores the row under the id the caller generated and assigns its timestamps. */
    void insert(CvImportEntity entity);

    /** Replaces the mutable fields of the row with the same id; an unknown id is ignored. */
    void update(CvImportEntity entity);
}

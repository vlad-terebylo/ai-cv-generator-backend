package org.example.aicvgenerator.repository;

import java.util.Optional;

import org.example.aicvgenerator.repository.entity.CvEntity;

public interface CvRepository {

    Optional<CvEntity> findById(long id);

    Optional<CvEntity> findByEmail(String email);

    /** Inserts the row and returns its generated id. */
    long insert(CvEntity entity);

    void update(CvEntity entity);
}

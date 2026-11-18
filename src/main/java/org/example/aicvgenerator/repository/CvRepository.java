package org.example.aicvgenerator.repository;

import java.util.Optional;

import org.example.aicvgenerator.repository.entity.CvEntity;

public interface CvRepository {

    Optional<CvEntity> findById(long id);

    /** The email is compared exactly; callers pass it already lower-cased. */
    Optional<CvEntity> findByEmail(String email);

    /** Inserts the row and returns its generated id. */
    long insert(CvEntity entity);

    /** Replaces the mutable fields of the row with the same id; an unknown id is ignored. */
    void update(CvEntity entity);

    /**
     * Inserts the row, or replaces the mutable fields of the existing row with the same email. The lookup and the
     * write are one atomic step, so concurrent callers with the same email never both insert.
     */
    UpsertResult upsertByEmail(CvEntity entity);
}

package org.example.aicvgenerator.repository;

/** Outcome of {@link CvRepository#upsertByEmail}: the row's id and whether it was newly inserted. */
public record UpsertResult(long id, boolean created) {
}

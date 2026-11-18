package org.example.aicvgenerator.service.model;

/** The stored CV; {@code created} is false when an existing CV with the same email was replaced. */
public record CvSaveResult(Cv cv, boolean created) {
}

package org.example.aicvgenerator.service.model;

/** An uploaded file waiting to be processed. */
public record CvDocument(String fileName, byte[] content) {
}

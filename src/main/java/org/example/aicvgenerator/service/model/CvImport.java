package org.example.aicvgenerator.service.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tracks one asynchronous CV import from upload to its stored CV or its failure. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CvImport {

    private UUID id;
    private CvImportStatus status;
    private String fileName;
    /** Set once the import is {@link CvImportStatus#COMPLETED}. */
    private Long cvId;
    /** Set once the import is {@link CvImportStatus#FAILED}. */
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}

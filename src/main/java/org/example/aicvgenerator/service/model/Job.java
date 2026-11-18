package org.example.aicvgenerator.service.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tracks one background job from submission to the entity it produced or its failure. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    private UUID id;
    private JobStatus status;
    /** Short description of the job's input, e.g. the uploaded file name; not shown to clients. */
    private String label;
    /** Set once the import is {@link JobStatus#COMPLETED}. */
    private EntityType entityType;
    /** Set together with {@code entityType}. */
    private String entityId;
    /** Set once the import is {@link JobStatus#FAILED}. */
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}

package org.example.aicvgenerator.controller.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Progress of a background job. It only points at what the job produced; it never carries the entity itself. */
@Schema(description = "Progress of a background job (currently only CV imports)")
public record JobResponse(
        @Schema(description = "Opaque id of the job; pass it unchanged to GET /api/v1/jobs/{id}")
        String id,
        JobStatus status,
        @Schema(description = "Type of the entity the job produced; null until COMPLETED")
        EntityType entityType,
        @Schema(description = "Id of the entity the job produced; null until COMPLETED. Fetch it from the endpoint for its type")
        String entityId,
        @Schema(description = "Set when FAILED, e.g. NOT_A_CV, UNREADABLE_DOCUMENT, AI_SERVICE_ERROR or INTERNAL_ERROR")
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
}

package org.example.aicvgenerator.controller.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Progress of a background job. Not tied to one entity: {@code result} says what the job produced. */
@Schema(description = "Progress of a background job (currently a CV import)")
public record JobResponse(
        UUID id,
        @Schema(description = "PENDING, PROCESSING, COMPLETED or FAILED") String status,
        @Schema(description = "What the job works on, e.g. the uploaded file name") String name,
        @Schema(description = "What the job produced; null until COMPLETED. Fetch the entity from the endpoint for its type.")
        Result result,
        @Schema(description = "Set when FAILED, e.g. NOT_A_CV, UNREADABLE_DOCUMENT, AI_SERVICE_ERROR or INTERNAL_ERROR")
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    @Schema(name = "JobResult", description = "Reference to the entity a completed job produced")
    public record Result(
            @Schema(description = "Entity type, e.g. CV (fetch it with GET /api/v1/cvs/{id})") String type,
            @Schema(description = "Id of the entity") Long id) {
    }
}

package org.example.aicvgenerator.controller.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Progress of an asynchronous CV import")
public record CvImportResponse(
        UUID id,
        @Schema(description = "PENDING, PROCESSING, COMPLETED or FAILED") String status,
        String fileName,
        @Schema(description = "Id of the stored CV once the import is COMPLETED; fetch it with GET /api/v1/cvs/{id}")
        Long cvId,
        @Schema(description = "Set when FAILED: NOT_A_CV, UNREADABLE_DOCUMENT, AI_SERVICE_ERROR or INTERNAL_ERROR")
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
}

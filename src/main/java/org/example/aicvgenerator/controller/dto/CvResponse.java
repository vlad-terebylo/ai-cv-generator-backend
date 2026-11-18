package org.example.aicvgenerator.controller.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A processed and stored CV")
public record CvResponse(
        @Schema(description = "Opaque id of the CV; pass it unchanged to GET /api/v1/cvs/{id}")
        String id,
        String firstName,
        String lastName,
        String email,
        String summary,
        String sourceFileName,
        List<ExperienceResponse> experience) {
}

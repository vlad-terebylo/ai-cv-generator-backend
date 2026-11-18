package org.example.aicvgenerator.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One position from the CV work history")
public record ExperienceResponse(
        Integer startYear,
        @Schema(description = "Null when the position is current") Integer endYear,
        String position,
        String company) {
}

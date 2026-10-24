package org.example.aicvgenerator.controller.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A processed and stored CV")
public record CvResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String summary,
        String sourceFileName,
        List<ExperienceResponse> experience) {
}

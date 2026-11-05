package org.example.aicvgenerator.controller;

import java.util.UUID;

import org.example.aicvgenerator.controller.dto.CvImportResponse;
import org.example.aicvgenerator.mapper.CvImportDtoMapper;
import org.example.aicvgenerator.service.CvImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cv-imports")
@RequiredArgsConstructor
@Tag(name = "CV imports")
public class CvImportController {

    private final CvImportService cvImportService;
    private final CvImportDtoMapper mapper;

    @GetMapping("/{id}")
    @Operation(summary = "Get the progress of a CV import",
            description = "When COMPLETED, cvId points to the stored CV; when FAILED, errorCode and errorMessage say why.")
    @ApiResponse(responseCode = "404", description = "Import not found (jobs are lost when the application restarts)")
    public CvImportResponse get(@PathVariable UUID id) {
        return mapper.toResponse(cvImportService.getById(id));
    }
}

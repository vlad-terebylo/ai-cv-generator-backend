package org.example.aicvgenerator.controller;

import org.example.aicvgenerator.controller.dto.JobResponse;
import org.example.aicvgenerator.mapper.JobDtoMapper;
import org.example.aicvgenerator.service.JobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs")
public class JobController {

    private final JobService jobService;
    private final JobDtoMapper mapper;

    @GetMapping("/{id}")
    @Operation(summary = "Get the progress of a background job (for example a CV import)",
            description = "When COMPLETED, entityType and entityId point at the stored CV; when FAILED, errorCode and errorMessage say why.")
    @ApiResponse(responseCode = "400", description = "The id is not a valid job id")
    @ApiResponse(responseCode = "404", description = "Job not found (jobs are lost when the application restarts)")
    public JobResponse get(@PathVariable String id) {
        return mapper.toResponse(jobService.getById(mapper.toJobId(id)));
    }
}

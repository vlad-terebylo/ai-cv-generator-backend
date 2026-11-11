package org.example.aicvgenerator.controller;

import java.io.IOException;
import java.net.URI;

import org.example.aicvgenerator.controller.dto.CvResponse;
import org.example.aicvgenerator.controller.dto.JobResponse;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.mapper.CvDtoMapper;
import org.example.aicvgenerator.mapper.CvImportDtoMapper;
import org.example.aicvgenerator.service.CvImportService;
import org.example.aicvgenerator.service.CvService;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvImport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cvs")
@RequiredArgsConstructor
@Tag(name = "CVs")
public class CvController {

    private final CvService cvService;
    private final CvImportService cvImportService;
    private final CvDtoMapper mapper;
    private final CvImportDtoMapper importMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CV (.pdf or .docx) for asynchronous processing",
            description = "Returns immediately with an import job. Poll GET /api/v1/cv-imports/{id} until it is "
                    + "COMPLETED (then fetch the CV) or FAILED. An existing CV with the same email is replaced.")
    @ApiResponse(responseCode = "202", description = "Accepted; the Location header points to the import job")
    @ApiResponse(responseCode = "400", description = "Unsupported, empty or unreadable file")
    @ApiResponse(responseCode = "413", description = "File too large")
    @ApiResponse(responseCode = "503", description = "Too many imports queued, try again later")
    public ResponseEntity<JobResponse> upload(@RequestParam("file") MultipartFile file) {
        CvImport job = cvImportService.submit(toDocument(file));
        URI location = UriComponentsBuilder.fromPath("/api/v1/cv-imports/{id}").buildAndExpand(job.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(importMapper.toResponse(job));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a stored CV")
    @ApiResponse(responseCode = "404", description = "CV not found")
    public CvResponse get(@PathVariable long id) {
        return mapper.toResponse(cvService.getById(id));
    }

    private CvDocument toDocument(MultipartFile file) {
        try {
            return new CvDocument(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UnreadableDocumentException("The uploaded file could not be read", e);
        }
    }
}

package org.example.aicvgenerator.controller;

import java.io.IOException;

import org.example.aicvgenerator.controller.dto.CvResponse;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.mapper.CvDtoMapper;
import org.example.aicvgenerator.service.CvService;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final CvDtoMapper mapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CV (.pdf or .docx), parse it and store it",
            description = "Creates the CV, or replaces the stored one with the same email.")
    @ApiResponse(responseCode = "201", description = "CV created")
    @ApiResponse(responseCode = "200", description = "Existing CV with the same email updated")
    @ApiResponse(responseCode = "400", description = "Unsupported or unreadable file")
    @ApiResponse(responseCode = "422", description = "The document is not a CV")
    public ResponseEntity<CvResponse> upload(@RequestParam("file") MultipartFile file) {
        CvSaveResult result = cvService.process(toDocument(file));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(mapper.toResponse(result.cv()));
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

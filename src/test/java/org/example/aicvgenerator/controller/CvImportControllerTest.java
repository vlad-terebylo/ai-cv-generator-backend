package org.example.aicvgenerator.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.example.aicvgenerator.exception.CvImportNotFoundException;
import org.example.aicvgenerator.exception.GlobalExceptionHandler;
import org.example.aicvgenerator.mapper.CvImportDtoMapperImpl;
import org.example.aicvgenerator.service.CvImportService;
import org.example.aicvgenerator.service.model.CvImport;
import org.example.aicvgenerator.service.model.CvImportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CvImportController.class)
@Import({CvImportDtoMapperImpl.class, GlobalExceptionHandler.class})
class CvImportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CvImportService cvImportService;

    private final UUID id = UUID.randomUUID();

    private CvImport.CvImportBuilder job(CvImportStatus status) {
        return CvImport.builder().id(id).status(status).fileName("cv.pdf");
    }

    @Test
    void returnsPendingJob() throws Exception {
        when(cvImportService.getById(id)).thenReturn(job(CvImportStatus.PENDING).build());

        mockMvc.perform(get("/api/v1/cv-imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.name").value("cv.pdf"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returnsCompletedJobWithTheCvAsItsResult() throws Exception {
        when(cvImportService.getById(id)).thenReturn(job(CvImportStatus.COMPLETED).cvId(42L).build());

        mockMvc.perform(get("/api/v1/cv-imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.type").value("CV"))
                .andExpect(jsonPath("$.result.id").value(42))
                .andExpect(jsonPath("$.cvId").doesNotExist())
                .andExpect(jsonPath("$.fileName").doesNotExist());
    }

    @Test
    void returnsFailedJobWithErrorDetails() throws Exception {
        when(cvImportService.getById(id)).thenReturn(job(CvImportStatus.FAILED)
                .errorCode("NOT_A_CV").errorMessage("The uploaded document is not a CV").build());

        mockMvc.perform(get("/api/v1/cv-imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("NOT_A_CV"))
                .andExpect(jsonPath("$.errorMessage").value("The uploaded document is not a CV"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returns404ForUnknownJob() throws Exception {
        when(cvImportService.getById(id)).thenThrow(new CvImportNotFoundException(id));

        mockMvc.perform(get("/api/v1/cv-imports/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void returns400ForMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/cv-imports/not-a-uuid")).andExpect(status().isBadRequest());
    }
}

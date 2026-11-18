package org.example.aicvgenerator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.example.aicvgenerator.exception.CvNotFoundException;
import org.example.aicvgenerator.exception.GlobalExceptionHandler;
import org.example.aicvgenerator.exception.ServerBusyException;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.mapper.CvDtoMapperImpl;
import org.example.aicvgenerator.mapper.JobDtoMapperImpl;
import org.example.aicvgenerator.service.CvImportService;
import org.example.aicvgenerator.service.CvService;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.JobStatus;
import org.example.aicvgenerator.service.model.Experience;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CvController.class)
@Import({CvDtoMapperImpl.class, JobDtoMapperImpl.class, GlobalExceptionHandler.class})
class CvControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CvService cvService;
    @MockitoBean CvImportService cvImportService;

    private final MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[] {1});

    private Cv cv() {
        return Cv.builder().id(1L).firstName("Valieria").lastName("Karlova").email("v.karlova@gmail.com")
                .summary("proactive").experience(List.of(
                        Experience.builder().startYear(2025).position("Senior Java Developer").company("Absolute").build()))
                .build();
    }

    @Test
    void uploadReturns202WithLocationAndPendingJob() throws Exception {
        UUID id = UUID.randomUUID();
        when(cvImportService.submit(any())).thenReturn(Job.builder().id(id).status(JobStatus.PENDING)
                .label("cv.pdf").createdAt(Instant.parse("2026-11-05T10:00:00Z")).build());

        mockMvc.perform(multipart("/api/v1/cvs").file(file))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", String.format("/api/v1/jobs/%s", id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.entityType").doesNotExist())
                .andExpect(jsonPath("$.entityId").doesNotExist())
                .andExpect(jsonPath("$.fileName").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void uploadReturns400ForUnsupportedFile() throws Exception {
        when(cvImportService.submit(any())).thenThrow(new UnsupportedDocumentException("Only .pdf and .docx files are supported"));

        mockMvc.perform(multipart("/api/v1/cvs").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only .pdf and .docx files are supported"));
    }

    @Test
    void uploadReturns400ForEmptyFile() throws Exception {
        when(cvImportService.submit(any())).thenThrow(new UnreadableDocumentException("The uploaded file is empty"));

        mockMvc.perform(multipart("/api/v1/cvs").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void uploadReturns503WhenTheQueueIsFull() throws Exception {
        when(cvImportService.submit(any())).thenThrow(new ServerBusyException());

        mockMvc.perform(multipart("/api/v1/cvs").file(file)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void uploadReturns400WhenFilePartMissing() throws Exception {
        mockMvc.perform(multipart("/api/v1/cvs")).andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTheStoredCv() throws Exception {
        when(cvService.getById(1L)).thenReturn(cv());

        mockMvc.perform(get("/api/v1/cvs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.firstName").value("Valieria"))
                .andExpect(jsonPath("$.experience[0].company").value("Absolute"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(cvService.getById(5L)).thenThrow(new CvNotFoundException(5L));

        mockMvc.perform(get("/api/v1/cvs/5")).andExpect(status().isNotFound());
    }

    @Test
    void getReturns400WhenTheIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/v1/cvs/abc")).andExpect(status().isBadRequest());
    }
}
